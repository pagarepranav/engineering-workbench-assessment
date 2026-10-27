package dev.pranav.workbench.common;

public class ApiException extends RuntimeException {
    private final int status;
    public ApiException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
    public static void require(boolean condition, int status, String message) {
        if (!condition) throw new ApiException(status, message);
    }
}
