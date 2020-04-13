package xyz.gianlu.librespot.dealer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.gianlu.librespot.BytesArrayList;
import xyz.gianlu.librespot.common.NameThreadFactory;
import xyz.gianlu.librespot.core.ApResolver;
import xyz.gianlu.librespot.core.Session;
import xyz.gianlu.librespot.mercury.MercuryClient;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

/**
 * @author Gianlu
 */
public class DealerClient implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(DealerClient.class);
    private final Looper looper = new Looper();
    private final Session session;
    private final Map<String, RequestListener> reqListeners = new HashMap<>();
    private final Map<MessageListener, List<String>> msgListeners = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NameThreadFactory((r) -> "dealer-scheduler-" + r.hashCode()));
    private final AtomicReference<ConnectionHolder> conn = new AtomicReference<>();
    private ScheduledFuture<?> lastScheduledReconnection;

    public DealerClient(@NotNull Session session) {
        this.session = session;
        new Thread(looper, "dealer-looper").start();
    }

    @NotNull
    private static Map<String, String> getHeaders(@NotNull JsonObject obj) {
        JsonObject headers = obj.getAsJsonObject("headers");
        if (headers == null) return Collections.emptyMap();

        Map<String, String> map = new HashMap<>();
        for (String key : headers.keySet()) map.put(key, headers.get(key).getAsString());
        return map;
    }

    /**
     * Creates a new WebSocket client. <b>Intended for internal use only!</b>
     */
    public void connect() throws IOException, MercuryClient.MercuryException {
        conn.set(new ConnectionHolder(session, new Request.Builder()
                .url(String.format("wss://%s/?access_token=%s", ApResolver.getRandomDealer(), session.tokens().get("playlist-read")))
                .build()));
    }

    private void waitForListeners() {
        synchronized (msgListeners) {
            if (!msgListeners.isEmpty()) return;

            try {
                msgListeners.wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void handleRequest(@NotNull JsonObject obj) {
        String mid = obj.get("message_ident").getAsString();
        String key = obj.get("key").getAsString();

        Map<String, String> headers = getHeaders(obj);
        JsonObject payload = obj.getAsJsonObject("payload");
        if ("gzip".equals(headers.get("Transfer-Encoding"))) {
            byte[] gzip = Base64.getDecoder().decode(payload.get("compressed").getAsString());
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
                payload = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
            } catch (IOException ex) {
                LOGGER.warn(String.format("Failed decompressing request! {mid: %s, key: %s}", mid, key), ex);
                return;
            }
        }

        int pid = payload.get("message_id").getAsInt();
        String sender = payload.get("sent_by_device_id").getAsString();

        JsonObject command = payload.getAsJsonObject("command");
        LOGGER.trace(String.format("Received request. {mid: %s, key: %s, pid: %d, sender: %s}", mid, key, pid, sender));

        boolean interesting = false;
        synchronized (reqListeners) {
            for (String midPrefix : reqListeners.keySet()) {
                if (mid.startsWith(midPrefix)) {
                    RequestListener listener = reqListeners.get(midPrefix);
                    interesting = true;
                    looper.submit(() -> {
                        RequestResult result = listener.onRequest(mid, pid, sender, command);
                        conn.get().sendReply(key, result);
                        LOGGER.debug(String.format("Handled request. {key: %s, result: %s}", key, result));
                    });
                }
            }
        }

        if (!interesting) LOGGER.debug("Couldn't dispatch request: " + mid);
    }

    private void handleMessage(@NotNull JsonObject obj) {
        String uri = obj.get("uri").getAsString();

        Map<String, String> headers = getHeaders(obj);
        JsonArray payloads = obj.getAsJsonArray("payloads");
        byte[] decodedPayload;
        if (payloads != null) {
            String[] payloadsStr = new String[payloads.size()];
            for (int i = 0; i < payloads.size(); i++) payloadsStr[i] = payloads.get(i).getAsString();

            InputStream in = BytesArrayList.streamBase64(payloadsStr);
            if ("gzip".equals(headers.get("Transfer-Encoding"))) {
                try {
                    in = new GZIPInputStream(in);
                } catch (IOException ex) {
                    LOGGER.warn(String.format("Failed decompressing message! {uri: %s}", uri), ex);
                    return;
                }
            }

            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(in.available());
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                decodedPayload = out.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        } else {
            decodedPayload = new byte[0];
        }

        boolean interesting = false;
        synchronized (msgListeners) {
            for (MessageListener listener : msgListeners.keySet()) {
                boolean dispatched = false;
                List<String> keys = msgListeners.get(listener);
                for (String key : keys) {
                    if (uri.startsWith(key) && !dispatched) {
                        interesting = true;
                        looper.submit(() -> {
                            try {
                                listener.onMessage(uri, headers, decodedPayload);
                            } catch (IOException ex) {
                                LOGGER.error("Failed dispatching message!", ex);
                            }
                        });
                        dispatched = true;
                    }
                }
            }
        }

        if (!interesting) LOGGER.debug("Couldn't dispatch message: " + uri);
    }

    public void addMessageListener(@NotNull MessageListener listener, @NotNull String... uris) {
        synchronized (msgListeners) {
            if (msgListeners.containsKey(listener))
                throw new IllegalArgumentException(String.format("A listener for %s has already been added.", Arrays.toString(uris)));

            msgListeners.put(listener, Arrays.asList(uris));
            msgListeners.notifyAll();
        }
    }

    public void removeMessageListener(@NotNull MessageListener listener) {
        synchronized (msgListeners) {
            msgListeners.remove(listener);
        }
    }

    public void addRequestListener(@NotNull RequestListener listener, @NotNull String uri) {
        synchronized (reqListeners) {
            if (reqListeners.containsKey(uri))
                throw new IllegalArgumentException(String.format("A listener for '%s' has already been added.", uri));

            reqListeners.put(uri, listener);
            reqListeners.notifyAll();
        }
    }

    public void removeRequestListener(@NotNull RequestListener listener) {
        synchronized (reqListeners) {
            reqListeners.values().remove(listener);
        }
    }

    @Override
    public void close() {
        if (conn.get() != null) {
            ConnectionHolder tmp = conn.get(); // Do not trigger connectionInvalided()
            conn.set(null);
            tmp.close();
        }

        if (lastScheduledReconnection != null) {
            lastScheduledReconnection.cancel(true);
            lastScheduledReconnection = null;
        }

        scheduler.shutdown();
        msgListeners.clear();
    }

    /**
     * Called when the {@link ConnectionHolder} has been closed and cannot be used no more for a connection.
     */
    private void connectionInvalided() {
        if (lastScheduledReconnection != null && !lastScheduledReconnection.isDone())
            throw new IllegalStateException();

        conn.set(null);

        LOGGER.trace("Scheduled reconnection attempt in 10 seconds...");
        lastScheduledReconnection = scheduler.schedule(() -> {
            lastScheduledReconnection = null;

            try {
                connect();
            } catch (IOException | MercuryClient.MercuryException ex) {
                LOGGER.error("Failed reconnecting, retrying...", ex);
                connectionInvalided();
            }
        }, 10, TimeUnit.SECONDS);
    }

    public enum RequestResult {
        UNKNOWN_SEND_COMMAND_RESULT, SUCCESS,
        DEVICE_NOT_FOUND, CONTEXT_PLAYER_ERROR,
        DEVICE_DISAPPEARED, UPSTREAM_ERROR,
        DEVICE_DOES_NOT_SUPPORT_COMMAND, RATE_LIMITED
    }

    public interface RequestListener {
        @NotNull
        RequestResult onRequest(@NotNull String mid, int pid, @NotNull String sender, @NotNull JsonObject command);
    }

    public interface MessageListener {
        void onMessage(@NotNull String uri, @NotNull Map<String, String> headers, @NotNull byte[] payload) throws IOException;
    }

    private static class Looper implements Runnable, Closeable {
        private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        private boolean shouldStop = false;

        void submit(@NotNull Runnable task) {
            tasks.add(task);
        }

        @Override
        public void run() {
            while (!shouldStop) {
                try {
                    tasks.take().run();
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }

        @Override
        public void close() {
            shouldStop = true;
        }
    }

    private class ConnectionHolder implements Closeable {
        private final WebSocket ws;
        private boolean closed = false;
        private boolean receivedPong = false;
        private ScheduledFuture<?> lastScheduledPing;

        ConnectionHolder(@NotNull Session session, @NotNull Request request) {
            ws = session.client().newWebSocket(request, new WebSocketListenerImpl());
        }

        private void sendPing() {
            ws.send("{\"type\":\"ping\"}");
        }

        void sendReply(@NotNull String key, @NotNull RequestResult result) {
            boolean success = result == RequestResult.SUCCESS;
            ws.send(String.format("{\"type\":\"reply\", \"key\": \"%s\", \"payload\": {\"success\": %b}}", key, success));
        }

        @Override
        public void close() {
            if (closed) {
                ws.cancel();
            } else {
                closed = true;
                ws.close(1000, null);
            }

            if (lastScheduledPing != null) {
                lastScheduledPing.cancel(false);
                lastScheduledPing = null;
            }

            ConnectionHolder holder = conn.get();
            if (holder == null) return;

            if (holder == ConnectionHolder.this)
                connectionInvalided();
            else
                LOGGER.debug(String.format("Did not dispatch connection invalidated: %s != %s", holder, ConnectionHolder.this));
        }

        private class WebSocketListenerImpl extends WebSocketListener {

            @Override
            public void onOpen(@NotNull WebSocket ws, @NotNull Response response) {
                if (closed || scheduler.isShutdown()) {
                    LOGGER.fatal(String.format("I wonder what happened here... Terminating. {closed: %b}", closed));
                    return;
                }

                LOGGER.debug(String.format("Dealer connected! {host: %s}", response.request().url().host()));
                lastScheduledPing = scheduler.scheduleAtFixedRate(() -> {
                    sendPing();
                    receivedPong = false;

                    scheduler.schedule(() -> {
                        if (lastScheduledPing == null || lastScheduledPing.isCancelled()) return;

                        if (!receivedPong) {
                            LOGGER.warn("Did not receive ping in 3 seconds. Reconnecting...");
                            ConnectionHolder.this.close();
                            return;
                        }

                        receivedPong = false;
                    }, 3, TimeUnit.SECONDS);
                }, 0, 30, TimeUnit.SECONDS);
            }

            @Override
            public void onMessage(@NotNull WebSocket ws, @NotNull String text) {
                JsonObject obj = JsonParser.parseString(text).getAsJsonObject();

                waitForListeners();

                MessageType type = MessageType.parse(obj.get("type").getAsString());
                switch (type) {
                    case MESSAGE:
                        handleMessage(obj);
                        break;
                    case REQUEST:
                        handleRequest(obj);
                        break;
                    case PONG:
                        receivedPong = true;
                        break;
                    case PING:
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown message type for " + type);
                }
            }

            @Override
            public void onFailure(@NotNull WebSocket ws, @NotNull Throwable t, @Nullable Response response) {
                LOGGER.warn("An exception occurred. Reconnecting...", t);
                ConnectionHolder.this.close();
            }
        }
    }
}
