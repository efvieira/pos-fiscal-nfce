package posfiscalnfce.cash;

import posfiscalnfce.audit.AuditService;
import posfiscalnfce.shared.BusinessException;
import posfiscalnfce.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/cash-sessions")
@Tag(name = "Caixa")
public class CashController {

    public record OpenRequest(@NotNull @DecimalMin("0") BigDecimal openingFloat) {
    }

    public record MovementRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, String note) {
    }

    public record CloseRequest(@NotNull Map<PaymentMethod, BigDecimal> counted) {
    }

    public record LineView(PaymentMethod method, BigDecimal expected, BigDecimal counted, BigDecimal difference) {
    }

    public record SessionView(UUID id, String operator, CashSession.Status status, BigDecimal openingFloat,
                              Instant openedAt, Instant closedAt, Map<PaymentMethod, BigDecimal> expected,
                              List<LineView> closing) {
        static SessionView from(CashSession s) {
            return new SessionView(s.getId(), s.getOperator(), s.getStatus(), s.getOpeningFloat(), s.getOpenedAt(),
                    s.getClosedAt(), s.expectedBalance(), s.getClosing().stream()
                    .map(l -> new LineView(l.getMethod(), l.getExpected(), l.getCounted(), l.getDifference())).toList());
        }
    }

    private final CashSessionRepository sessions;
    private final AuditService audit;
    private final Clock clock;

    public CashController(CashSessionRepository sessions, AuditService audit, Clock clock) {
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Abre o caixa do operador com o fundo de troco")
    public SessionView open(@Valid @RequestBody OpenRequest request) {
        var storeId = TenantContext.storeId();
        var operator = TenantContext.user();
        sessions.findByStoreIdAndOperatorAndStatus(storeId, operator, CashSession.Status.OPEN).ifPresent(s -> {
            throw BusinessException.rule("Operador já tem um caixa aberto: " + s.getId());
        });
        var session = sessions.save(new CashSession(storeId, operator, request.openingFloat(), clock.instant()));
        audit.record(storeId, "CASH_OPENED", session.getId(), "fundo " + request.openingFloat());
        return SessionView.from(session);
    }

    @GetMapping("/current")
    public SessionView current() {
        return SessionView.from(currentSession());
    }

    @GetMapping("/{id}")
    public SessionView get(@PathVariable UUID id) {
        return SessionView.from(sessions.findByIdAndStoreId(id, TenantContext.storeId())
                .orElseThrow(() -> BusinessException.notFound("Caixa")));
    }

    @PostMapping("/current/supplies")
    @Transactional
    @Operation(summary = "Suprimento (entrada de dinheiro)")
    public SessionView supply(@Valid @RequestBody MovementRequest request) {
        var session = currentSession();
        session.supply(request.amount(), request.note(), clock.instant());
        audit.record(session.getStoreId(), "CASH_SUPPLY", session.getId(), request.amount() + " " + request.note());
        return SessionView.from(session);
    }

    @PostMapping("/current/withdrawals")
    @Transactional
    @Operation(summary = "Sangria (retirada de dinheiro)")
    public SessionView withdraw(@Valid @RequestBody MovementRequest request) {
        var session = currentSession();
        session.withdraw(request.amount(), request.note(), clock.instant());
        audit.record(session.getStoreId(), "CASH_WITHDRAWAL", session.getId(), request.amount() + " " + request.note());
        return SessionView.from(session);
    }

    @PostMapping("/current/close")
    @Transactional
    @Operation(summary = "Fecha o caixa informando o valor contado de cada meio de pagamento")
    public SessionView close(@Valid @RequestBody CloseRequest request) {
        var session = currentSession();
        var lines = session.close(request.counted(), clock.instant());
        var difference = lines.stream().map(ClosingLine::getDifference).reduce(BigDecimal.ZERO, BigDecimal::add);
        audit.record(session.getStoreId(), "CASH_CLOSED", session.getId(), "diferença total " + difference);
        return SessionView.from(session);
    }

    private CashSession currentSession() {
        return sessions.findByStoreIdAndOperatorAndStatus(TenantContext.storeId(), TenantContext.user(), CashSession.Status.OPEN)
                .orElseThrow(() -> BusinessException.rule("Nenhum caixa aberto para o operador " + TenantContext.user()));
    }
}
