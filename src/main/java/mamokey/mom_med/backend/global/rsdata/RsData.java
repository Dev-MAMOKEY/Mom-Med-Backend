package mamokey.mom_med.backend.global.rsdata;

public record RsData<T>(
        boolean success,
        int code,
        String message,
        T data
) {
    public static <T> RsData<T> ok(T data) {
        return new RsData<>(true, 200, "OK", data);
    }

    public static <T> RsData<T> created(T data) {
        return new RsData<>(true, 201, "Created", data);
    }

    public static <T> RsData<T> ok(int code, String message, T data) {
        return new RsData<>(true, code, message, data);
    }

    public static <T> RsData<T> fail(int code, String message) {
        return new RsData<>(false, code, message, null);
    }
}
