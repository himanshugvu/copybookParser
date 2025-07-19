package org.example.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CopybookTokenizer {
    private static final Pattern COPYBOOK_LINE_PATTERN = Pattern.compile(
            "^\\s*(\\d{2})\\s+([A-Za-z0-9_-]+)(?:\\s+PIC\\s+([^\\s]+))?(?:\\s+(COMP|COMP-[1-5]|BINARY|PACKED-DECIMAL|DISPLAY))?(?:\\s+OCCURS\\s+(\\d+))?(?:\\s+REDEFINES\\s+([A-Za-z0-9_-]+))?(?:\\s+VALUE\\s+([^\\.]*))?\\s*\\.$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LEVEL_NAME_PATTERN = Pattern.compile(
            "^\\s*(\\d{2})\\s+([A-Za-z0-9_-]+).*$"
    );

    public static class Token {
        public int level;
        public String name;
        public String picture;
        public String usage;
        public int occurs;
        public String redefines;
        public String value;
        public String originalLine;

        public Token(String line) {
            this.originalLine = line.trim();
            parseLine(line);
        }

        private void parseLine(String line) {
            // Clean the line
            line = line.trim();
            if (line.endsWith(".")) {
                line = line.substring(0, line.length() - 1);
            }

            // Extract level and name first
            Matcher levelNameMatcher = LEVEL_NAME_PATTERN.matcher(line);
            if (levelNameMatcher.matches()) {
                this.level = Integer.parseInt(levelNameMatcher.group(1));
                this.name = levelNameMatcher.group(2);
            }

            // Parse remaining parts
            parsePicture(line);
            parseUsage(line);
            parseOccurs(line);
            parseRedefines(line);
            parseValue(line);
        }

        private void parsePicture(String line) {
            Pattern picPattern = Pattern.compile("PIC\\s+([^\\s\\.]+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = picPattern.matcher(line);
            if (matcher.find()) {
                this.picture = matcher.group(1);
            }
        }

        private void parseUsage(String line) {
            Pattern usagePattern = Pattern.compile("(COMP|COMP-[1-5]|BINARY|PACKED-DECIMAL|DISPLAY)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = usagePattern.matcher(line);
            if (matcher.find()) {
                this.usage = matcher.group(1);
            }
        }

        private void parseOccurs(String line) {
            Pattern occursPattern = Pattern.compile("OCCURS\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = occursPattern.matcher(line);
            if (matcher.find()) {
                this.occurs = Integer.parseInt(matcher.group(1));
            }
        }

        private void parseRedefines(String line) {
            Pattern redefinesPattern = Pattern.compile("REDEFINES\\s+([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = redefinesPattern.matcher(line);
            if (matcher.find()) {
                this.redefines = matcher.group(1);
            }
        }

        private void parseValue(String line) {
            Pattern valuePattern = Pattern.compile("VALUE\\s+([^\\.]*)\\s*$", Pattern.CASE_INSENSITIVE);
            Matcher matcher = valuePattern.matcher(line);
            if (matcher.find()) {
                this.value = matcher.group(1).trim();
                if (this.value.startsWith("'") && this.value.endsWith("'")) {
                    this.value = this.value.substring(1, this.value.length() - 1);
                }
            }
        }
    }

    public static List<Token> tokenize(List<String> lines) {
        List<Token> tokens = new ArrayList<>();

        for (String line : lines) {
            // Skip empty lines and comments
            if (line.trim().isEmpty() || line.trim().startsWith("*")) {
                continue;
            }

            // Check if line starts with a level number
            if (line.trim().matches("^\\d{2}\\s+.*")) {
                tokens.add(new Token(line));
            }
        }

        return tokens;
    }
}
