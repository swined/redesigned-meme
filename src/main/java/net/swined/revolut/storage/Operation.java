package net.swined.revolut.storage;

import org.joda.money.Money;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Operation {

    private final String id;
    private final Map<String, String> diff;
    private boolean done;
    private RuntimeException error;

    public Operation(String id, Map<String, String> diff) {
        this.id = Objects.requireNonNull(id);
        this.diff = Objects.requireNonNull(diff);
    }

    private static <K, V> boolean equals(Map<K, V> dst, Map<K, V> src) {
        return src.entrySet().stream().allMatch(e -> e.getValue().equals(dst.get(e.getKey()))) &&
                dst.entrySet().stream().allMatch(e -> e.getValue().equals(src.get(e.getKey())));
    }

    public synchronized void apply(Function<String, Account> accountMapper) {
        if (!done) {
            try {
                Account.update(diff.entrySet().stream().collect(Collectors.toMap(
                        e -> accountMapper.apply(e.getKey()),
                        e -> Money.parse(e.getValue())
                )));
            } catch (RuntimeException e) {
                error = e;
            } finally {
                done = true;
            }
        }
        if (error != null) {
            throw error;
        }
    }

    @Override
    public boolean equals(Object o) {
        return Optional
                .ofNullable(o)
                .filter(Operation.class::isInstance)
                .map(Operation.class::cast)
                .map(that -> this.id.equals(that.id) && equals(this.diff, that.diff))
                .orElse(false);
    }

    @Override
    public synchronized String toString() {
        return String.format(
                "operation(id=%s, status=%s, diff=%s)",
                id,
                done ? error == null ? "done" : error.getMessage() : "not started",
                diff
        );
    }

}
