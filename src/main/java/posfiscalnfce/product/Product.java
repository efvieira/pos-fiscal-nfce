package posfiscalnfce.product;

import posfiscalnfce.shared.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Produto com os dados fiscais exigidos na NFC-e: NCM (classificação), CFOP (natureza da
 * operação) e CSOSN (tributação do ICMS no Simples Nacional).
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 14)
    private String gtin;

    @Column(nullable = false, length = 8)
    private String ncm;

    @Column(nullable = false, length = 4)
    private String cfop;

    @Column(nullable = false, length = 3)
    private String csosn;

    @Column(nullable = false, length = 6)
    private String unit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal stock;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    protected Product() {
    }

    Product(UUID storeId, String sku, String name, String gtin, String ncm, String cfop, String csosn,
            String unit, BigDecimal price, BigDecimal stock) {
        this.id = UUID.randomUUID();
        this.storeId = storeId;
        this.sku = sku;
        this.name = name;
        this.gtin = gtin;
        this.ncm = ncm;
        this.cfop = cfop;
        this.csosn = csosn;
        this.unit = unit;
        this.price = price;
        this.stock = stock;
    }

    public void decreaseStock(BigDecimal quantity) {
        if (stock.compareTo(quantity) < 0) {
            throw BusinessException.rule("Estoque insuficiente para %s: disponível %s".formatted(name, stock.stripTrailingZeros().toPlainString()));
        }
        stock = stock.subtract(quantity);
    }

    public void increaseStock(BigDecimal quantity) {
        stock = stock.add(quantity);
    }

    void update(String name, BigDecimal price, String ncm, String cfop, String csosn, boolean active) {
        this.name = name;
        this.price = price;
        this.ncm = ncm;
        this.cfop = cfop;
        this.csosn = csosn;
        this.active = active;
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getGtin() { return gtin; }
    public String getNcm() { return ncm; }
    public String getCfop() { return cfop; }
    public String getCsosn() { return csosn; }
    public String getUnit() { return unit; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getStock() { return stock; }
    public boolean isActive() { return active; }
}
