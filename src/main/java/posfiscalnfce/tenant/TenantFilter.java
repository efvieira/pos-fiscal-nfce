package posfiscalnfce.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Lê X-Store-Id / X-User e abre o escopo do tenant para as rotas de negócio (/v1, exceto /v1/admin). */
@Component
public class TenantFilter extends OncePerRequestFilter {

    private final StoreRepository stores;

    public TenantFilter(StoreRepository stores) {
        this.stores = stores;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return !path.startsWith("/v1/") || path.startsWith("/v1/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID storeId;
        try {
            storeId = UUID.fromString(request.getHeader("X-Store-Id"));
        } catch (IllegalArgumentException | NullPointerException e) {
            reject(response, HttpStatus.BAD_REQUEST, "Header X-Store-Id ausente ou inválido");
            return;
        }
        if (!stores.existsById(storeId)) {
            reject(response, HttpStatus.FORBIDDEN, "Loja não encontrada");
            return;
        }
        var user = request.getHeader("X-User") == null ? "operador" : request.getHeader("X-User");
        try {
            ScopedValue.where(TenantContext.CURRENT, new TenantContext.Current(storeId, user)).call(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"status\":%d,\"detail\":\"%s\"}".formatted(status.value(), detail));
    }
}
