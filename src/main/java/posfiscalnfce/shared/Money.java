package posfiscalnfce.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.function.Function;

public final class Money {

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static <T> BigDecimal sum(Collection<T> items, Function<T, BigDecimal> value) {
        return of(items.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
