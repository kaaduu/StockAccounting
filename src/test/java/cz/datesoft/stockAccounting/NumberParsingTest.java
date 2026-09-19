package cz.datesoft.stockAccounting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for unified number parsing across all importers and parsers.
 * 
 * This test suite ensures consistent handling of:
 * - Czech locale: "1 234,56" → 1234.56
 * - Parentheses for negatives: "(1 234.56)" → -1234.56
 * - Unicode minus: "−12,34" (U+2212) → -12.34
 * - NBSP variants: "1\u00A0234,56" → 1234.56
 * - Empty/null/invalid: "", "-", "abc" → 0.0
 */
class NumberParsingTest {

    @Test
    void parseCzechLocale() {
        // Czech uses space as thousands separator and comma as decimal
        assertEquals(1234.56, NumberParser.parseDouble("1 234,56"), 0.001);
        assertEquals(1234567.89, NumberParser.parseDouble("1 234 567,89"), 0.001);
        assertEquals(0.5, NumberParser.parseDouble("0,5"), 0.001);
    }

    @Test
    void parseParenthesesNegatives() {
        // Accounting notation: parentheses indicate negative
        assertEquals(-1234.56, NumberParser.parseDouble("(1 234,56)"), 0.001);
        assertEquals(-100.0, NumberParser.parseDouble("(100)"), 0.001);
        assertEquals(-0.5, NumberParser.parseDouble("(0,5)"), 0.001);
    }

    @Test
    void parseUnicodeMinus() {
        // Unicode minus sign U+2212 (not ASCII hyphen-minus U+002D)
        assertEquals(-12.34, NumberParser.parseDouble("−12,34"), 0.001);
        assertEquals(-1234.56, NumberParser.parseDouble("−1 234,56"), 0.001);
    }

    @Test
    void parseNBSPVariants() {
        // Non-breaking space U+00A0 and narrow no-break space U+202F
        assertEquals(1234.56, NumberParser.parseDouble("1\u00A0234,56"), 0.001);
        assertEquals(1234.56, NumberParser.parseDouble("1\u202F234,56"), 0.001);
    }

    @Test
    void parseStandardFormats() {
        // Standard formats that should work
        assertEquals(1234.56, NumberParser.parseDouble("1234.56"), 0.001);
        assertEquals(1234.56, NumberParser.parseDouble("1,234.56"), 0.001);
        assertEquals(-1234.56, NumberParser.parseDouble("-1234.56"), 0.001);
        assertEquals(-1234.56, NumberParser.parseDouble("-1,234.56"), 0.001);
    }

    @Test
    void parseEmptyAndInvalid() {
        // Empty, null, and invalid should return 0.0
        assertEquals(0.0, NumberParser.parseDouble(""), 0.001);
        assertEquals(0.0, NumberParser.parseDouble(null), 0.001);
        assertEquals(0.0, NumberParser.parseDouble("   "), 0.001);
        assertEquals(0.0, NumberParser.parseDouble("-"), 0.001);
        assertEquals(0.0, NumberParser.parseDouble("abc"), 0.001);
    }

    @Test
    void parseEdgeCases() {
        // Edge cases
        assertEquals(0.0, NumberParser.parseDouble("."), 0.001);
        assertEquals(0.0, NumberParser.parseDouble(","), 0.001);
        assertEquals(1.0, NumberParser.parseDouble("1"), 0.001);
        assertEquals(-1.0, NumberParser.parseDouble("-1"), 0.001);
        assertEquals(0.01, NumberParser.parseDouble(",01"), 0.001);
    }

    @Test
    void parseWithWhitespace() {
        // Leading/trailing whitespace should be trimmed
        assertEquals(1234.56, NumberParser.parseDouble("  1 234,56  "), 0.001);
        assertEquals(-1234.56, NumberParser.parseDouble("  (1 234,56)  "), 0.001);
    }
}
