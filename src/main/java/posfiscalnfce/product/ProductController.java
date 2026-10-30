package posfiscalnfce.product;

import posfiscalnfce.audit.AuditService;
import posfiscalnfce.shared.BusinessException;
import posfiscalnfce.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/products")
@Tag(name = "Produtos e estoque")
public class ProductController {

    public record ProductRequest(
            @NotBlank String sku,
            @NotBlank String name,
            @Pattern(regexp = "\\d{8}|\\d{12,14}") String gtin,
            @Pattern(regexp = "\\d{8}", message = "NCM tem 8 dígitos") String ncm,
            @Pattern(regexp = "5\\d{3}", message = "CFOP de saída interna começa com 5") String cfop,
            @Pattern(regexp = "101|102|103|300|400|500|900") String csosn,
            @NotBlank String unit,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @NotNull @DecimalMin("0") BigDecimal stock,
            Boolean active) {
    }

    public record StockAdjustment(@NotNull BigDecimal quantity, @NotBlank String reason) {
    }

    public record ProductResponse(UUID id, String sku, String name, String gtin, String ncm, String cfop,
                                  String csosn, String unit, BigDecimal price, BigDecimal stock, boolean active) {
        static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getGtin(), p.getNcm(), p.getCfop(),
                    p.getCsosn(), p.getUnit(), p.getPrice(), p.getStock(), p.isActive());
        }
    }

    private final ProductRepository products;
    private final AuditService audit;

    public ProductController(ProductRepository products, AuditService audit) {
        this.products = products;
        this.audit = audit;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return products.findByStoreIdOrderByName(TenantContext.storeId()).stream().map(ProductResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ProductResponse create(@Valid @RequestBody ProductRequest r) {
        var storeId = TenantContext.storeId();
        if (products.existsByStoreIdAndSku(storeId, r.sku())) {
            throw BusinessException.rule("SKU já cadastrado: " + r.sku());
        }
        var product = products.save(new Product(storeId, r.sku(), r.name(), r.gtin(), r.ncm(), r.cfop(), r.csosn(),
                r.unit(), r.price(), r.stock()));
        audit.record(storeId, "PRODUCT_CREATED", product.getId(), r.sku() + " " + r.name());
        return ProductResponse.from(product);
    }

    @PutMapping("/{id}")
    @Transactional
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest r) {
        var product = find(id);
        product.update(r.name(), r.price(), r.ncm(), r.cfop(), r.csosn(), r.active() == null || r.active());
        audit.record(product.getStoreId(), "PRODUCT_UPDATED", id, "preço " + r.price());
        return ProductResponse.from(product);
    }

    @PostMapping("/{id}/stock-adjustments")
    @Transactional
    public ProductResponse adjustStock(@PathVariable UUID id, @Valid @RequestBody StockAdjustment adjustment) {
        var product = find(id);
        if (adjustment.quantity().signum() >= 0) {
            product.increaseStock(adjustment.quantity());
        } else {
            product.decreaseStock(adjustment.quantity().negate());
        }
        audit.record(product.getStoreId(), "STOCK_ADJUSTED", id, adjustment.quantity() + " (" + adjustment.reason() + ")");
        return ProductResponse.from(product);
    }

    private Product find(UUID id) {
        return products.findByIdAndStoreId(id, TenantContext.storeId()).orElseThrow(() -> BusinessException.notFound("Produto"));
    }
}
