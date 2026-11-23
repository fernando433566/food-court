package es.foodcourt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Receiver side of the exchange: the saved pairing and background requests to the sender.
 * Answers and errors are delivered on the UI thread, and dropped if the activity is gone.
 */
final class SenderClient {
    static final String CONTACT_FAILED = "No se pudo contactar con el emisor. Comprueba la red y el emparejamiento.";

    interface Answer { void accept(String answer); }
    interface JsonAnswer { void accept(JSONObject answer); }
    interface Failure { void accept(String message); }

    private final Activity activity;
    private final SharedPreferences settings;

    SenderClient(Activity activity) {
        this.activity = activity;
        settings = settings(activity);
    }

    static SharedPreferences settings(Context context) {
        return context.getSharedPreferences(CodeExchange.SETTINGS, Context.MODE_PRIVATE);
    }

    /** Whether this phone already acts as sender or has a paired sender. */
    static boolean isConfigured(Context context) {
        SharedPreferences settings = settings(context);
        boolean sender = settings.getBoolean(CodeExchange.SENDER_ENABLED, false)
            && !settings.getString(CodeExchange.SENDER_KEY, "").isEmpty();
        boolean receiver = !settings.getString(CodeExchange.RECEIVER_HOST, "").isEmpty()
            && !settings.getString(CodeExchange.RECEIVER_KEY, "").isEmpty();
        return sender || receiver;
    }

    String host() { return settings.getString(CodeExchange.RECEIVER_HOST, ""); }
    String key() { return settings.getString(CodeExchange.RECEIVER_KEY, ""); }
    boolean isPaired() { return !host().isEmpty() && !key().isEmpty(); }

    void save(String host, String key) {
        settings.edit().putString(CodeExchange.RECEIVER_HOST, host).putString(CodeExchange.RECEIVER_KEY, key).apply();
    }

    void forget() {
        settings.edit().remove(CodeExchange.RECEIVER_KEY).remove(CodeExchange.RECEIVER_HOST).apply();
    }

    /** Sends a request to the paired sender. */
    void send(CodeExchange.Operation operation, String input, Answer onAnswer, Failure onFailure) {
        send(host(), key(), operation, input, null, onAnswer, onFailure);
    }

    /** Sends a request whose answer is a JSON object; an «error» field is reported as a failure. */
    void sendJson(CodeExchange.Operation operation, String input, JsonAnswer onAnswer, Failure onFailure) {
        send(operation, input, answer -> {
            JSONObject json;
            try {
                json = new JSONObject(answer);
            } catch (JSONException unexpected) {
                onFailure.accept("El emisor respondió algo inesperado. Actualiza Food Court en los dos móviles.");
                return;
            }
            String error = json.optString("error");
            if (error.isEmpty()) onAnswer.accept(json);
            else onFailure.accept(error);
        }, onFailure);
    }

    /** Asks for a member QR; the receiver can cancel the wait through {@code request}. */
    void sendQr(CodeExchange.Operation operation, CodeExchange.QrRequest request, Answer onAnswer, Failure onFailure) {
        send(host(), key(), operation, "", request, onAnswer, onFailure);
    }

    /** Sends to an explicit address and key, for pairing before they are saved. */
    void send(String host, String key, CodeExchange.Operation operation, String input,
              CodeExchange.QrRequest qrRequest, Answer onAnswer, Failure onFailure) {
        new Thread(() -> {
            String answer = null;
            String failure = null;
            try {
                answer = qrRequest == null ? CodeExchange.request(host, key, operation, input)
                    : qrRequest.request(host, key, operation);
            } catch (java.io.IOException | java.security.GeneralSecurityException | IllegalArgumentException error) {
                failure = CodeExchange.receiverError(error, CONTACT_FAILED);
            }
            String result = answer;
            String message = failure;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (message != null) onFailure.accept(message);
                else onAnswer.accept(result);
            });
        }, "food-court-receiver").start();
    }
}
