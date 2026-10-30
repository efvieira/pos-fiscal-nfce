package posfiscalnfce.sale;

import posfiscalnfce.audit.AuditService;
import posfiscalnfce.cash.CashSession;
import posfiscalnfce.cash.CashSessionRepository;
import posfiscalnfce.cash.PaymentMethod;
import posfiscalnfce.fiscal.FiscalDocument;
import posfiscalnfce.fiscal.FiscalService;
import posfiscalnfce.product.Product;
import posfiscalnfce.product.ProductRepository;
import posfiscalnfce.shared.BusinessException;
import posfiscalnfce.tenant.StoreRepository;
import posfiscalnfce.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SaleService {

    public record ItemRequest(UUID productId, BigDecimal quantity) {
    }

    public record Checkout(Sale sale, FiscalDocument document) {
    }

    private final SaleRepository sales;
    private final ProductRepository products;
    private final CashSessionRepository sessions;
    private final StoreRepository stores;
    private final FiscalService fiscal;
    private final AuditService audit;
    private final Clock clock;

    public SaleService(SaleRepository sales, ProductRepository products, CashSessionRepository sessions,
                       StoreRepository stores, FiscalService fiscal, AuditService audit, Clock clock) {
        this.sales = sales;
        this.products = products;
        this.sessions = sessions;
        this.stores = stores;
        this.fiscal = fiscal;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Registra a venda, baixa o estoque, lança no caixa e prepara a NFC-e numa única transação.
     * A transmissão para a SEFAZ acontece depois do commit (ver SaleController), para que uma
     * SEFAZ lenta não segure locks de estoque e numeração.
     */
    @Transactional
    public Checkout checkout(List<ItemRequest> requested, Map<PaymentMethod, BigDecimal> payments) {
        var storeId = TenantContext.storeId();
        var session = openSession();
        var store = stores.lockById(storeId).orElseThrow(() -> BusinessException.notFound("Loja"));

        var catalog = products.findByStoreIdAndIdIn(storeId, requested.stream().map(ItemRequest::productId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        var items = new ArrayList<SaleItem>();
        for (var request : requested) {
            var product = catalog.get(request.productId());
            if (product == null || !product.isActive()) {
                throw BusinessException.rule("Produto indisponível: " + request.productId());
            }
            if (request.quantity() == null || request.quantity().signum() <= 0) {
                throw BusinessException.rule("Quantidade inválida para " + product.getName());
            }
            product.decreaseStock(request.quantity());
            items.add(new SaleItem(product, request.quantity()));
        }

        var sale = sales.save(Sale.complete(storeId, session.getId(), session.getOperator(), items, payments, clock.instant()));
        sale.netByMethod().forEach((method, amount) ->
                session.registerSale(method, amount, sale.getId().toString(), clock.instant()));
        var document = fiscal.prepare(store, sale);
        audit.record(storeId, "SALE_COMPLETED", sale.getId(), "total " + sale.getTotal() + ", NFC-e " + document.getNumber());
        return new Checkout(sale, document);
    }

    /** Cancela a NFC-e na SEFAZ, devolve o estoque e estorna o caixa do operador. */
    @Transactional
    public Sale cancel(UUID saleId, String justification) {
        var storeId = TenantContext.storeId();
        var sale = sales.findByIdAndStoreId(saleId, storeId).orElseThrow(() -> BusinessException.notFound("Venda"));
        sale.cancel();
        fiscal.cancel(storeId, saleId, justification);
        for (var item : sale.getItems()) {
            products.findById(item.getProductId()).ifPresent(p -> p.increaseStock(item.getQuantity()));
        }
        var session = openSession();
        sale.netByMethod().forEach((method, amount) ->
                session.registerSaleCancellation(method, amount, saleId.toString(), clock.instant()));
        audit.record(storeId, "SALE_CANCELLED", saleId, justification);
        return sale;
    }

    private CashSession openSession() {
        return sessions.findByStoreIdAndOperatorAndStatus(TenantContext.storeId(), TenantContext.user(), CashSession.Status.OPEN)
                .orElseThrow(() -> BusinessException.rule("Abra o caixa antes de vender"));
    }
}
