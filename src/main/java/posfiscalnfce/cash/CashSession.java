package posfiscalnfce.cash;

import posfiscalnfce.shared.BusinessException;
import posfiscalnfce.shared.Money;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Turno de caixa de um operador, da abertura ao fechamento. */
@Entity
@Table(name = "cash_sessions")
public class CashSession {

    public enum Status { OPEN, CLOSED }

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false, length = 60)
    private String operator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "opening_float", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingFloat;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cash_movements", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "position")
    private List<CashMovement> movements = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cash_closing_lines", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "position")
    private List<ClosingLine> closing = new ArrayList<>();

    @Version
    private long version;

    protected CashSession() {
    }

    CashSession(UUID storeId, String operator, BigDecimal openingFloat, Instant now) {
        if (openingFloat.signum() < 0) {
            throw BusinessException.rule("Fundo de troco não pode ser negativo");
        }
        this.id = UUID.randomUUID();
        this.storeId = storeId;
        this.operator = operator;
        this.openingFloat = Money.of(openingFloat);
        this.openedAt = now;
        this.status = Status.OPEN;
    }

    /** Suprimento: dinheiro que entra no caixa (ex.: reforço de troco). */
    public void supply(BigDecimal amount, String note, Instant now) {
        requireOpen();
        requirePositive(amount);
        movements.add(new CashMovement(CashMovement.Type.SUPPLY, PaymentMethod.CASH, Money.of(amount), note, now));
    }

    /** Sangria: retirada de dinheiro. Não pode deixar o caixa negativo. */
    public void withdraw(BigDecimal amount, String note, Instant now) {
        requireOpen();
        requirePositive(amount);
        if (expectedBalance().get(PaymentMethod.CASH).compareTo(amount) < 0) {
            throw BusinessException.rule("Sangria maior que o dinheiro em caixa");
        }
        movements.add(new CashMovement(CashMovement.Type.WITHDRAWAL, PaymentMethod.CASH, Money.of(amount), note, now));
    }

    public void registerSale(PaymentMethod method, BigDecimal amount, String saleId, Instant now) {
        requireOpen();
        movements.add(new CashMovement(CashMovement.Type.SALE, method, Money.of(amount), "venda " + saleId, now));
    }

    public void registerSaleCancellation(PaymentMethod method, BigDecimal amount, String saleId, Instant now) {
        requireOpen();
        movements.add(new CashMovement(CashMovement.Type.SALE_CANCELLATION, method, Money.of(amount), "cancelamento " + saleId, now));
    }

    /** Saldo esperado por meio de pagamento. Dinheiro começa com o fundo de troco. */
    public Map<PaymentMethod, BigDecimal> expectedBalance() {
        var balance = new EnumMap<PaymentMethod, BigDecimal>(PaymentMethod.class);
        for (var method : PaymentMethod.values()) {
            balance.put(method, BigDecimal.ZERO);
        }
        balance.put(PaymentMethod.CASH, openingFloat);
        for (var movement : movements) {
            balance.merge(movement.getMethod(), movement.signedAmount(), BigDecimal::add);
        }
        balance.replaceAll((m, v) -> Money.of(v));
        return balance;
    }

    /** Fecha o caixa conferindo o valor contado de cada meio com o esperado. */
    public List<ClosingLine> close(Map<PaymentMethod, BigDecimal> counted, Instant now) {
        requireOpen();
        var expected = expectedBalance();
        for (var method : PaymentMethod.values()) {
            closing.add(new ClosingLine(method, expected.get(method), Money.of(counted.getOrDefault(method, BigDecimal.ZERO))));
        }
        status = Status.CLOSED;
        closedAt = now;
        return getClosing();
    }

    private void requireOpen() {
        if (status != Status.OPEN) {
            throw BusinessException.rule("Caixa fechado");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw BusinessException.rule("Valor deve ser positivo");
        }
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public String getOperator() { return operator; }
    public Status getStatus() { return status; }
    public BigDecimal getOpeningFloat() { return openingFloat; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public List<CashMovement> getMovements() { return Collections.unmodifiableList(movements); }
    public List<ClosingLine> getClosing() { return Collections.unmodifiableList(closing); }
}
