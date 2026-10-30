package posfiscalnfce.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "username", nullable = false, length = 60)
    private String user;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_id", length = 60)
    private String entityId;

    @Column(length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEntry() {
    }

    AuditEntry(UUID storeId, String user, String action, String entityId, String details, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.storeId = storeId;
        this.user = user;
        this.action = action;
        this.entityId = entityId;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public String getUser() { return user; }
    public String getAction() { return action; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}
