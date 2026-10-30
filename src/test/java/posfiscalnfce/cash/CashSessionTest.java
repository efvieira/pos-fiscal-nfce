package posfiscalnfce.cash;

import posfiscalnfce.shared.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashSessionTest {

    private final Instant now = Instant.parse("2026-03-15T12:00:00Z");

    private CashSession open() {
        return new CashSession(java.util.UUID.randomUUID(), "ana", new BigDecimal("100"), now);
    }

    @Test
    void expectedCashConsidersFloatSuppliesWithdrawalsAndSales() {
        var session = open();
        session.supply(new BigDecimal("50"), "reforço", now);
        session.registerSale(PaymentMethod.CASH, new BigDecimal("30"), "s1", now);
        session.registerSale(PaymentMethod.PIX, new BigDecimal("20"), "s2", now);
        session.withdraw(new BigDecimal("120"), "sangria", now);
        session.registerSaleCancellation(PaymentMethod.PIX, new BigDecimal("20"), "s2", now);

        var expected = session.expectedBalance();
        assertThat(expected.get(PaymentMethod.CASH)).isEqualByComparingTo("60");
        assertThat(expected.get(PaymentMethod.PIX)).isEqualByComparingTo("0");
    }

    @Test
    void closingComparesCountedWithExpected() {
        var session = open();
        session.registerSale(PaymentMethod.DEBIT_CARD, new BigDecimal("45.90"), "s1", now);

        var lines = session.close(Map.of(PaymentMethod.CASH, new BigDecimal("98"),
                PaymentMethod.DEBIT_CARD, new BigDecimal("45.90")), now);

        var cash = lines.stream().filter(l -> l.getMethod() == PaymentMethod.CASH).findFirst().orElseThrow();
        assertThat(cash.getDifference()).isEqualByComparingTo("-2");
        assertThat(session.getStatus()).isEqualTo(CashSession.Status.CLOSED);
        assertThatThrownBy(() -> session.supply(BigDecimal.ONE, "x", now)).isInstanceOf(BusinessException.class);
    }

    @Test
    void withdrawalCannotExceedCashInDrawer() {
        var session = open();
        assertThatThrownBy(() -> session.withdraw(new BigDecimal("100.01"), "x", now))
                .hasMessageContaining("Sangria");
        assertThatThrownBy(() -> session.supply(BigDecimal.ZERO, "x", now)).hasMessageContaining("positivo");
        assertThatThrownBy(() -> new CashSession(java.util.UUID.randomUUID(), "ana", new BigDecimal("-1"), now))
                .hasMessageContaining("negativo");
    }

    @Test
    void movementSignDependsOnType() {
        var out = new CashMovement(CashMovement.Type.WITHDRAWAL, PaymentMethod.CASH, BigDecimal.TEN, null, now);
        assertThat(out.signedAmount()).isEqualByComparingTo("-10");
    }
}
