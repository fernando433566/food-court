package es.foodcourt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Carries the paired exchange protocol through a WebSocket behind an HTTPS link
 * (for example a Cloudflare Tunnel or a reverse proxy pointing at {@link HttpsOrigin}).
 * The protocol keeps its own authentication and encryption; TLS only protects the route.
 */
final class HttpsChannel extends WebSocketListener implements CodeExchange.Channel {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build();
    private static final byte[] CLOSED = new byte[0];
    private final LinkedBlockingQueue<byte[]> messages = new LinkedBlockingQueue<>();
    private final WebSocket socket;
    private final InputStream input = new Input();
    private final OutputStream output = new Output();
    private volatile String failure = "El enlace HTTPS cerró la conexión antes de responder.";
    private volatile int readTimeout;

    HttpsChannel(String address) {
        HttpUrl url = HttpUrl.parse(address.trim());
        if (url == null || !url.isHttps() || !url.username().isEmpty() || !url.password().isEmpty()
                || url.fragment() != null) {
            throw new IllegalArgumentException("Introduce una URL https del emisor sin credenciales.");
        }
        HttpUrl endpoint = "/".equals(url.encodedPath())
            ? url.newBuilder().encodedPath(HttpsOrigin.PATH).build() : url;
        socket = CLIENT.newWebSocket(new Request.Builder().url(endpoint).build(), this);
    }

    @Override public InputStream input() { return input; }

    @Override public OutputStream output() { return output; }

    @Override public void setReadTimeout(int milliseconds) { readTimeout = milliseconds; }

    @Override public void onMessage(WebSocket connection, ByteString bytes) {
        if (bytes.size() > HttpsOrigin.MAX_MESSAGE) {
            failure = "El enlace HTTPS envió un mensaje no válido.";
            close();
            return;
        }
        if (bytes.size() > 0) messages.offer(bytes.toByteArray());
    }

    @Override public void onMessage(WebSocket connection, String message) {
        failure = "El enlace HTTPS no apunta al origen de Food Court.";
        close();
    }

    @Override public void onClosing(WebSocket connection, int code, String reason) {
        if (code == 1013) failure = "El emisor está ocupado. Vuelve a intentarlo.";
        else if (code == 1011) failure = "El emisor no pudo atender la consulta. Comprueba que esté activo.";
        connection.close(1000, null);
        messages.offer(CLOSED);
    }

    @Override public void onClosed(WebSocket connection, int code, String reason) {
        messages.offer(CLOSED);
    }

    @Override public void onFailure(WebSocket connection, Throwable error, Response response) {
        if (response != null) {
            failure = "El enlace HTTPS respondió con HTTP " + response.code()
                + ". Comprueba la URL y que el túnel apunte al puerto " + HttpsOrigin.PORT + " del emisor.";
        } else if (error instanceof java.net.UnknownHostException) {
            failure = "No se encontró el servidor del enlace HTTPS. Comprueba la URL.";
        } else if (error instanceof javax.net.ssl.SSLException) {
            failure = "El certificado HTTPS del enlace no es válido.";
        } else {
            failure = "No se pudo conectar al enlace HTTPS. Comprueba la URL y la conexión a Internet.";
        }
        messages.offer(CLOSED);
    }

    @Override public void close() {
        socket.cancel();
        messages.clear();
        messages.offer(CLOSED);
    }

    private final class Input extends InputStream {
        private byte[] current = new byte[0];
        private int position;

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override public int read(byte[] target, int offset, int length) throws IOException {
            if (length == 0) return 0;
            while (position >= current.length) {
                if (current == CLOSED) return -1;
                byte[] next;
                try {
                    int timeout = readTimeout;
                    next = timeout == 0 ? messages.take() : messages.poll(timeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Consulta cancelada.", interrupted);
                }
                if (next == null) throw new SocketTimeoutException("El enlace HTTPS no respondió.");
                if (next == CLOSED) {
                    messages.offer(CLOSED);
                    throw new CodeExchange.Unavailable(failure);
                }
                current = next;
                position = 0;
            }
            int count = Math.min(length, current.length - position);
            System.arraycopy(current, position, target, offset, count);
            position += count;
            return count;
        }
    }

    private final class Output extends OutputStream {
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        @Override public void write(int value) { pending.write(value); }

        @Override public void write(byte[] source, int offset, int length) {
            pending.write(source, offset, length);
        }

        @Override public void flush() throws IOException {
            if (pending.size() == 0) return;
            byte[] data = pending.toByteArray();
            pending.reset();
            if (data.length > HttpsOrigin.MAX_MESSAGE || !socket.send(ByteString.of(data))) {
                throw new CodeExchange.Unavailable(failure);
            }
        }
    }
}
