package posfiscalnfce.fiscal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Retransmite as notas pendentes (contingência ou que caíram entre a venda e o envio). */
@Component
public class ContingencyRetryJob {

    private static final Logger log = LoggerFactory.getLogger(ContingencyRetryJob.class);

    private final FiscalDocumentRepository documents;
    private final FiscalService fiscal;

    public ContingencyRetryJob(FiscalDocumentRepository documents, FiscalService fiscal) {
        this.documents = documents;
        this.fiscal = fiscal;
    }

    @Scheduled(fixedDelayString = "${fiscal.contingency-retry-interval}")
    public int retryPending() {
        var pending = documents.findByStatusInOrderByIssuedAt(
                List.of(FiscalDocument.Status.CREATED, FiscalDocument.Status.CONTINGENCY_PENDING), Limit.of(50));
        int authorized = 0;
        for (var document : pending) {
            if (fiscal.transmit(document.getId()).getStatus() == FiscalDocument.Status.AUTHORIZED) {
                authorized++;
            }
        }
        if (!pending.isEmpty()) {
            log.info("Retransmissão: {} de {} notas autorizadas", authorized, pending.size());
        }
        return authorized;
    }
}
