package org.example.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CopybookTokenizer {
    private static final Pattern LEVEL_NAME_PATTERN = Pattern.compile(
            "^\\s*(\\d{2})\\s+([A-Za-z0-9_-]+).*$"
    );

    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "VALUE\\s+['\"]?([^'\"\\s\\.]+)['\"]?", Pattern.CASE_INSENSITIVE
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
        public boolean isConditionName;

        public Token(String line) {
            this.originalLine = line.trim();
            parseLine(line);
        }

        private void parseLine(String line) {
            line = line.trim();
            if (line.endsWith(".")) {
                line = line.substring(0, line.length() - 1);
            }

            Matcher levelNameMatcher = LEVEL_NAME_PATTERN.matcher(line);
            if (levelNameMatcher.matches()) {
                try {
                    this.level = Integer.parseInt(levelNameMatcher.group(1));
                    this.name = levelNameMatcher.group(2);
                    this.isConditionName = (this.level == 88);
                } catch (NumberFormatException e) {
                    this.level = 1;
                    this.name = "UNKNOWN";
                }
            }

            // Parse all fields including 88-level items
            if (!isConditionName) {
                parsePicture(line);
                parseUsage(line);
                parseOccurs(line);
                parseRedefines(line);
            }

            // Always parse VALUE (especially important for 88-level)
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
            Pattern usagePattern = Pattern.compile("(COMP|COMP-[1-5]|BINARY|PACKED-DECIMAL|DISPLAY)(?!\\-)", Pattern.CASE_INSENSITIVE);
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
            Matcher matcher = VALUE_PATTERN.matcher(line);
            if (matcher.find()) {
                this.value = matcher.group(1);
            }
        }
    }

    public static List<Token> tokenize(List<String> lines) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder continuationLine = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("*")) {
                continue;
            }

            if (!trimmed.matches("^\\d{2}\\s+.*")) {
                if (continuationLine.length() > 0) {
                    continuationLine.append(" ").append(trimmed);
                }
                continue;
            }

            if (continuationLine.length() > 0) {
                Token token = new Token(continuationLine.toString());
                tokens.add(token); // Include ALL tokens (including 88-level)
                continuationLine = new StringBuilder();
            }

            if (trimmed.matches("^\\d{2}\\s+.*")) {
                if (trimmed.endsWith(".")) {
                    Token token = new Token(trimmed);
                    tokens.add(token); // Include ALL tokens
                } else {
                    continuationLine.append(trimmed);
                }
            }
        }

        if (continuationLine.length() > 0) {
            Token token = new Token(continuationLine.toString());
            tokens.add(token); // Include ALL tokens
        }

        return tokens;
    }
}
