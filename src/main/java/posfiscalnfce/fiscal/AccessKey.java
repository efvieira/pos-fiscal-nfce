package posfiscalnfce.fiscal;

import java.time.LocalDate;

/**
 * Chave de acesso de 44 dígitos da NF-e/NFC-e:
 * cUF(2) AAMM(4) CNPJ(14) modelo(2) série(3) número(9) tpEmis(1) cNF(8) DV(1).
 */
public record AccessKey(String value) {

    public AccessKey {
        if (value == null || !value.matches("\\d{44}")) {
            throw new IllegalArgumentException("Chave de acesso deve ter 44 dígitos");
        }
        if (checkDigit(value.substring(0, 43)) != value.charAt(43) - '0') {
            throw new IllegalArgumentException("Dígito verificador inválido");
        }
    }

    public static AccessKey of(String ufCode, LocalDate issueDate, String cnpj, int model, int series, long number,
                               EmissionType emissionType, int randomCode) {
        var base = ufCode
                + "%02d%02d".formatted(issueDate.getYear() % 100, issueDate.getMonthValue())
                + cnpj
                + "%02d".formatted(model)
                + "%03d".formatted(series)
                + "%09d".formatted(number)
                + emissionType.code()
                + "%08d".formatted(randomCode);
        return new AccessKey(base + checkDigit(base));
    }

    /** Módulo 11 com pesos de 2 a 9 da direita para a esquerda; resto 0 ou 1 resulta em DV 0. */
    static int checkDigit(String digits) {
        int sum = 0;
        int weight = 2;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    public EmissionType emissionType() {
        return EmissionType.fromCode(value.charAt(34) - '0');
    }

    /** Formato impresso no DANFE: grupos de 4 dígitos. */
    public String formatted() {
        return value.replaceAll("(\\d{4})(?=\\d)", "$1 ");
    }

    @Override
    public String toString() {
        return value;
    }
}
