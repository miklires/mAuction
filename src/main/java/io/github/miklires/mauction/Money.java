package io.github.miklires.mauction;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Money {
    private Money() {}

    static double round(double value, int fractionDigits) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid money amount");
        return BigDecimal.valueOf(value).setScale(Math.clamp(fractionDigits, 0, 8), RoundingMode.HALF_UP).doubleValue();
    }

    static double payout(double gross, double taxPercent, int fractionDigits) {
        double tax = Math.clamp(taxPercent, 0, 100);
        return round(gross * (1 - tax / 100), fractionDigits);
    }
}
