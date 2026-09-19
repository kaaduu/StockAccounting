package cz.datesoft.stockAccounting;

/**
 * Unified number parser for consistent handling of numeric values across all importers.
 * 
 * Handles:
 * - Czech locale: "1 234,56" → 1234.56
 * - Parentheses for negatives: "(1 234.56)" → -1234.56
 * - Unicode minus: "−12,34" (U+2212) → -12.34
 * - NBSP variants: "1\u00A0234,56" → 1234.56
 * - Standard formats: "1,234.56", "-1234.56"
 * - Empty/null/invalid → 0.0
 */
public class NumberParser {
    
    /**
     * Parse a string to double with robust handling of various formats.
     * 
     * @param value The string to parse
     * @return The parsed double value, or 0.0 if parsing fails or input is empty/null
     */
    public static double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().equals("-")) {
            return 0.0;
        }
        
        try {
            // Trim whitespace
            String s = value.trim();
            
            // Check for parentheses notation (negative)
            boolean negative = false;
            if (s.startsWith("(") && s.endsWith(")")) {
                negative = true;
                s = s.substring(1, s.length() - 1).trim();
            }
            
            // Replace Unicode minus variants with standard minus
            // U+2212 (minus sign), U+2013 (en-dash), U+2014 (em-dash)
            s = s.replace('\u2212', '-').replace('\u2013', '-').replace('\u2014', '-');
            
            // Remove all whitespace variants (space, NBSP U+00A0, narrow NBSP U+202F)
            s = s.replaceAll("[\\s\u00A0\u202F]", "");
            
            // Handle explicit minus/plus prefix
            if (s.startsWith("-")) {
                negative = !negative; // Toggle if already negative from parentheses
                s = s.substring(1);
            } else if (s.startsWith("+")) {
                s = s.substring(1);
            }
            
            // Determine decimal separator
            // If both comma and dot exist, assume comma is thousands separator
            // If only comma exists, assume it's decimal separator (Czech locale)
            // If only dot exists, assume it's decimal separator (English locale)
            int commaPos = s.lastIndexOf(',');
            int dotPos = s.lastIndexOf('.');
            
            if (commaPos >= 0 && dotPos >= 0) {
                // Both exist - determine which is decimal separator
                if (commaPos > dotPos) {
                    // Comma is decimal separator (European): 1.234,56
                    s = s.replace(".", "");  // Remove thousands separator
                    s = s.replace(',', '.'); // Convert decimal separator
                } else {
                    // Dot is decimal separator (English): 1,234.56
                    s = s.replace(",", "");  // Remove thousands separator
                }
            } else if (commaPos >= 0) {
                // Only comma - assume decimal separator (Czech locale)
                s = s.replace(',', '.');
            }
            // If only dot or neither, no transformation needed
            
            double result = Double.parseDouble(s);
            return negative ? -result : result;
            
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
