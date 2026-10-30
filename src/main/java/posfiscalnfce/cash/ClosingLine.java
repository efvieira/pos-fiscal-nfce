package posfiscalnfce.cash;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

/** Conferência de um meio de pagamento no fechamento: esperado x contado. */
@Embeddable
public class ClosingLine {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal expected;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal counted;

    protected ClosingLine() {
    }

    ClosingLine(PaymentMethod method, BigDecimal expected, BigDecimal counted) {
        this.method = method;
        this.expected = expected;
        this.counted = counted;
    }

    public PaymentMethod getMethod() { return method; }
    public BigDecimal getExpected() { return expected; }
    public BigDecimal getCounted() { return counted; }
    public BigDecimal getDifference() { return counted.subtract(expected); }
}
