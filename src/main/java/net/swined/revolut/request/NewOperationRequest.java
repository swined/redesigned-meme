package net.swined.revolut.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class NewOperationRequest {

    public final Map<String, String> diff = new HashMap<>();

    @JsonAnySetter
    public void addDiff(String account, String value) {
        diff.put(account, value);
    }
}
