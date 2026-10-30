package posfiscalnfce.audit;

import posfiscalnfce.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Registra quem fez o quê e quando, na mesma transação da operação auditada. */
@Service
public class AuditService {

    public record AuditView(Instant at, String user, String action, String entityId, String details) {
    }

    private final AuditRepository repository;
    private final Clock clock;

    AuditService(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID storeId, String action, Object entityId, String details) {
        repository.save(new AuditEntry(storeId, TenantContext.user(), action, String.valueOf(entityId), details, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<AuditView> latest(int limit) {
        return repository.findByStoreId(TenantContext.storeId(),
                        PageRequest.of(0, Math.min(limit, 500), Sort.by(Sort.Direction.DESC, "occurredAt")))
                .stream()
                .map(e -> new AuditView(e.getOccurredAt(), e.getUser(), e.getAction(), e.getEntityId(), e.getDetails()))
                .toList();
    }

    @RestController
    @Tag(name = "Auditoria")
    static class AuditController {

        private final AuditService service;

        AuditController(AuditService service) {
            this.service = service;
        }

        @GetMapping("/v1/audit")
        List<AuditView> latest(@RequestParam(defaultValue = "100") int limit) {
            return service.latest(limit);
        }
    }
}
