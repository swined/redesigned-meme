package net.swined.revolut.storage;

import net.swined.revolut.ClientError;
import org.joda.money.CurrencyUnit;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Storage {

    private static final Logger logger = Logger.getLogger(Storage.class.getName());
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, Operation> operations = new ConcurrentHashMap<>();

    public void create(String id, CurrencyUnit currency) {
        if (id == null || id.isEmpty()) {
            throw new ClientError(HttpURLConnection.HTTP_BAD_REQUEST, "account id is missing");
        }
        var account = new Account(id, currency);
        accounts.putIfAbsent(id, account);
        if (!account.equals(accounts.get(id))) {
            throw new ClientError(HttpURLConnection.HTTP_CONFLICT, "account already exists with different currency");
        }
    }

    public Account get(String id) {
        var account = accounts.get(id);
        if (account == null) {
            throw new ClientError(HttpURLConnection.HTTP_NOT_FOUND, "account not found: " + id);
        } else {
            return account;
        }
    }

    public void update(String id, Map<String, String> diff) {
        logger.info(String.format("about to execute id=%s diff=%s", id, diff));
        if (id == null || id.isEmpty()) {
            throw new ClientError(HttpURLConnection.HTTP_BAD_REQUEST, "operation id is missing");
        }
        var operation = operations.compute(id, (k, v) -> {
            var op = new Operation(id, diff);
            if (v == null) {
                return op;
            } else {
                if (op.equals(v)) {
                    return v;
                } else {
                    throw new ClientError(HttpURLConnection.HTTP_CONFLICT, "operation mismatch");
                }
            }
        });
        logger.info("executing " + operation);
        operation.apply(this::get);
    }

}
