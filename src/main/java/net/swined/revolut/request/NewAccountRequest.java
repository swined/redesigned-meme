package net.swined.revolut.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.swined.revolut.ClientError;
import org.joda.money.CurrencyUnit;
import org.joda.money.IllegalCurrencyException;

import java.net.HttpURLConnection;

public class NewAccountRequest {

    public final CurrencyUnit currency;

    public NewAccountRequest(@JsonProperty("currency") String currency) {
        try {
            this.currency = CurrencyUnit.of(currency);
        } catch (IllegalCurrencyException e) {
            throw new ClientError(HttpURLConnection.HTTP_BAD_REQUEST, e);
        }
    }
}
