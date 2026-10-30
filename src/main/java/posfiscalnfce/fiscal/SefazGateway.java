package posfiscalnfce.fiscal;

import java.time.Instant;

/**
 * Porta de saída para o autorizador (SEFAZ). O resto do sistema não sabe se do outro lado
 * está a SEFAZ-SP via SOAP (ex.: biblioteca Java_NFe), um provedor como Focus NFe ou o
 * simulador local: basta trocar o bean.
 */
public interface SefazGateway {

    sealed interface Result {
    }

    record Authorized(String protocol, Instant at) implements Result {
    }

    record Rejected(String code, String reason) implements Result {
    }

    /** @throws SefazUnavailableException quando não há comunicação (timeout, serviço fora do ar) */
    Result authorize(String accessKey, String signedXml);

    /** @return protocolo do evento de cancelamento */
    String cancel(String accessKey, String protocol, String justification);

    class SefazUnavailableException extends RuntimeException {
        public SefazUnavailableException(String message) {
            super(message);
        }
    }
}
