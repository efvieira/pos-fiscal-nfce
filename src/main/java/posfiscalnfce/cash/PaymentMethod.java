package posfiscalnfce.cash;

/** Meios de pagamento com o código tPag do layout da NFC-e. */
public enum PaymentMethod {
    CASH("01", "Dinheiro"),
    CREDIT_CARD("03", "Cartão de crédito"),
    DEBIT_CARD("04", "Cartão de débito"),
    PIX("17", "Pix");

    private final String tPag;
    private final String label;

    PaymentMethod(String tPag, String label) {
        this.tPag = tPag;
        this.label = label;
    }

    public String tPag() {
        return tPag;
    }

    public String label() {
        return label;
    }
}
