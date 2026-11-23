package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The sender. While «Activar emisor automático» is on, it listens for paired receivers
 * ({@link CodeExchange} and {@link HttpsOrigin}), wakes the phone when it has no secure lock,
 * and runs each request as an {@link AppTask} in the official app.
 *
 * <p>The class name is kept although it now does more than read codes: Android stores enabled
 * accessibility services by class name, and renaming it would switch the sender off on every phone.
 */
public final class OfficialCodeService extends AccessibilityService {
    private static final long WAKE_TIMEOUT_MS = 5000;
    /** Pause after the keyguard goes away before opening the official app. */
    private static final long SETTLE_AFTER_WAKE_MS = 500;
    private static final long SLEEP_DELAY_MS = 1500;
    /** Opened Club·by links need time to load before the screen goes off. */
    private static final long SLEEP_DELAY_AFTER_LINK_MS = 10000;
    private static OfficialCodeService connected;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TaskRunner runner;
    private SharedPreferences settings;
    private CodeExchange exchange;
    private HttpsOrigin httpsOrigin;
    /** The request whose task is running, to tell whether a new one may replace it. */
    private PendingRequest running;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged = (prefs, key) -> {
        if (CodeExchange.SENDER_ENABLED.equals(key) || CodeExchange.SENDER_KEY.equals(key)) restartSender();
    };

    static boolean isConnected() { return connected != null; }

    static boolean isListening() { return connected != null && connected.exchange != null; }

    static boolean isHttpsOriginReady() {
        OfficialCodeService service = connected;
        return service != null && service.httpsOrigin != null && service.httpsOrigin.isReady();
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = this;
        runner = new TaskRunner(this, main);
        settings = SenderClient.settings(this);
        settings.registerOnSharedPreferenceChangeListener(settingsChanged);
        restartSender();
    }

    // ---- Network listeners

    private void restartSender() {
        stopSender();
        if (!settings.getBoolean(CodeExchange.SENDER_ENABLED, false)) return;
        String key = settings.getString(CodeExchange.SENDER_KEY, "");
        if (key.isEmpty()) return;
        try {
            exchange = new CodeExchange(key, this::handleRequest);
        } catch (IOException | IllegalArgumentException failure) {
            android.util.Log.w("FoodCourtSender", "No se pudo abrir el puerto del emisor", failure);
            return;
        }
        SenderKeepAliveService.start(this);
        try {
            httpsOrigin = new HttpsOrigin();
            httpsOrigin.start();
        } catch (RuntimeException failure) {
            httpsOrigin = null;
            android.util.Log.w("FoodCourtSender", "No se pudo abrir el origen HTTPS", failure);
        }
    }

    private void stopSender() {
        if (runner != null) runner.cancel(null);
        if (httpsOrigin != null) {
            httpsOrigin.shutdown();
            httpsOrigin = null;
        }
        if (exchange == null) return;
        SenderKeepAliveService.stop(this);
        CodeExchange.closeQuietly(exchange);
        exchange = null;
    }

    // ---- Requests (called on a connection thread, answered from the main thread)

    private String handleRequest(CodeExchange.Operation operation, String input, boolean waitForQr)
            throws CodeExchange.Unavailable {
        String invalid = validate(operation, input);
        if (invalid != null) return invalid;
        PendingRequest request = new PendingRequest(operation, input, waitForQr);
        main.post(() -> whenAwake(request, () -> run(request)));
        long wait = (waitForQr ? CodeExchange.QR_WAIT_MS : CodeExchange.budgetMs(operation)) + 8000;
        try {
            if (!request.done.await(wait, TimeUnit.MILLISECONDS)) {
                main.post(() -> cancel(request, null));
                throw new CodeExchange.Unavailable(waitForQr
                    ? "El emisor no mostró el QR a tiempo. Comprueba la sesión de la app oficial."
                    : "El emisor tardó demasiado en responder.");
            }
        } catch (InterruptedException receiverLeft) {
            // The receiver disconnected while waiting for a QR.
            main.post(() -> cancel(request, null));
            Thread.currentThread().interrupt();
            return "";
        }
        if (request.wokeScreen) {
            main.postDelayed(this::sleepAgain, operation == CodeExchange.Operation.CLUB_LINK
                ? SLEEP_DELAY_AFTER_LINK_MS : SLEEP_DELAY_MS);
        }
        if (request.unavailable != null) throw new CodeExchange.Unavailable(request.unavailable);
        return request.answer;
    }

    /** Rejects malformed input before touching the phone; returns the message, or null when valid. */
    private static String validate(CodeExchange.Operation operation, String input) {
        if (operation == CodeExchange.Operation.TACO_TICKET && !TacoBellTask.isValidTicket(input)) {
            return "El código del ticket no tiene un formato válido.";
        }
        if (operation == CodeExchange.Operation.AMICI_TICKET && AmiciTicket.parse(input) == null) {
            return "Los datos del ticket de Amici no son válidos.";
        }
        return null;
    }

    private static AppTask taskFor(PendingRequest request) {
        switch (request.operation) {
            case MCD_POINTS: return new McDonaldsQrTask();
            case TACO_POINTS: return new TacoBellTask(null);
            case TACO_TICKET: return new TacoBellTask(request.input);
            case CLUB_POINTS: return new ClubCardTask(ClubCardTask.Goal.MEMBER_QR);
            case CLUB_SCANNER: return new ClubCardTask(ClubCardTask.Goal.READER);
            case CLUB_LEISURE: return new ClubCardTask(ClubCardTask.Goal.LEISURE);
            case AMICI_SCANNER: return new AmiciScannerTask();
            case AMICI_TICKET: return new AmiciTicketTask(AmiciTicket.parse(request.input));
            case AMICI_SYNC: return new AmiciBenefitsTask(null);
            case AMICI_COUPON: return new AmiciBenefitsTask(request.input);
            case CLUB_PROMOS: return new ClubPromotionsTask(ClubPromotionsTask.Mode.LIST, "");
            case CLUB_PROMO_QR: return new ClubPromotionsTask(ClubPromotionsTask.Mode.QR, request.input);
            case CLUB_PROMO_ADD: return new ClubPromotionsTask(ClubPromotionsTask.Mode.ADD, request.input);
            default: return null;
        }
    }

    /** Starts the request's task. Only one runs at a time; a new member QR request replaces an older one. */
    private void run(PendingRequest request) {
        if (request.cancelled || exchange == null || SystemClock.elapsedRealtime() >= request.deadline) {
            request.finish("", null);
            return;
        }
        if (runner.isBusy()) {
            if (running == null || !CodeExchange.isMemberQr(running.operation)) {
                request.finish("", "El emisor está atendiendo otra operación. Vuelve a intentarlo.");
                return;
            }
            runner.cancel("Una nueva solicitud sustituyó esta consulta de QR.");
        }
        if (request.operation == CodeExchange.Operation.CLUB_LINK) {
            request.finish(ClubBy.openScannedLink(this, request.input), null);
            return;
        }
        AppTask task = taskFor(request);
        if (task == null) {
            request.finish("", null);
            return;
        }
        running = request;
        runner.start(task, request.deadline, (answer, unavailable) -> {
            if (running == request) running = null;
            request.finish(answer, unavailable);
        });
    }

    private void cancel(PendingRequest request, String reason) {
        request.cancelled = true;
        if (running == request) runner.cancel(reason);
        request.finish("", reason);
    }

    // ---- Screen

    /**
     * Runs {@code then} once the screen is on and unlocked. Without PIN, pattern or password the
     * phone is woken through {@link WakeActivity}; a secure lock is never bypassed.
     */
    private void whenAwake(PendingRequest request, Runnable then) {
        if (request.cancelled) return;
        PowerManager power = getSystemService(PowerManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (power.isInteractive() && !keyguard.isKeyguardLocked()) {
            if (request.wokeScreen && !request.settled) {
                request.settled = true;
                main.postDelayed(then, SETTLE_AFTER_WAKE_MS);
            } else {
                then.run();
            }
            return;
        }
        if (keyguard.isKeyguardSecure()) {
            request.finish("", "Desbloquea el emisor y deja su pantalla encendida. "
                + "Sin PIN, patrón ni contraseña, Food Court lo enciende solo.");
            return;
        }
        if (!request.wokeScreen) {
            request.wokeScreen = true;
            request.wakeStarted = SystemClock.elapsedRealtime();
            try {
                startActivity(new Intent(this, WakeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
            } catch (RuntimeException refused) {
                android.util.Log.w("FoodCourtSender", "No se pudo encender el emisor", refused);
            }
        }
        if (SystemClock.elapsedRealtime() - request.wakeStarted < WAKE_TIMEOUT_MS) {
            main.postDelayed(() -> whenAwake(request, then), 250);
        } else {
            request.finish("", "No se pudo encender el emisor. Enciende su pantalla y vuelve a intentarlo.");
        }
    }

    /** Goes home and turns the screen off again after a request that woke the phone. */
    private void sleepAgain() {
        if (runner.isBusy() || Build.VERSION.SDK_INT < 28) return;
        if (!getSystemService(PowerManager.class).isInteractive()) return;
        performGlobalAction(GLOBAL_ACTION_HOME);
        main.postDelayed(() -> {
            if (!runner.isBusy()) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }, 400);
    }

    // ---- Service lifecycle

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() {
        if (runner != null) runner.cancel(null);
    }

    @Override public void onDestroy() {
        stopSender();
        if (settings != null) settings.unregisterOnSharedPreferenceChangeListener(settingsChanged);
        if (connected == this) connected = null;
        super.onDestroy();
    }

    /** A receiver's request, waited for on the connection thread and completed on the main thread. */
    private static final class PendingRequest {
        final CountDownLatch done = new CountDownLatch(1);
        final CodeExchange.Operation operation;
        final String input;
        final long deadline;
        volatile boolean cancelled, wokeScreen;
        boolean settled;
        long wakeStarted;
        volatile String answer = "";
        volatile String unavailable;

        PendingRequest(CodeExchange.Operation operation, String input, boolean waitForQr) {
            this.operation = operation;
            this.input = input;
            deadline = SystemClock.elapsedRealtime()
                + (waitForQr ? CodeExchange.QR_WAIT_MS : CodeExchange.budgetMs(operation));
        }

        /** Completes the request once; later calls are ignored. */
        synchronized void finish(String value, String reason) {
            if (done.getCount() == 0) return;
            answer = value == null ? "" : value;
            unavailable = reason;
            done.countDown();
        }
    }
}
