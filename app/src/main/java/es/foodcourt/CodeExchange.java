package es.foodcourt;

import android.util.Base64;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Paired, encrypted request/answer protocol between a receiver and the sender phone.
 *
 * <p>The sender listens on TCP {@link #PORT} (reachable over the LAN or Netbird) and, for HTTPS
 * links, behind {@link HttpsOrigin}. Each connection is one exchange:
 * sender challenge → receiver nonce + HMAC proof → encrypted request «OPERATION\ninput» →
 * encrypted answer. Both sides derive AES-GCM keys from the shared pairing key.
 */
final class CodeExchange implements Closeable {
    static final int PORT = 38147;
    /** Time the sender waits for the receiver's proof and request. */
    static final int HANDSHAKE_TIMEOUT_MS = 2500;
    static final int CONNECT_TIMEOUT_MS = 6000;
    static final int HTTPS_CONNECT_TIMEOUT_MS = 20000;
    /** How long the sender keeps trying to show a member QR before giving up. */
    static final int QR_WAIT_MS = 60000;
    static final int CHALLENGE_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int FRAME_LIMIT = 4096;

    /** Shared preferences file and keys for the pairing on both phones (names kept for existing installs). */
    static final String SETTINGS = "local_exchange";
    static final String SENDER_ENABLED = "senderEnabled", SENDER_KEY = "senderKey";
    static final String RECEIVER_HOST = "receiverHost", RECEIVER_KEY = "receiverKey";

    /** What a receiver can ask the sender to do in an official app. */
    enum Operation {
        /** Checks the pairing; the sender answers with an empty frame. */
        PAIR,
        // Member QR codes. Receivers may wait for them and cancel (see QrRequest).
        MCD_POINTS, TACO_POINTS, CLUB_POINTS,
        /** Types a Taco Bell ticket code into «Introducir manualmente». */
        TACO_TICKET,
        /** Opens the official camera reader on the sender. */
        AMICI_SCANNER, CLUB_SCANNER,
        /** Opens «Mis descuentos de ocio» in Club·by. */
        CLUB_LEISURE,
        /** Fills «Añadir datos manualmente» in Amici; input from {@link AmiciTicket#encode}. */
        AMICI_TICKET,
        /** Opens a clubby:// or club-by.onelink.me link in Club·by. */
        CLUB_LINK,
        // JSON answers (see AppTask): Amici level and coupons, one coupon code, Club·by promotions.
        AMICI_SYNC, AMICI_COUPON, CLUB_PROMOS, CLUB_PROMO_QR, CLUB_PROMO_ADD
    }

    /** Time the sender may spend driving the official app for one request. */
    static int budgetMs(Operation operation) {
        switch (operation) {
            case MCD_POINTS: return 10500;
            case AMICI_TICKET: return 45000;
            case AMICI_SYNC: case AMICI_COUPON: case CLUB_PROMO_ADD: return 30000;
            case CLUB_PROMO_QR: return 35000;
            case CLUB_PROMOS: return 45000;
            default: return 16500;
        }
    }
    private static final String STATUS_REQUEST = "\u0000status";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;
    private final ServerSocket server;
    private final RequestHandler handler;
    private final Set<Socket> clients = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ThreadPoolExecutor connections = new ThreadPoolExecutor(0, 6, 20, TimeUnit.SECONDS,
        new SynchronousQueue<>(), task -> new Thread(task, "food-court-local-connection"));

    static final class Unavailable extends IOException {
        Unavailable(String message) { super(message); }
    }

    static String receiverError(Exception error, String fallback) {
        if (error instanceof Unavailable || error instanceof IllegalArgumentException) {
            String message = error.getMessage();
            if (message != null && !message.isEmpty()) return message;
        }
        if (error instanceof java.net.ConnectException || error instanceof java.net.SocketTimeoutException
                || error instanceof java.net.NoRouteToHostException) {
            return "No se pudo conectar con el emisor. Comprueba que esté activo, en la misma red o Netbird, "
                + "y que Food Court no tenga restricciones de batería.";
        }
        return fallback;
    }

    /** Carries out one authenticated request on the sender and returns the answer. */
    interface RequestHandler {
        String handle(Operation operation, String input, boolean waitForQr) throws Unavailable;
    }

    CodeExchange(String pairedKey, RequestHandler handler) throws IOException {
        this.handler = handler;
        key = parseKey(pairedKey);
        server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
        } catch (IOException failure) {
            server.close();
            throw failure;
        }
        Thread worker = new Thread(this::listen, "food-court-local-sender");
        worker.start();
    }

    static String newKey() {
        return Base64.encodeToString(randomBytes(32), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] parseKey(String value) {
        byte[] key;
        try {
            key = Base64.decode(value.trim(), Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Clave de emparejamiento no válida.");
        }
        if (key.length != 32) throw new IllegalArgumentException("Clave de emparejamiento no válida.");
        return key;
    }

    private void listen() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                clients.add(client);
                try {
                    connections.execute(() -> serve(client));
                } catch (RejectedExecutionException unavailable) {
                    clients.remove(client);
                    closeQuietly(client);
                }
            } catch (IOException ignored) {
                // Closing the listener interrupts accept().
            }
        }
    }

    private void serve(Socket client) {
        try (Socket connection = client) {
            connection.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            reply(connection);
        } catch (IOException | GeneralSecurityException ignored) {
            // A failed connection does not interrupt other requests.
        } catch (RuntimeException error) {
            android.util.Log.w("FoodCourtExchange", "Error al atender una solicitud", error);
        } finally {
            clients.remove(client);
        }
    }

    private void reply(Socket client) throws IOException, GeneralSecurityException {
        DataInputStream in = new DataInputStream(client.getInputStream());
        DataOutputStream out = new DataOutputStream(client.getOutputStream());
        byte[] challenge = randomBytes(CHALLENGE_LENGTH);
        out.write(challenge);
        out.flush();
        byte[] receiverNonce = new byte[CHALLENGE_LENGTH];
        in.readFully(receiverNonce);
        byte[] transcript = transcript(challenge, receiverNonce);
        byte[] proof = new byte[32];
        in.readFully(proof);
        if (!MessageDigest.isEqual(proof, sign(key, transcript))) return;

        String request = readFrame(in, key, transcript, (byte) 1);
        int delimiter = request.indexOf('\n');
        if (delimiter < 0) return;
        Operation operation;
        try {
            operation = Operation.valueOf(request.substring(0, delimiter));
        } catch (IllegalArgumentException unknownOperation) {
            return;
        }
        String input = request.substring(delimiter + 1);
        boolean wantsStatus = isMemberQr(operation) && STATUS_REQUEST.equals(input);
        String response = "";
        if (operation != Operation.PAIR) {
            try {
                response = handle(client, in, operation, input, wantsStatus);
            } catch (Unavailable error) {
                if (wantsStatus) response = "\u0000" + error.getMessage();
            }
        }
        sendFrame(out, key, transcript, (byte) 2, response);
    }

    /** Runs the request; while waiting for a QR, a receiver disconnect cancels it. */
    private String handle(Socket client, DataInputStream in, Operation operation,
                          String input, boolean waitForQr) throws IOException {
        if (!waitForQr) return handler.handle(operation, input, false);
        client.setSoTimeout(0);
        Thread worker = Thread.currentThread();
        AtomicBoolean finished = new AtomicBoolean();
        Thread disconnect = new Thread(() -> {
            try {
                in.read();
            } catch (IOException ignored) {
                // A disconnect cancels the read of the official app.
            }
            synchronized (finished) {
                if (!finished.getAndSet(true)) worker.interrupt();
            }
        }, "food-court-qr-disconnect");
        disconnect.start();
        try {
            return handler.handle(operation, "", true);
        } finally {
            synchronized (finished) {
                if (finished.getAndSet(true)) Thread.interrupted();
            }
        }
    }

    /** A byte stream to the sender: plain TCP (LAN or Netbird) or a WebSocket through HTTPS. */
    interface Channel extends Closeable {
        InputStream input() throws IOException;
        OutputStream output() throws IOException;
        /** Read timeout in milliseconds; 0 waits without limit. */
        void setReadTimeout(int milliseconds) throws IOException;
    }

    static final class QrRequest {
        private Channel channel;
        private boolean cancelled;

        synchronized void cancel() {
            cancelled = true;
            if (channel != null) closeQuietly(channel);
        }

        synchronized void attach(Channel client) throws IOException {
            if (cancelled) throw new IOException("Consulta cancelada.");
            channel = client;
        }

        String request(String host, String key, Operation operation)
                throws IOException, GeneralSecurityException {
            if (!isMemberQr(operation)) throw new IllegalArgumentException("Se esperaba una consulta de QR.");
            return CodeExchange.request(host, key, operation, "", this);
        }
    }

    /** Member QR operations, the only ones a receiver can wait for and cancel. */
    static boolean isMemberQr(Operation operation) {
        return operation == Operation.MCD_POINTS || operation == Operation.TACO_POINTS
            || operation == Operation.CLUB_POINTS;
    }

    static String request(String host, String pairedKey, Operation operation, String input)
            throws IOException, GeneralSecurityException {
        return request(host, pairedKey, operation, input, null);
    }

    static boolean isHttpsAddress(String address) {
        return address.regionMatches(true, 0, "https://", 0, 8);
    }

    /** Private LAN ranges, link-local and the 100.64.0.0/10 range that Netbird and Tailscale use. */
    static boolean isReachablePrivate(InetAddress address) {
        if (!(address instanceof Inet4Address)) return false;
        return address.isSiteLocalAddress() || address.isLinkLocalAddress() || isOverlay(address);
    }

    /** Netbird/Tailscale addresses (100.64.0.0/10). */
    static boolean isOverlay(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 64;
    }

    private static Channel open(String address) throws IOException {
        if (isHttpsAddress(address)) return new HttpsChannel(address);
        String host = address;
        if (host.endsWith(":" + PORT)) host = host.substring(0, host.length() - (":" + PORT).length());
        if (host.isEmpty() || !host.matches("[A-Za-z0-9.-]+")) {
            throw new IllegalArgumentException(
                "Introduce la IP local o de Netbird del emisor, su nombre de Netbird o una URL https.");
        }
        InetAddress target;
        try {
            target = InetAddress.getByName(host);
        } catch (java.net.UnknownHostException unknown) {
            throw new Unavailable("No se encontró «" + host + "». Comprueba el nombre o que Netbird esté conectado.");
        }
        if (!isReachablePrivate(target)) {
            throw new IllegalArgumentException(
                "Usa una IP de la red local o de Netbird. Para Internet, usa una URL https del emisor.");
        }
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(target, PORT), CONNECT_TIMEOUT_MS);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
        } catch (IOException unavailable) {
            closeQuietly(socket);
            throw unavailable;
        }
        return new SocketChannel(socket);
    }

    private static String request(String host, String pairedKey, Operation operation, String input,
                                  QrRequest qrRequest) throws IOException, GeneralSecurityException {
        byte[] key = parseKey(pairedKey);
        String address = host.trim();
        boolean https = isHttpsAddress(address);
        int answerTimeout = qrRequest == null ? budgetMs(operation) + 15000 : QR_WAIT_MS + 15000;
        for (int attempt = 0; ; attempt++) {
            Channel client = null;
            try {
                DataInputStream in;
                byte[] challenge = new byte[CHALLENGE_LENGTH];
                try {
                    client = open(address);
                    if (qrRequest != null) qrRequest.attach(client);
                    client.setReadTimeout(https ? HTTPS_CONNECT_TIMEOUT_MS : CONNECT_TIMEOUT_MS);
                    in = new DataInputStream(client.input());
                    in.readFully(challenge);
                } catch (IOException unavailable) {
                    if (attempt == 0 && !(unavailable instanceof Unavailable)
                            && !"Consulta cancelada.".equals(unavailable.getMessage())) {
                        closeQuietly(client);
                        continue;
                    }
                    throw unavailable;
                }

                DataOutputStream out = new DataOutputStream(client.output());
                byte[] receiverNonce = randomBytes(CHALLENGE_LENGTH);
                byte[] transcript = transcript(challenge, receiverNonce);
                out.write(receiverNonce);
                out.write(sign(key, transcript));
                boolean wantsStatus = qrRequest != null;
                sendFrame(out, key, transcript, (byte) 1,
                    operation.name() + "\n" + (wantsStatus ? STATUS_REQUEST : input));

                client.setReadTimeout(answerTimeout);
                String response;
                try {
                    response = readFrame(in, key, transcript, (byte) 2);
                } catch (java.io.EOFException rejected) {
                    throw new Unavailable("El emisor cerró la conexión. Comprueba la clave de emparejamiento.");
                } catch (java.net.SocketTimeoutException slow) {
                    throw new Unavailable("El emisor no respondió a tiempo. Comprueba que esté desbloqueado.");
                }
                if (wantsStatus && response.startsWith("\u0000")) {
                    throw new Unavailable(response.substring(1));
                }
                return response;
            } finally {
                closeQuietly(client);
            }
        }
    }

    static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // The connection is being discarded.
        }
    }

    private static final class SocketChannel implements Channel {
        private final Socket socket;

        SocketChannel(Socket socket) { this.socket = socket; }

        @Override public InputStream input() throws IOException { return socket.getInputStream(); }

        @Override public OutputStream output() throws IOException { return socket.getOutputStream(); }

        @Override public void setReadTimeout(int milliseconds) throws IOException {
            socket.setSoTimeout(milliseconds);
        }

        @Override public void close() throws IOException { socket.close(); }
    }

    private static void sendFrame(DataOutputStream out, byte[] key, byte[] transcript, byte direction,
                                  String content) throws IOException, GeneralSecurityException {
        byte[] plaintext = content.getBytes(StandardCharsets.UTF_8);
        if (plaintext.length > FRAME_LIMIT) throw new IOException("Mensaje demasiado grande.");
        byte[] nonce = randomBytes(NONCE_LENGTH);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, key, transcript, direction, nonce, plaintext);
        out.write(nonce);
        out.writeInt(encrypted.length);
        out.write(encrypted);
        out.flush();
    }

    private static String readFrame(DataInputStream in, byte[] key, byte[] transcript, byte direction)
            throws IOException, GeneralSecurityException {
        byte[] nonce = new byte[NONCE_LENGTH];
        in.readFully(nonce);
        int length = in.readInt();
        if (length < 16 || length > FRAME_LIMIT + 16) throw new IOException("Respuesta no válida.");
        byte[] encrypted = new byte[length];
        in.readFully(encrypted);
        return new String(crypt(Cipher.DECRYPT_MODE, key, transcript, direction, nonce, encrypted),
            StandardCharsets.UTF_8);
    }

    private static byte[] transcript(byte[] challenge, byte[] receiverNonce) {
        byte[] transcript = new byte[CHALLENGE_LENGTH * 2];
        System.arraycopy(challenge, 0, transcript, 0, CHALLENGE_LENGTH);
        System.arraycopy(receiverNonce, 0, transcript, CHALLENGE_LENGTH, CHALLENGE_LENGTH);
        return transcript;
    }

    private static byte[] randomBytes(int size) {
        byte[] value = new byte[size];
        RANDOM.nextBytes(value);
        return value;
    }

    private static byte[] sign(byte[] key, byte[] challenge) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(challenge);
    }

    private static byte[] crypt(int mode, byte[] key, byte[] challenge, byte direction, byte[] nonce, byte[] value)
        throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] encryptionKey = mac.doFinal(new byte[] { 1, direction });
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(challenge);
        return cipher.doFinal(value);
    }

    @Override public void close() throws IOException {
        try {
            server.close();
        } finally {
            connections.shutdownNow();
            for (Socket client : clients) closeQuietly(client);
        }
    }
}
