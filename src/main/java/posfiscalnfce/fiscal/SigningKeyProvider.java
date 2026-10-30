package posfiscalnfce.fiscal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
 * Fornece a chave de assinatura. Com FISCAL_CERT_PATH/FISCAL_CERT_PASSWORD usa o certificado
 * A1 (PKCS#12) da empresa; sem eles gera uma chave RSA efêmera, suficiente para o modo
 * simulado. O certificado nunca deve ser versionado.
 */
@Component
public class SigningKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyProvider.class);

    public record SigningKey(PrivateKey privateKey, PublicKey publicKey, X509Certificate certificate) {
    }

    private final SigningKey key;

    public SigningKeyProvider(FiscalProperties props) throws Exception {
        var cert = props.certificate();
        this.key = cert != null && cert.path() != null && !cert.path().isBlank()
                ? loadPkcs12(cert.path(), cert.password())
                : ephemeral();
    }

    public SigningKey key() {
        return key;
    }

    private static SigningKey loadPkcs12(String path, String password) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        try (var in = new FileInputStream(path)) {
            keyStore.load(in, password.toCharArray());
        }
        var alias = keyStore.aliases().nextElement();
        var certificate = (X509Certificate) keyStore.getCertificate(alias);
        var privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
        log.info("Certificado A1 carregado: {}", certificate.getSubjectX500Principal().getName());
        return new SigningKey(privateKey, certificate.getPublicKey(), certificate);
    }

    private static SigningKey ephemeral() throws GeneralSecurityException {
        log.warn("Sem certificado A1 configurado: usando chave efêmera (apenas para o modo simulado)");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        return new SigningKey(pair.getPrivate(), pair.getPublic(), null);
    }
}
