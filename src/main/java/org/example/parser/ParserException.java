package org.example.parser;


public class ParserException extends Exception {
    private final int lineNumber;
    private final String line;

    public ParserException(String message) {
        super(message);
        this.lineNumber = -1;
        this.line = null;
    }

    public ParserException(String message, int lineNumber, String line) {
        super(String.format("Line %d: %s - '%s'", lineNumber, message, line));
        this.lineNumber = lineNumber;
        this.line = line;
    }

    public ParserException(String message, Throwable cause) {
        super(message, cause);
        this.lineNumber = -1;
        this.line = null;
    }

    public int getLineNumber() { return lineNumber; }
    public String getLine() { return line; }
}
