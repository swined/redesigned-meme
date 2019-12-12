package net.swined.revolut.storage;

import net.swined.revolut.ClientError;
import org.joda.money.CurrencyMismatchException;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.net.HttpURLConnection;
import java.util.*;

public class Account {

    private final String id;
    private final CurrencyUnit currency;
    private Money balance;

    public Account(String id, CurrencyUnit currency) {
        this.id = Objects.requireNonNull(id);
        this.currency = Objects.requireNonNull(currency);
        this.balance = Money.zero(currency);
    }

    public String getId() {
        return id;
    }

    public synchronized Money getBalance() {
        return balance;
    }

    private void verify(Money diff) {
        try {
            if (balance.plus(diff).isNegative()) {
                throw new ClientError(HttpURLConnection.HTTP_PRECON_FAILED, "insufficient balance");
            }
        } catch (CurrencyMismatchException e) {
            throw new ClientError(HttpURLConnection.HTTP_PRECON_FAILED, e);
        }
    }

    private void execute(Money diff) {
        balance = balance.plus(diff);
    }

    public static void update(Map<Account, Money> diff) {
        update(diff, diff.keySet().stream().sorted(Comparator.comparing(Account::getId, String::compareTo)).iterator());
    }

    private static void update(Map<Account, Money> diff, Iterator<Account> locks) {
        if (locks.hasNext()) {
            synchronized (locks.next()) {
                update(diff, locks);
            }
        } else {
            diff.forEach(Account::verify);
            diff.forEach(Account::execute);
        }
    }

    @Override
    public boolean equals(Object o) {
        return Optional
                .ofNullable(o)
                .filter(Account.class::isInstance)
                .map(Account.class::cast)
                .map(that -> this.id.equals(that.id) && this.currency.equals(that.currency))
                .orElse(false);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, currency);
    }
}
