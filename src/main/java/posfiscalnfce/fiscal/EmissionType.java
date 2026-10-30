package posfiscalnfce.fiscal;

/** tpEmis: 1 = emissão normal; 9 = contingência offline da NFC-e. */
public enum EmissionType {
    NORMAL(1), OFFLINE_CONTINGENCY(9);

    private final int code;

    EmissionType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static EmissionType fromCode(int code) {
        for (var type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("tpEmis não suportado: " + code);
    }
}
