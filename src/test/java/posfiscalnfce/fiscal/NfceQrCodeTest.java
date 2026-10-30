package posfiscalnfce.fiscal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class NfceQrCodeTest {

    private final AccessKey key = AccessKey.of("35", LocalDate.of(2026, 3, 15), "11222333000181", 65, 1, 10,
            EmissionType.NORMAL, 42);

    @Test
    void onlineQrCodeHashesParametersWithCsc() {
        var url = NfceQrCode.online("https://qr", key, 2, "000001", "SEGREDO");
        var params = key.value() + "|2|2|1";

        assertThat(url).isEqualTo("https://qr?p=" + params + "|" + NfceQrCode.sha1Hex(params + "SEGREDO"));
    }

    @Test
    void offlineQrCodeCarriesDayTotalAndDigest() {
        var url = NfceQrCode.offline("https://qr", key, 2, 5, new BigDecimal("12.5"), "abcd", "1", "SEGREDO");
        assertThat(url).contains("|2|2|05|12.50|abcd|1|");
    }

    @Test
    void sha1IsUppercaseHex() {
        assertThat(NfceQrCode.sha1Hex("abc")).isEqualTo("A9993E364706816ABA3E25717850C26C9CD0D89D");
    }
}
