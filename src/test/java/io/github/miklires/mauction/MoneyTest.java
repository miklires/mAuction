package io.github.miklires.mauction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    @Test void roundsVaultAmountsPredictably() {
        assertEquals(9.50, Money.payout(10, 5, 2));
        assertEquals(0.67, Money.payout(1, 33.333, 2));
    }

    @Test void rejectsUnsafeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> Money.round(Double.NaN, 2));
        assertThrows(IllegalArgumentException.class, () -> Money.round(-1, 2));
    }
}
