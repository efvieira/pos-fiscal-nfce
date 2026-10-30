package posfiscalnfce.tenant;

import posfiscalnfce.shared.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Loja e operador da requisição atual. Usa ScopedValue (Java 25) em vez de ThreadLocal:
 * o valor é imutável, some sozinho ao fim do escopo e funciona bem com virtual threads.
 */
public final class TenantContext {

    public record Current(UUID storeId, String user) {
    }

    static final ScopedValue<Current> CURRENT = ScopedValue.newInstance();

    private TenantContext() {
    }

    public static UUID storeId() {
        return current().storeId();
    }

    public static String user() {
        return CURRENT.isBound() ? CURRENT.get().user() : "system";
    }

    private static Current current() {
        if (!CURRENT.isBound()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Header X-Store-Id obrigatório");
        }
        return CURRENT.get();
    }

    /** Executa um trecho como se fosse uma requisição da loja informada (jobs e testes). */
    public static <T> T callAs(UUID storeId, String user, ScopedValue.CallableOp<T, RuntimeException> op) {
        return ScopedValue.where(CURRENT, new Current(storeId, user)).call(op);
    }
}
