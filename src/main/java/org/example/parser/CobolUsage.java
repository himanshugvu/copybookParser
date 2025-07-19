package org.example.parser;


public enum CobolUsage {
    DISPLAY("DISPLAY", 1),
    COMPUTATIONAL("COMP", 0.5),
    COMPUTATIONAL_1("COMP-1", 4),
    COMPUTATIONAL_2("COMP-2", 8),
    COMPUTATIONAL_3("COMP-3", 0.5),
    COMPUTATIONAL_4("COMP-4", 0.5),
    COMPUTATIONAL_5("COMP-5", 0.5),
    BINARY("BINARY", 0.5),
    PACKED_DECIMAL("PACKED-DECIMAL", 0.5),
    INDEX("INDEX", 4),
    POINTER("POINTER", 4);

    private final String name;
    private final double bytesPerDigit;

    CobolUsage(String name, double bytesPerDigit) {
        this.name = name;
        this.bytesPerDigit = bytesPerDigit;
    }

    public String getName() { return name; }
    public double getBytesPerDigit() { return bytesPerDigit; }

    public static CobolUsage fromString(String usage) {
        if (usage == null) return DISPLAY;

        String upperUsage = usage.toUpperCase().trim();
        for (CobolUsage u : values()) {
            if (u.name.equals(upperUsage)) {
                return u;
            }
        }
        return DISPLAY; // Default
    }
}

