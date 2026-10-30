package posfiscalnfce.sale;

import posfiscalnfce.product.Product;
import posfiscalnfce.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.UUID;

/** Item vendido, com uma cópia dos dados fiscais do produto no momento da venda. */
@Embeddable
public class SaleItem {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(nullable = false, length = 8)
    private String ncm;

    @Column(nullable = false, length = 4)
    private String cfop;

    @Column(nullable = false, length = 3)
    private String csosn;

    @Column(nullable = false, length = 6)
    private String unit;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    protected SaleItem() {
    }

    SaleItem(Product product, BigDecimal quantity) {
        this.productId = product.getId();
        this.sku = product.getSku();
        this.description = product.getName();
        this.ncm = product.getNcm();
        this.cfop = product.getCfop();
        this.csosn = product.getCsosn();
        this.unit = product.getUnit();
        this.quantity = quantity;
        this.unitPrice = product.getPrice();
        this.total = Money.of(product.getPrice().multiply(quantity));
    }

    public UUID getProductId() { return productId; }
    public String getSku() { return sku; }
    public String getDescription() { return description; }
    public String getNcm() { return ncm; }
    public String getCfop() { return cfop; }
    public String getCsosn() { return csosn; }
    public String getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotal() { return total; }
}
