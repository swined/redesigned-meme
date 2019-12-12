package net.swined.revolut;

public class ClientError extends RuntimeException {

    private final int code;

    public ClientError(int code, String message) {
        super(message);
        this.code = code;
    }

    public ClientError(int code, Throwable e) {
        super(e.getMessage(), e);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
