package posfiscalnfce.report;

import posfiscalnfce.cash.CashSession;
import posfiscalnfce.cash.CashSessionRepository;
import posfiscalnfce.cash.ClosingLine;
import posfiscalnfce.cash.PaymentMethod;
import posfiscalnfce.fiscal.FiscalDocument;
import posfiscalnfce.fiscal.FiscalDocumentRepository;
import posfiscalnfce.sale.Sale;
import posfiscalnfce.sale.SaleRepository;
import posfiscalnfce.shared.ClockConfig;
import posfiscalnfce.shared.Money;
import posfiscalnfce.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@RestController
@RequestMapping("/v1/reports")
@Tag(name = "Relatórios")
public class ReportController {

    public record SalesReport(LocalDate from, LocalDate to, int count, BigDecimal gross, BigDecimal averageTicket,
                              Map<PaymentMethod, BigDecimal> byPaymentMethod, Map<LocalDate, BigDecimal> byDay) {
    }

    public record ClosingReport(UUID sessionId, String operator, Instant openedAt, Instant closedAt,
                                BigDecimal totalDifference, List<ClosingLine> lines) {
    }

    public record PendingFiscal(UUID id, long number, String accessKey, FiscalDocument.Status status, int attempts) {
    }

    private final SaleRepository sales;
    private final CashSessionRepository sessions;
    private final FiscalDocumentRepository documents;

    public ReportController(SaleRepository sales, CashSessionRepository sessions, FiscalDocumentRepository documents) {
        this.sales = sales;
        this.sessions = sessions;
        this.documents = documents;
    }

    @GetMapping("/sales")
    public SalesReport sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var list = sales.findByStoreIdAndStatusAndCreatedAtBetween(TenantContext.storeId(), Sale.Status.COMPLETED,
                start(from), start(to.plusDays(1)));
        var byMethod = new EnumMap<PaymentMethod, BigDecimal>(PaymentMethod.class);
        var byDay = new TreeMap<LocalDate, BigDecimal>();
        for (var sale : list) {
            sale.netByMethod().forEach((m, v) -> byMethod.merge(m, v, BigDecimal::add));
            byDay.merge(LocalDate.ofInstant(sale.getCreatedAt(), ClockConfig.BRT), sale.getTotal(), BigDecimal::add);
        }
        var gross = Money.sum(list, Sale::getTotal);
        var average = list.isEmpty() ? BigDecimal.ZERO : Money.of(gross.divide(BigDecimal.valueOf(list.size()), java.math.RoundingMode.HALF_EVEN));
        return new SalesReport(from, to, list.size(), gross, average, byMethod, byDay);
    }

    @GetMapping("/cash-closings")
    public List<ClosingReport> closings(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return sessions.findByStoreIdAndStatusAndClosedAtBetweenOrderByClosedAt(TenantContext.storeId(),
                        CashSession.Status.CLOSED, start(from), start(to.plusDays(1))).stream()
                .map(s -> new ClosingReport(s.getId(), s.getOperator(), s.getOpenedAt(), s.getClosedAt(),
                        s.getClosing().stream().map(ClosingLine::getDifference).reduce(BigDecimal.ZERO, BigDecimal::add),
                        s.getClosing()))
                .toList();
    }

    @GetMapping("/fiscal-pending")
    public List<PendingFiscal> pendingFiscal() {
        return documents.findByStoreIdAndStatusOrderByIssuedAt(TenantContext.storeId(), FiscalDocument.Status.CONTINGENCY_PENDING)
                .stream()
                .map(d -> new PendingFiscal(d.getId(), d.getNumber(), d.getAccessKey().value(), d.getStatus(), d.getTransmissionAttempts()))
                .toList();
    }

    private static Instant start(LocalDate date) {
        return date.atStartOfDay(ClockConfig.BRT).toInstant();
    }
}
