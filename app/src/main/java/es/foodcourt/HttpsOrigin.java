package es.foodcourt;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * Plain WebSocket origin for HTTPS links. A tunnel or reverse proxy (cloudflared, Caddy, nginx…)
 * terminates TLS and forwards to this port; every connection is relayed byte for byte to the
 * authenticated {@link CodeExchange} listener, so nothing is readable without the pairing key.
 */
final class HttpsOrigin extends WebSocketServer {
    static final int PORT = 38148;
    static final String PATH = "/foodcourt/v1";
    static final int MAX_MESSAGE = 8192;
    private final ThreadPoolExecutor bridges = new ThreadPoolExecutor(0, 6, 20, TimeUnit.SECONDS,
        new SynchronousQueue<>(), work -> new Thread(work, "food-court-https-origin"));
    private volatile boolean ready;

    HttpsOrigin() {
        super(new InetSocketAddress(PORT), 2, Collections.singletonList(
            new Draft_6455(Collections.emptyList(),
                Collections.singletonList(new org.java_websocket.protocols.Protocol("")), MAX_MESSAGE)));
        setReuseAddr(true);
        setConnectionLostTimeout(60);
    }

    boolean isReady() {
        return ready;
    }

    @Override public void onStart() {
        ready = true;
    }

    @Override public void onOpen(WebSocket connection, ClientHandshake handshake) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", CodeExchange.PORT), 1500);
            socket.setTcpNoDelay(true);
        } catch (IOException unavailable) {
            CodeExchange.closeQuietly(socket);
            connection.close(1011, "Emisor no disponible");
            return;
        }
        connection.setAttachment(socket);
        try {
            bridges.execute(() -> relay(connection, socket));
        } catch (RejectedExecutionException busy) {
            CodeExchange.closeQuietly(socket);
            connection.close(1013, "Emisor ocupado");
        }
    }

    /** Copies everything the local listener sends back to the WebSocket. */
    private void relay(WebSocket connection, Socket socket) {
        byte[] buffer = new byte[MAX_MESSAGE];
        try {
            InputStream input = socket.getInputStream();
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (!connection.isOpen()) break;
                connection.send(ByteBuffer.wrap(buffer, 0, count).slice());
            }
            connection.close(1000, "");
        } catch (IOException | RuntimeException unavailable) {
            if (connection.isOpen()) connection.close(1011, "Conexión interrumpida");
        } finally {
            CodeExchange.closeQuietly(socket);
        }
    }

    @Override public void onMessage(WebSocket connection, ByteBuffer message) {
        Socket socket = connection.getAttachment();
        if (socket == null || message.remaining() > MAX_MESSAGE) {
            connection.close(1008, "Solicitud no válida");
            return;
        }
        try {
            byte[] data = new byte[message.remaining()];
            message.get(data);
            socket.getOutputStream().write(data);
            socket.getOutputStream().flush();
        } catch (IOException unavailable) {
            connection.close(1011, "No se completó la consulta");
        }
    }

    @Override public void onMessage(WebSocket connection, String message) {
        connection.close(1003, "Se esperaba un mensaje binario");
    }

    @Override public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        Socket socket = connection.getAttachment();
        if (socket != null) CodeExchange.closeQuietly(socket);
    }

    @Override public void onError(WebSocket connection, Exception error) {
        if (connection != null) {
            onClose(connection, 1011, "", false);
        } else {
            ready = false;
            android.util.Log.w("FoodCourtHttps", "No se pudo abrir el origen HTTPS", error);
        }
    }

    void shutdown() {
        ready = false;
        for (WebSocket connection : getConnections()) onClose(connection, 1001, "", false);
        bridges.shutdownNow();
        try {
            stop(200);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
