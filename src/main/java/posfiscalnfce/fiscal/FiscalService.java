package posfiscalnfce.fiscal;

import posfiscalnfce.audit.AuditService;
import posfiscalnfce.fiscal.SefazGateway.Authorized;
import posfiscalnfce.fiscal.SefazGateway.Rejected;
import posfiscalnfce.fiscal.SefazGateway.SefazUnavailableException;
import posfiscalnfce.sale.Sale;
import posfiscalnfce.sale.SaleRepository;
import posfiscalnfce.shared.BusinessException;
import posfiscalnfce.tenant.Store;
import posfiscalnfce.tenant.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Orquestra a emissão da NFC-e. A venda nunca para por causa da SEFAZ: se o autorizador
 * não responde, a nota é reemitida em contingência offline (tpEmis 9), impressa com o
 * aviso de contingência e transmitida depois pelo job de retransmissão.
 */
@Service
public class FiscalService {

    private static final Logger log = LoggerFactory.getLogger(FiscalService.class);
    private static final int MODEL_NFCE = 65;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FiscalDocumentRepository documents;
    private final StoreRepository stores;
    private final SaleRepository sales;
    private final SefazGateway sefaz;
    private final XmlSigner signer;
    private final AuditService audit;
    private final FiscalProperties props;
    private final Clock clock;

    public FiscalService(FiscalDocumentRepository documents, StoreRepository stores, SaleRepository sales,
                         SefazGateway sefaz, XmlSigner signer, AuditService audit, FiscalProperties props, Clock clock) {
        this.documents = documents;
        this.stores = stores;
        this.sales = sales;
        this.sefaz = sefaz;
        this.signer = signer;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    /** Numera, gera e assina a NFC-e na mesma transação da venda. Ainda não transmite. */
    @Transactional(propagation = Propagation.MANDATORY)
    public FiscalDocument prepare(Store lockedStore, Sale sale) {
        var number = lockedStore.takeNextNfceNumber();
        var document = new FiscalDocument(lockedStore.getId(), sale.getId(), number, lockedStore.getNfceSeries(), clock.instant());
        buildAndSign(document, lockedStore, sale, EmissionType.NORMAL, null);
        return documents.save(document);
    }

    /** Transmite (ou retransmite) um documento. Roda fora da transação da venda. */
    @Transactional
    public FiscalDocument transmit(UUID documentId) {
        var document = documents.findById(documentId).orElseThrow(() -> BusinessException.notFound("Documento fiscal"));
        if (document.getStatus() != FiscalDocument.Status.CREATED
                && document.getStatus() != FiscalDocument.Status.CONTINGENCY_PENDING) {
            return document;
        }
        try {
            switch (sefaz.authorize(document.getAccessKey().value(), document.getXml())) {
                case Authorized(var protocol, var at) -> {
                    document.authorized(protocol, at);
                    audit.record(document.getStoreId(), "NFCE_AUTHORIZED", document.getAccessKey(), "protocolo " + protocol);
                }
                case Rejected(var code, var reason) -> {
                    document.rejected(code + " - " + reason);
                    audit.record(document.getStoreId(), "NFCE_REJECTED", document.getAccessKey(), code + " " + reason);
                }
            }
        } catch (SefazUnavailableException e) {
            if (document.getStatus() == FiscalDocument.Status.CREATED) {
                enterContingency(document, e.getMessage());
            } else {
                document.transmissionFailed(e.getMessage());
            }
        }
        return document;
    }

    @Transactional
    public FiscalDocument cancel(UUID storeId, UUID saleId, String justification) {
        var document = documents.findBySaleId(saleId)
                .filter(d -> d.getStoreId().equals(storeId))
                .orElseThrow(() -> BusinessException.notFound("Documento fiscal"));
        if (justification == null || justification.strip().length() < 15) {
            throw BusinessException.rule("Justificativa do cancelamento precisa ter ao menos 15 caracteres");
        }
        if (document.getStatus() != FiscalDocument.Status.AUTHORIZED) {
            throw BusinessException.rule("Só NFC-e autorizada pode ser cancelada (status atual: " + document.getStatus() + ")");
        }
        var elapsed = Duration.between(document.getAuthorizedAt(), clock.instant());
        if (elapsed.compareTo(props.cancelWindow()) > 0) {
            throw BusinessException.rule("Prazo de cancelamento expirado (" + props.cancelWindow().toMinutes() + " minutos)");
        }
        var protocol = sefaz.cancel(document.getAccessKey().value(), document.getProtocol(), justification);
        document.cancelled(protocol, justification);
        audit.record(storeId, "NFCE_CANCELLED", document.getAccessKey(), justification);
        return document;
    }

    private void enterContingency(FiscalDocument document, String reason) {
        var store = stores.findById(document.getStoreId()).orElseThrow();
        var sale = sales.findById(document.getSaleId()).orElseThrow();
        buildAndSign(document, store, sale, EmissionType.OFFLINE_CONTINGENCY, "SEFAZ indisponivel: " + reason);
        log.warn("NFC-e {} emitida em contingência offline: {}", document.getNumber(), reason);
        audit.record(document.getStoreId(), "NFCE_CONTINGENCY", document.getAccessKey(), reason);
    }

    private void buildAndSign(FiscalDocument document, Store store, Sale sale, EmissionType type, String contingencyReason) {
        var issuedAt = OffsetDateTime.now(clock);
        var key = AccessKey.of(NfceXmlBuilder.ufCode(store.getUf()), issuedAt.toLocalDate(), store.getCnpj(),
                MODEL_NFCE, store.getNfceSeries(), document.getNumber(), type, RANDOM.nextInt(100_000_000));
        var xml = NfceXmlBuilder.build(store, sale, key, document.getNumber(), issuedAt, props.environment(), contingencyReason);
        var signed = signer.sign(xml);
        var qrCode = type == EmissionType.NORMAL
                ? NfceQrCode.online(props.qrCodeUrl(), key, props.environment(), store.getCscId(), store.getCsc())
                : NfceQrCode.offline(props.qrCodeUrl(), key, props.environment(), issuedAt.getDayOfMonth(),
                sale.getTotal(), signed.digestValueHex(), store.getCscId(), store.getCsc());
        NfceXmlBuilder.addSupplement(signed.document(), qrCode, props.consultUrl());
        document.attachXml(key, NfceXmlBuilder.toString(signed.document()), qrCode);
    }
}
