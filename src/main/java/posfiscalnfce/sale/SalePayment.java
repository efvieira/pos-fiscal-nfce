package posfiscalnfce.sale;

import posfiscalnfce.cash.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

@Embeddable
public class SalePayment {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    protected SalePayment() {
    }

    SalePayment(PaymentMethod method, BigDecimal amount) {
        this.method = method;
        this.amount = amount;
    }

    public PaymentMethod getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
}
