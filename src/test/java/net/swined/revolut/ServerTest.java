package net.swined.revolut;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import net.swined.revolut.storage.Storage;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class ServerTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    public void assertGet(String path, int code, String body) {
        assertResponse(GET(path), code, body);
    }

    public void assertPut(String path, Map<String, String> data, int code, String body) {
        assertResponse(PUT(path, data), code, body);
    }

    public void assertResponse(HttpRequest request, int code, String body) {
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var expected = List.of(code, body);
            var actual = List.of(response.statusCode(), response.body());
            Assertions.assertEquals(expected, actual);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public HttpRequest GET(String url) {
        try {
            return HttpRequest
                    .newBuilder(new URI("http://localhost:" + server.getAddress().getPort() + url))
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public HttpRequest PUT(String url, Map<String, String> data) {
        try {
            return PUT(url, mapper.writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpRequest PUT(String url, String data) {
        try {
            return HttpRequest
                    .newBuilder(new URI("http://localhost:" + server.getAddress().getPort() + url))
                    .PUT(HttpRequest.BodyPublishers.ofString(data))
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void getUnknownPath() {
        assertGet("/", 404, "<h1>404 Not Found</h1>No context found for request");
    }

    @Test
    public void getUnknownMethod() {
        assertGet("/operation/qwe", 405, "");
    }

    @Test
    public void putUnknownPath() {
        assertPut("/", Map.of(), 404, "<h1>404 Not Found</h1>No context found for request");
    }

    @Test
    public void getUnknownAccount() {
        assertGet("/account/qwe", 404, "{\"error\":\"account not found: qwe\"}");
    }

    @Test
    public void invalidJson() {
        assertPut("/account/qwe", Map.of(), 400, "{\"error\":\"invalid json\"}");
        assertPut("/account/qwe", Map.of("foo", "bar"), 400, "{\"error\":\"invalid json\"}");
    }

    @Test
    public void badCurrency() {
        assertPut("/account/qwe", Map.of("currency", "usd"), 400, "{\"error\":\"Unknown currency 'usd'\"}");
    }

    @Test
    public void missingAccountId() {
        assertPut("/account/", Map.of("currency", "USD"), 400, "{\"error\":\"account id is missing\"}");
    }

    @Test
    public void missingOperationId() {
        assertPut("/operation/", Map.of(), 400, "{\"error\":\"operation id is missing\"}");
    }


    @Test
    public void createAccount() {
        assertGet("/account/qwe", 404, "{\"error\":\"account not found: qwe\"}");
        assertPut("/account/qwe", Map.of("currency", "USD"), 200, "{}");
        assertGet("/account/qwe", 200, "{\"balance\":\"USD 0.00\"}");
        assertPut("/account/qwe", Map.of("currency", "USD"), 200, "{}");
        assertGet("/account/qwe", 200, "{\"balance\":\"USD 0.00\"}");
        assertPut("/account/qwe", Map.of("currency", "GBP"), 409, "{\"error\":\"account already exists with different currency\"}");
    }

    @Test
    public void overwriteAccount() {
        assertGet("/account/1", 404, "{\"error\":\"account not found: 1\"}");
        assertPut("/account/1", Map.of("currency", "USD"), 200, "{}");
        assertGet("/account/1", 200, "{\"balance\":\"USD 0.00\"}");
        assertPut("/operation/1", Map.of("1", "USD1"), 200, "{}");
        assertGet("/account/1", 200, "{\"balance\":\"USD 1.00\"}");
        assertPut("/account/1", Map.of("currency", "USD"), 200, "{}");
        assertGet("/account/1", 200, "{\"balance\":\"USD 1.00\"}");
    }

    @Test
    public void fundAccount() {
        assertGet("/account/1", 404, "{\"error\":\"account not found: 1\"}");
        assertPut("/account/1", Map.of("currency", "USD"), 200, "{}");
        assertPut("/operation/1", Map.of("1", "USD-1"), 412, "{\"error\":\"insufficient balance\"}");
        assertPut("/operation/1", Map.of("1", "GBP-1"), 409, "{\"error\":\"operation mismatch\"}");
        assertPut("/operation/2", Map.of("1", "GBP1"), 412, "{\"error\":\"Currencies differ: USD/GBP\"}");
        assertPut("/operation/3", Map.of("1", "USD1"), 200, "{}");
        assertPut("/operation/3", Map.of("1", "USD1"), 200, "{}");
        assertGet("/account/1", 200, "{\"balance\":\"USD 1.00\"}");
        assertPut("/operation/1", Map.of("1", "USD-1"), 412, "{\"error\":\"insufficient balance\"}");
        assertPut("/operation/4", Map.of("1", "USD-1"), 200, "{}");
        assertGet("/account/1", 200, "{\"balance\":\"USD 0.00\"}");
    }

    @Test
    public void transferFunds() {
        assertGet("/account/1", 404, "{\"error\":\"account not found: 1\"}");
        assertGet("/account/2", 404, "{\"error\":\"account not found: 2\"}");
        assertGet("/account/3", 404, "{\"error\":\"account not found: 3\"}");
        assertGet("/account/4", 404, "{\"error\":\"account not found: 4\"}");
        assertPut("/account/1", Map.of("currency", "USD"), 200, "{}");
        assertPut("/account/2", Map.of("currency", "USD"), 200, "{}");
        assertPut("/account/3", Map.of("currency", "GBP"), 200, "{}");
        assertGet("/account/1", 200, "{\"balance\":\"USD 0.00\"}");
        assertGet("/account/2", 200, "{\"balance\":\"USD 0.00\"}");
        assertGet("/account/3", 200, "{\"balance\":\"GBP 0.00\"}");
        assertPut("/operation/1", Map.of("1", "USD1"), 200, "{}");
        assertGet("/account/1", 200, "{\"balance\":\"USD 1.00\"}");
        assertGet("/account/2", 200, "{\"balance\":\"USD 0.00\"}");
        assertPut("/operation/2", Map.of("1", "USD-1", "2", "USD 1.00"), 200, "{}");
        assertGet("/account/1", 200, "{\"balance\":\"USD 0.00\"}");
        assertGet("/account/2", 200, "{\"balance\":\"USD 1.00\"}");
        assertPut("/operation/3", Map.of("2", "USD-1", "3", "USD 1.00"), 412, "{\"error\":\"Currencies differ: GBP/USD\"}");
        assertGet("/account/1", 200, "{\"balance\":\"USD 0.00\"}");
        assertGet("/account/2", 200, "{\"balance\":\"USD 1.00\"}");
        assertGet("/account/3", 200, "{\"balance\":\"GBP 0.00\"}");
        assertPut("/operation/4", Map.of("2", "USD-1", "4", "USD 1.00"), 404, "{\"error\":\"account not found: 4\"}");
        assertPut("/account/4", Map.of("currency", "USD"), 200, "{}");
        assertPut("/operation/4", Map.of("2", "USD-1", "4", "USD 1.00"), 404, "{\"error\":\"account not found: 4\"}");
        assertPut("/operation/5", Map.of("2", "USD-1", "4", "USD 1.00"), 200, "{}");
        assertGet("/account/2", 200, "{\"balance\":\"USD 0.00\"}");
        assertGet("/account/4", 200, "{\"balance\":\"USD 1.00\"}");
    }

    @Test
    public void concurrentTest() {
        assertGet("/account/1", 404, "{\"error\":\"account not found: 1\"}");
        assertGet("/account/2", 404, "{\"error\":\"account not found: 2\"}");
        assertGet("/account/3", 404, "{\"error\":\"account not found: 3\"}");
        assertGet("/account/4", 404, "{\"error\":\"account not found: 4\"}");
        assertPut("/account/1", Map.of("currency", "USD"), 200, "{}");
        assertPut("/account/2", Map.of("currency", "USD"), 200, "{}");
        assertPut("/account/3", Map.of("currency", "USD"), 200, "{}");
        var balance = new HashMap<String, Money>();
        var initial = new HashMap<String, Money>();
        var ids = new HashSet<String>();
        var ops = IntStream.range(0, 1000).mapToObj(i -> {
            var src = Integer.toString(1 + (int)(4 * Math.random()));
            var dst = Integer.toString(1 + (int)(4 * Math.random()));
            var amount = Money.of(CurrencyUnit.USD, Math.round(1000. * Math.random()) / 100.);
            if (!src.equals("4") && !dst.equals("4")) {
                balance.merge(src, amount.multipliedBy(-1), Money::plus);
                balance.merge(dst, amount, Money::plus);
                initial.merge(src, amount, Money::plus);
            }
            return (Runnable)(() -> {
                var op = String.format("/operation/%s", UUID.randomUUID().toString());
                synchronized (ids) {
                    if (ids.contains(op)) {
                        throw new IllegalStateException("operation id collision");
                    } else {
                        ids.add(op);
                    }
                }
                var data = src.equals(dst) ? Map.of(src, "USD0") : Map.of(src, amount.multipliedBy(-1).toString(), dst, amount.toString());
                IntStream.rangeClosed(0, 1 + (int)(10 * Math.random())).forEach(j -> {
                    if (src.equals("4") || dst.equals("4")) {
                        assertPut(op, data, 404, "{\"error\":\"account not found: 4\"}");
                    } else {
                        assertPut(op, data, 200, "{}");
                    }
                });
            });
        }).collect(Collectors.toList());
        initial.forEach((k, v) -> {
            balance.merge(k, v, Money::plus);
            assertPut("/operation/initial_" + k, Map.of(k, v.toString()), 200, "{}");
        });
        CompletableFuture.allOf(ops.stream().map(CompletableFuture::runAsync).toArray(CompletableFuture[]::new)).join();
        balance.forEach((k, v) -> assertGet("/account/" + k, 200, "{\"balance\":\"" + v + "\"}"));
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new Server(new Storage()).run(8080, 100, ForkJoinPool.commonPool());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        server = null;
    }
}