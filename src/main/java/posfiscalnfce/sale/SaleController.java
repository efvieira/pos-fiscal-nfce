package posfiscalnfce.sale;

import posfiscalnfce.cash.PaymentMethod;
import posfiscalnfce.fiscal.DanfeRenderer;
import posfiscalnfce.fiscal.EmissionType;
import posfiscalnfce.fiscal.FiscalDocument;
import posfiscalnfce.fiscal.FiscalDocumentRepository;
import posfiscalnfce.fiscal.FiscalService;
import posfiscalnfce.sale.SaleService.ItemRequest;
import posfiscalnfce.shared.BusinessException;
import posfiscalnfce.tenant.StoreRepository;
import posfiscalnfce.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sales")
@Tag(name = "Vendas e NFC-e")
public class SaleController {

    public record CheckoutRequest(@NotEmpty List<@Valid ItemRequest> items,
                                  @NotEmpty Map<PaymentMethod, BigDecimal> payments) {
    }

    public record CancelRequest(@NotBlank String justification) {
    }

    public record FiscalView(UUID id, long number, int series, String accessKey, FiscalDocument.Status status,
                             EmissionType emissionType, String protocol, String statusReason, String qrCodeUrl) {
        static FiscalView from(FiscalDocument d) {
            return new FiscalView(d.getId(), d.getNumber(), d.getSeries(), d.getAccessKey().value(), d.getStatus(),
                    d.getEmissionType(), d.getProtocol(), d.getStatusReason(), d.getQrCodeUrl());
        }
    }

    public record SaleView(UUID id, Sale.Status status, BigDecimal total, BigDecimal change, Instant createdAt,
                           List<SaleItem> items, List<SalePayment> payments, FiscalView nfce) {
        static SaleView from(Sale s, FiscalDocument d) {
            return new SaleView(s.getId(), s.getStatus(), s.getTotal(), s.getChange(), s.getCreatedAt(),
                    s.getItems(), s.getPayments(), d == null ? null : FiscalView.from(d));
        }
    }

    private final SaleService service;
    private final SaleRepository sales;
    private final FiscalService fiscal;
    private final FiscalDocumentRepository documents;
    private final StoreRepository stores;
    private final DanfeRenderer danfe;

    public SaleController(SaleService service, SaleRepository sales, FiscalService fiscal,
                          FiscalDocumentRepository documents, StoreRepository stores, DanfeRenderer danfe) {
        this.service = service;
        this.sales = sales;
        this.fiscal = fiscal;
        this.documents = documents;
        this.stores = stores;
        this.danfe = danfe;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Finaliza a venda e emite a NFC-e (em contingência se a SEFAZ estiver fora)")
    public SaleView checkout(@Valid @RequestBody CheckoutRequest request) {
        var result = service.checkout(request.items(), request.payments());
        var document = fiscal.transmit(result.document().getId());
        return SaleView.from(result.sale(), document);
    }

    @GetMapping("/{id}")
    public SaleView get(@PathVariable UUID id) {
        var sale = findSale(id);
        return SaleView.from(sale, documents.findBySaleId(id).orElse(null));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela a venda e a NFC-e (dentro do prazo legal)")
    public SaleView cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
        var sale = service.cancel(id, request.justification());
        return SaleView.from(sale, documents.findBySaleId(id).orElse(null));
    }

    @GetMapping(value = "/{id}/nfce/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String xml(@PathVariable UUID id) {
        return document(id).getXml();
    }

    @GetMapping("/{id}/nfce/danfe")
    public ResponseEntity<byte[]> danfe(@PathVariable UUID id) {
        var sale = findSale(id);
        var document = document(id);
        var store = stores.findById(sale.getStoreId()).orElseThrow();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=nfce-" + document.getNumber() + ".pdf")
                .body(danfe.render(document, store, sale));
    }

    private Sale findSale(UUID id) {
        return sales.findByIdAndStoreId(id, TenantContext.storeId()).orElseThrow(() -> BusinessException.notFound("Venda"));
    }

    private FiscalDocument document(UUID saleId) {
        return documents.findBySaleId(saleId)
                .filter(d -> d.getStoreId().equals(TenantContext.storeId()))
                .orElseThrow(() -> BusinessException.notFound("Documento fiscal"));
    }
}
