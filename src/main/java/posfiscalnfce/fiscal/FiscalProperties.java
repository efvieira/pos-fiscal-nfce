package posfiscalnfce.fiscal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param environment tpAmb: 1 = produção, 2 = homologação
 */
@ConfigurationProperties("fiscal")
public record FiscalProperties(
        int environment,
        String qrCodeUrl,
        String consultUrl,
        Duration cancelWindow,
        Duration contingencyRetryInterval,
        Certificate certificate) {

    public record Certificate(String path, String password) {
    }
}
