package posfiscalnfce.fiscal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Monta a URL do QR Code da NFC-e (versão 2.00 do leiaute).
 * Online:  p = chave|2|tpAmb|idCSC|hash
 * Offline: p = chave|2|tpAmb|dia|vNF|digVal(hex)|idCSC|hash
 * O hash é SHA-1 dos parâmetros concatenados ao CSC (código de segurança do contribuinte).
 */
public final class NfceQrCode {

    private NfceQrCode() {
    }

    public static String online(String baseUrl, AccessKey key, int environment, String cscId, String csc) {
        var params = "%s|2|%d|%d".formatted(key.value(), environment, Integer.parseInt(cscId));
        return baseUrl + "?p=" + params + "|" + sha1Hex(params + csc);
    }

    public static String offline(String baseUrl, AccessKey key, int environment, int dayOfMonth, BigDecimal total,
                                 String digestValueHex, String cscId, String csc) {
        var params = "%s|2|%d|%02d|%s|%s|%d".formatted(key.value(), environment, dayOfMonth,
                total.setScale(2).toPlainString(), digestValueHex, Integer.parseInt(cscId));
        return baseUrl + "?p=" + params + "|" + sha1Hex(params + csc);
    }

    static String sha1Hex(String value) {
        try {
            return HexFormat.of().withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
