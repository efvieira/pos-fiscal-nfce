package posfiscalnfce.sale;

import posfiscalnfce.cash.PaymentMethod;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "sales")
public class Sale {

    public enum Status { COMPLETED, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "cash_session_id", nullable = false)
    private UUID cashSessionId;

    @Column(nullable = false, length = 60)
    private String operator;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sale_items", joinColumns = @JoinColumn(name = "sale_id"))
    @OrderColumn(name = "position")
    private List<SaleItem> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sale_payments", joinColumns = @JoinColumn(name = "sale_id"))
    @OrderColumn(name = "position")
    private List<SalePayment> payments = new ArrayList<>();

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "change_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal change;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Sale() {
    }

    /**
     * Fecha a venda validando o pagamento: o total pago precisa cobrir a venda e o troco
     * só pode sair do dinheiro (não se dá troco de cartão ou Pix).
     */
    static Sale complete(UUID storeId, UUID cashSessionId, String operator, List<SaleItem> items,
                         Map<PaymentMethod, BigDecimal> paid, Instant now) {
        if (items.isEmpty()) {
            throw BusinessException.rule("Venda sem itens");
        }
        var sale = new Sale();
        sale.id = UUID.randomUUID();
        sale.storeId = storeId;
        sale.cashSessionId = cashSessionId;
        sale.operator = operator;
        sale.items.addAll(items);
        sale.total = Money.sum(items, SaleItem::getTotal);
        paid.forEach((method, amount) -> {
            if (amount.signum() > 0) {
                sale.payments.add(new SalePayment(method, Money.of(amount)));
            }
        });
        var totalPaid = Money.sum(sale.payments, SalePayment::getAmount);
        if (totalPaid.compareTo(sale.total) < 0) {
            throw BusinessException.rule("Pagamento insuficiente: total %s, pago %s".formatted(sale.total, totalPaid));
        }
        sale.change = totalPaid.subtract(sale.total);
        var cash = paid.getOrDefault(PaymentMethod.CASH, BigDecimal.ZERO);
        if (sale.change.compareTo(cash) > 0) {
            throw BusinessException.rule("Troco só pode ser dado sobre pagamento em dinheiro");
        }
        sale.status = Status.COMPLETED;
        sale.createdAt = now;
        return sale;
    }

    /** Quanto de cada meio efetivamente ficou no caixa (dinheiro já descontado do troco). */
    public Map<PaymentMethod, BigDecimal> netByMethod() {
        var net = new java.util.EnumMap<PaymentMethod, BigDecimal>(PaymentMethod.class);
        payments.forEach(p -> net.merge(p.getMethod(), p.getAmount(), BigDecimal::add));
        net.computeIfPresent(PaymentMethod.CASH, (m, v) -> v.subtract(change));
        return net;
    }

    void cancel() {
        if (status == Status.CANCELLED) {
            throw BusinessException.rule("Venda já cancelada");
        }
        status = Status.CANCELLED;
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public UUID getCashSessionId() { return cashSessionId; }
    public String getOperator() { return operator; }
    public List<SaleItem> getItems() { return Collections.unmodifiableList(items); }
    public List<SalePayment> getPayments() { return Collections.unmodifiableList(payments); }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getChange() { return change; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
