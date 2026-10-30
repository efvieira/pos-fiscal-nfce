package posfiscalnfce.shared;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, what + " não encontrado(a)");
    }

    public static BusinessException rule(String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
