package posfiscalnfce.fiscal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Autorizador simulado para demo e testes. Pode ser "derrubado" pelo endpoint de sandbox
 * para demonstrar a contingência offline. Rejeita XML sem assinatura (código 297 da SEFAZ).
 */
@Component
public class SimulatedSefazGateway implements SefazGateway {

    private final AtomicBoolean online = new AtomicBoolean(true);
    private final Clock clock;

    public SimulatedSefazGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Result authorize(String accessKey, String signedXml) {
        requireOnline();
        if (!signedXml.contains("SignatureValue")) {
            return new Rejected("297", "Rejeição: Assinatura difere do calculado");
        }
        return new Authorized(protocol(), clock.instant());
    }

    @Override
    public String cancel(String accessKey, String protocol, String justification) {
        requireOnline();
        return protocol();
    }

    public void setOnline(boolean value) {
        online.set(value);
    }

    public boolean isOnline() {
        return online.get();
    }

    private void requireOnline() {
        if (!online.get()) {
            throw new SefazUnavailableException("SEFAZ indisponível (simulado)");
        }
    }

    private String protocol() {
        return "135" + "%012d".formatted(ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
    }

    @RestController
    @Tag(name = "Sandbox")
    static class SandboxController {

        private final SimulatedSefazGateway gateway;

        SandboxController(SimulatedSefazGateway gateway) {
            this.gateway = gateway;
        }

        @PostMapping("/v1/admin/sandbox/sefaz")
        @Operation(summary = "Liga/desliga a SEFAZ simulada para demonstrar a contingência offline")
        Map<String, Boolean> toggle(@RequestParam boolean online) {
            gateway.setOnline(online);
            return Map.of("online", gateway.isOnline());
        }

        @GetMapping("/v1/admin/sandbox/sefaz")
        Map<String, Boolean> status() {
            return Map.of("online", gateway.isOnline());
        }
    }
}
