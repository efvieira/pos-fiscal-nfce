package posfiscalnfce.cash;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.time.Instant;

@Embeddable
public class CashMovement {

    public enum Type { SUPPLY, WITHDRAWAL, SALE, SALE_CANCELLATION }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 120)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected CashMovement() {
    }

    CashMovement(Type type, PaymentMethod method, BigDecimal amount, String note, Instant occurredAt) {
        this.type = type;
        this.method = method;
        this.amount = amount;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    /** Valor com sinal: saídas do caixa entram negativas. */
    BigDecimal signedAmount() {
        return switch (type) {
            case SUPPLY, SALE -> amount;
            case WITHDRAWAL, SALE_CANCELLATION -> amount.negate();
        };
    }

    public Type getType() { return type; }
    public PaymentMethod getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
}
