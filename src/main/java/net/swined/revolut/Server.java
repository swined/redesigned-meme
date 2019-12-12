package net.swined.revolut;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.swined.revolut.request.NewAccountRequest;
import net.swined.revolut.request.NewOperationRequest;
import net.swined.revolut.storage.Storage;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {

    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    private final Storage storage;

    public Server(Storage storage) {
        this.storage = storage;
    }

    public HttpServer run(int port, int backlog, Executor executor) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), backlog);
        server.createContext("/account/", byMethod(Map.of(
                "GET", restHandler(null, this::getAccount),
                "PUT", restHandler(NewAccountRequest.class, this::putAccount)
        )));
        server.createContext("/operation/", byMethod(Map.of(
                "PUT", restHandler(NewOperationRequest.class, this::putOperation)
        )));
        server.setExecutor(executor);
        server.start();
        logger.info(String.format("listening on port %s", port));
        return server;
    }

    private static void unsupportedMethodHandler(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, -1);
    }

    private static HttpHandler byMethod(Map<String, HttpHandler> map) {
        return exchange -> map.getOrDefault(exchange.getRequestMethod(), Server::unsupportedMethodHandler).handle(exchange);
    }

    private static String getIdFromPath(HttpExchange exchange) {
        var path = exchange.getRequestURI().getRawPath();
        var context = exchange.getHttpContext().getPath();
        return Optional
                .of(path)
                .filter(p -> p.startsWith(context))
                .map(p -> p.substring(context.length()))
                .orElseThrow();
    }

    private void reply(HttpExchange exchange, int code, Object data) throws IOException {
        logger.info(String.format(
                "replying %s to %s %s : %s",
                code,
                exchange.getRequestMethod(),
                exchange.getRequestURI(),
                data
        ));
        byte[] bytes = mapper.writeValueAsBytes(data);
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private <T> HttpHandler restHandler(Class<T> bodyClass, BiFunction<String, T, Map<String, String>> handler) {
        return exchange -> {
            logger.info(String.format("processing %s %s", exchange.getRequestMethod(), exchange.getRequestURI()));
            try {
                var id = getIdFromPath(exchange);
                var body = bodyClass == null ? null : mapper.readValue(exchange.getRequestBody(), bodyClass);
                var result = handler.apply(id, body);
                reply(exchange, HttpURLConnection.HTTP_OK, result);
            } catch (JsonMappingException e) {
                if (e.getCause() instanceof ClientError) {
                    logger.warning(e.getCause().getMessage());
                    reply(exchange, HttpURLConnection.HTTP_BAD_REQUEST, Map.of("error", e.getCause().getMessage()));
                } else {
                    logger.warning(e.getMessage());
                    reply(exchange, HttpURLConnection.HTTP_BAD_REQUEST, Map.of("error", "invalid json"));
                }
            } catch (Exception e) {
                if (!(e instanceof ClientError)) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
                reply(
                        exchange,
                        Optional
                                .of(e)
                                .filter(ClientError.class::isInstance)
                                .map(ClientError.class::cast)
                                .map(ClientError::getCode)
                                .orElse(HttpURLConnection.HTTP_INTERNAL_ERROR),
                        Map.of("error", e.getMessage())
                );
            }
        };
    }

    private Map<String, String> getAccount(String id, Void body) {
        return Map.of("balance", storage.get(id).getBalance().toString());
    }

    private Map<String, String> putAccount(String id, NewAccountRequest body) {
        storage.create(id, body.currency);
        return Map.of();
    }

    private Map<String, String> putOperation(String id, NewOperationRequest body) {
        storage.update(id, body.diff);
        return Map.of();
    }

}
