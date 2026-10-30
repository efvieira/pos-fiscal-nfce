package posfiscalnfce.fiscal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fiscal_documents")
public class FiscalDocument {

    public enum Status {
        /** Assinada, ainda não transmitida. */
        CREATED,
        /** Emitida em contingência offline; precisa ser transmitida depois. */
        CONTINGENCY_PENDING,
        AUTHORIZED,
        REJECTED,
        CANCELLED
    }

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "sale_id", nullable = false, unique = true)
    private UUID saleId;

    @Column(nullable = false)
    private long number;

    @Column(nullable = false)
    private int series;

    @Column(name = "access_key", nullable = false, unique = true, length = 44)
    private String accessKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "emission_type", nullable = false, length = 24)
    private EmissionType emissionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column(nullable = false, columnDefinition = "text")
    private String xml;

    @Column(name = "qr_code_url", nullable = false, length = 600)
    private String qrCodeUrl;

    @Column(length = 20)
    private String protocol;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "transmission_attempts", nullable = false)
    private int transmissionAttempts;

    @Version
    private long version;

    protected FiscalDocument() {
    }

    FiscalDocument(UUID storeId, UUID saleId, long number, int series, Instant issuedAt) {
        this.id = UUID.randomUUID();
        this.storeId = storeId;
        this.saleId = saleId;
        this.number = number;
        this.series = series;
        this.issuedAt = issuedAt;
        this.status = Status.CREATED;
    }

    void attachXml(AccessKey key, String xml, String qrCodeUrl) {
        this.accessKey = key.value();
        this.emissionType = key.emissionType();
        this.xml = xml;
        this.qrCodeUrl = qrCodeUrl;
        if (emissionType == EmissionType.OFFLINE_CONTINGENCY) {
            this.status = Status.CONTINGENCY_PENDING;
        }
    }

    void authorized(String protocol, Instant at) {
        this.status = Status.AUTHORIZED;
        this.protocol = protocol;
        this.authorizedAt = at;
        this.statusReason = null;
    }

    void rejected(String reason) {
        this.status = Status.REJECTED;
        this.statusReason = reason;
    }

    void transmissionFailed(String reason) {
        this.transmissionAttempts++;
        this.statusReason = reason;
    }

    void cancelled(String protocol, String justification) {
        this.status = Status.CANCELLED;
        this.protocol = protocol;
        this.statusReason = justification;
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public UUID getSaleId() { return saleId; }
    public long getNumber() { return number; }
    public int getSeries() { return series; }
    public AccessKey getAccessKey() { return new AccessKey(accessKey); }
    public EmissionType getEmissionType() { return emissionType; }
    public Status getStatus() { return status; }
    public String getXml() { return xml; }
    public String getQrCodeUrl() { return qrCodeUrl; }
    public String getProtocol() { return protocol; }
    public String getStatusReason() { return statusReason; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getAuthorizedAt() { return authorizedAt; }
    public int getTransmissionAttempts() { return transmissionAttempts; }
}
