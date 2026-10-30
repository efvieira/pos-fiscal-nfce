package posfiscalnfce.fiscal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessKeyTest {

    @Test
    void buildsKeyWithLayoutAndModulo11CheckDigit() {
        var key = AccessKey.of("35", LocalDate.of(2026, 3, 15), "11222333000181", 65, 1, 123,
                EmissionType.NORMAL, 4567);

        assertThat(key.value()).hasSize(44).startsWith("3526031122233300018165001000000123100004567");
        assertThat(key.emissionType()).isEqualTo(EmissionType.NORMAL);
        assertThat(key.formatted()).hasSize(44 + 10);
    }

    @Test
    void checkDigitFollowsModulo11Rules() {
        // soma ponderada de "1" = 2 -> resto 2 -> DV 9
        assertThat(AccessKey.checkDigit("1")).isEqualTo(9);
        // resto 0 ou 1 resulta em DV 0
        assertThat(AccessKey.checkDigit("0")).isZero();
    }

    @Test
    void contingencyKeyCarriesEmissionType9() {
        var key = AccessKey.of("35", LocalDate.of(2026, 3, 15), "11222333000181", 65, 1, 1,
                EmissionType.OFFLINE_CONTINGENCY, 1);
        assertThat(key.value().charAt(34)).isEqualTo('9');
        assertThat(key.emissionType()).isEqualTo(EmissionType.OFFLINE_CONTINGENCY);
    }

    @Test
    void rejectsInvalidKeys() {
        var valid = AccessKey.of("35", LocalDate.of(2026, 3, 15), "11222333000181", 65, 1, 1, EmissionType.NORMAL, 1).value();
        var tampered = valid.substring(0, 43) + ((valid.charAt(43) - '0' + 1) % 10);
        assertThatThrownBy(() -> new AccessKey(tampered)).hasMessageContaining("Dígito");
        assertThatThrownBy(() -> new AccessKey("123")).hasMessageContaining("44");
        assertThatThrownBy(() -> EmissionType.fromCode(5)).isInstanceOf(IllegalArgumentException.class);
    }
}
