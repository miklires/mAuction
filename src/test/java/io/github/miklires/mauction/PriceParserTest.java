package io.github.miklires.mauction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PriceParserTest{@Test void parsesCompactPrices(){assertEquals(10_000,PriceParser.parse("10k"));assertEquals(2_500_000,PriceParser.parse("2.5m"));assertEquals(1_000_000_000,PriceParser.parse("1b"));}@Test void rejectsNonNumbers(){assertThrows(NumberFormatException.class,()->PriceParser.parse("many"));assertThrows(NumberFormatException.class,()->PriceParser.parse("NaN"));}}
