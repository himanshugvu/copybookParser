package org.example.parser;


public enum CobolDataType {
    ALPHANUMERIC("X", "Alphanumeric"),
    NUMERIC("9", "Numeric"),
    SIGNED_NUMERIC("S9", "Signed Numeric"),
    DECIMAL("V", "Decimal Point"),
    ALPHABETIC("A", "Alphabetic"),
    BLANK_WHEN_ZERO("Z", "Blank When Zero"),
    ASTERISK_FILL("*", "Asterisk Fill"),
    CURRENCY_SYMBOL("$", "Currency Symbol"),
    COMMA(",", "Comma"),
    DECIMAL_POINT(".", "Decimal Point"),
    PLUS_SIGN("+", "Plus Sign"),
    MINUS_SIGN("-", "Minus Sign"),
    CREDIT_SYMBOL("CR", "Credit Symbol"),
    DEBIT_SYMBOL("DB", "Debit Symbol"),
    SLASH("/", "Slash"),
    ZERO_SUPPRESSION("Z", "Zero Suppression");

    private final String symbol;
    private final String description;

    CobolDataType(String symbol, String description) {
        this.symbol = symbol;
        this.description = description;
    }

    public String getSymbol() { return symbol; }
    public String getDescription() { return description; }

    public static CobolDataType fromSymbol(String symbol) {
        for (CobolDataType type : values()) {
            if (type.symbol.equals(symbol)) {
                return type;
            }
        }
        return ALPHANUMERIC; // Default
    }
}
