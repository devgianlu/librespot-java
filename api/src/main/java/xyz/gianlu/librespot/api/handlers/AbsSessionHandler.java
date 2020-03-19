package xyz.gianlu.librespot.api.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.jetbrains.annotations.NotNull;
import xyz.gianlu.librespot.api.SessionWrapper;
import xyz.gianlu.librespot.core.Session;

/**
 * @author Gianlu
 */
public abstract class AbsSessionHandler implements HttpHandler {
    private final SessionWrapper wrapper;

    public AbsSessionHandler(@NotNull SessionWrapper wrapper) {
        this.wrapper = wrapper;
    }

    @Override
    public final void handleRequest(HttpServerExchange exchange) throws Exception {
        Session s = wrapper.get();
        if (s == null) {
            exchange.setStatusCode(StatusCodes.NO_CONTENT);
            return;
        }

        if (s.reconnecting()) {
            exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
            exchange.getResponseHeaders().add(Headers.RETRY_AFTER, 10);
            return;
        }

        if (!s.valid()) {
            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            return;
        }

        exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
        exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE");
        exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Headers"), "*");

        handleRequest(exchange, s);
    }

    protected abstract void handleRequest(@NotNull HttpServerExchange exchange, @NotNull Session session) throws Exception;
}
