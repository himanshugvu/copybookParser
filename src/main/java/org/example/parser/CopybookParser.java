package org.example.parser;

import org.example.parser.util.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class CopybookParser {

    public static class ParseResult {
        private List<CobolField> fields;
        private List<RecordLayout> recordLayouts;
        private String fileName;
        private int totalLength;

        public ParseResult() {
            this.fields = new ArrayList<>();
            this.recordLayouts = new ArrayList<>();
        }

        public List<CobolField> getFields() { return fields; }
        public void setFields(List<CobolField> fields) { this.fields = fields; }

        public List<RecordLayout> getRecordLayouts() { return recordLayouts; }
        public void setRecordLayouts(List<RecordLayout> recordLayouts) { this.recordLayouts = recordLayouts; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public int getTotalLength() { return totalLength; }
        public void setTotalLength(int totalLength) { this.totalLength = totalLength; }
    }

    public static class RecordLayout {
        private String name;
        private String redefines;
        private List<CobolField> fields;
        private int startPosition;
        private int length;

        public RecordLayout(String name) {
            this.name = name;
            this.fields = new ArrayList<>();
        }

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRedefines() { return redefines; }
        public void setRedefines(String redefines) { this.redefines = redefines; }

        public List<CobolField> getFields() { return fields; }
        public void setFields(List<CobolField> fields) { this.fields = fields; }

        public int getStartPosition() { return startPosition; }
        public void setStartPosition(int startPosition) { this.startPosition = startPosition; }

        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
    }

    public ParseResult parseCopybook(Path copybookPath) throws IOException {
        List<String> lines = Files.readAllLines(copybookPath);
        List<CopybookTokenizer.Token> tokens = CopybookTokenizer.tokenize(lines);

        ParseResult result = new ParseResult();
        result.setFileName(copybookPath.getFileName().toString());

        Stack<CobolField> fieldStack = new Stack<>();
        int basePosition = 1;
        int currentPosition = basePosition;
        String currentRedefinesBase = null;

        for (CopybookTokenizer.Token token : tokens) {
            CobolField field = createFieldFromToken(token);

            // Handle REDEFINES at 01 level (different record layouts)
            if (field.getLevel() == 1 && field.getRedefines() != null) {
                currentRedefinesBase = field.getRedefines();
                currentPosition = basePosition;

                RecordLayout layout = new RecordLayout(field.getName());
                layout.setRedefines(field.getRedefines());
                layout.setStartPosition(basePosition);

                processRecordLayout(tokens, tokens.indexOf(token), layout, result);
                continue;
            }

            // Pop fields from stack until we find the parent level
            while (!fieldStack.isEmpty() && fieldStack.peek().getLevel() >= field.getLevel()) {
                CobolField completedField = fieldStack.pop();
                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addChild(completedField);
                } else {
                    if (field.getLevel() == 1) {
                        RecordLayout mainLayout = new RecordLayout(completedField.getName());
                        mainLayout.setStartPosition(basePosition);
                        mainLayout.getFields().add(completedField);
                        result.getRecordLayouts().add(mainLayout);
                    }
                    result.getFields().add(completedField);
                }
            }

            // Set position for fields
            if (field.getPicture() != null) {
                // Elementary field with picture clause
                field.setStartPosition(currentPosition);

                int fieldLength = calculateActualFieldLength(field);
                field.setLength(fieldLength);
                field.setEndPosition(currentPosition + fieldLength - 1);
                currentPosition += fieldLength;

            } else {
                // Group field - set start position, end position will be calculated later
                field.setStartPosition(currentPosition);
            }

            fieldStack.push(field);
        }

        // Process remaining fields in stack
        while (!fieldStack.isEmpty()) {
            CobolField completedField = fieldStack.pop();
            if (!fieldStack.isEmpty()) {
                fieldStack.peek().addChild(completedField);
            } else {
                result.getFields().add(completedField);
            }
        }

        // Update group field positions and handle OCCURS
        updateGroupFieldPositions(result.getFields());
        updateRecordLayoutLengths(result.getRecordLayouts());

        result.setTotalLength(calculateMaxRecordLength(result.getRecordLayouts()));

        return result;
    }

    private int calculateActualFieldLength(CobolField field) {
        int baseLength = field.getLength(); // Length from picture clause

        // Adjust for usage clause
        if ("COMP-3".equals(field.getUsage()) || "PACKED-DECIMAL".equals(field.getUsage())) {
            // COMP-3: (digits + 1) / 2 rounded up
            String pic = field.getPicture();
            int totalDigits = extractTotalDigits(pic);
            baseLength = (totalDigits + 1) / 2;
        } else if ("COMP".equals(field.getUsage()) || "BINARY".equals(field.getUsage())) {
            // COMP/BINARY: depends on number of digits
            String pic = field.getPicture();
            int totalDigits = extractTotalDigits(pic);
            if (totalDigits <= 4) {
                baseLength = 2; // Halfword
            } else if (totalDigits <= 9) {
                baseLength = 4; // Fullword
            } else {
                baseLength = 8; // Doubleword
            }
        }

        return baseLength;
    }

    private int extractTotalDigits(String picture) {
        if (picture == null) return 0;

        int totalDigits = 0;
        String pic = picture.toUpperCase().replaceAll("\\s+", "");

        // Handle S (sign)
        pic = pic.replace("S", "");

        // Handle V (decimal point)
        pic = pic.replace("V", "");

        // Count 9s with parentheses
        while (pic.contains("9(")) {
            int start = pic.indexOf("9(");
            int end = pic.indexOf(")", start);
            if (end != -1) {
                try {
                    int count = Integer.parseInt(pic.substring(start + 2, end));
                    totalDigits += count;
                    pic = pic.substring(0, start) + pic.substring(end + 1);
                } catch (NumberFormatException e) {
                    break;
                }
            } else {
                break;
            }
        }

        // Count remaining 9s
        for (char c : pic.toCharArray()) {
            if (c == '9') totalDigits++;
        }

        return totalDigits;
    }

    private void updateGroupFieldPositions(List<CobolField> fields) {
        for (CobolField field : fields) {
            if (!field.getChildren().isEmpty()) {
                // Process children first
                updateGroupFieldPositions(field.getChildren());

                // Calculate group field positions and handle OCCURS
                int minStart = Integer.MAX_VALUE;
                int maxEnd = 0;
                int childrenTotalLength = 0;

                for (CobolField child : field.getChildren()) {
                    if (child.getStartPosition() > 0) {
                        minStart = Math.min(minStart, child.getStartPosition());
                        maxEnd = Math.max(maxEnd, child.getEndPosition());
                        childrenTotalLength += child.getLength();
                    }
                }

                if (minStart != Integer.MAX_VALUE) {
                    field.setStartPosition(minStart);

                    // Handle OCCURS clause
                    if (field.getOccursCount() > 0) {
                        // This field occurs multiple times
                        int singleOccurrenceLength = maxEnd - minStart + 1;
                        int totalLength = singleOccurrenceLength * field.getOccursCount();

                        field.setLength(totalLength);
                        field.setEndPosition(minStart + totalLength - 1);
                    } else {
                        field.setEndPosition(maxEnd);
                        field.setLength(maxEnd - minStart + 1);
                    }
                }
            }
        }
    }

    private void processRecordLayout(List<CopybookTokenizer.Token> allTokens, int startIndex,
                                     RecordLayout layout, ParseResult result) {
        Stack<CobolField> fieldStack = new Stack<>();
        int currentPosition = layout.getStartPosition();
        boolean inCurrentLayout = false;

        for (int i = startIndex; i < allTokens.size(); i++) {
            CopybookTokenizer.Token token = allTokens.get(i);
            CobolField field = createFieldFromToken(token);

            if (field.getLevel() == 1 && i > startIndex) {
                break;
            }

            if (field.getLevel() == 1) {
                inCurrentLayout = true;
                fieldStack.push(field);
                continue;
            }

            if (!inCurrentLayout) continue;

            while (!fieldStack.isEmpty() && fieldStack.peek().getLevel() >= field.getLevel()) {
                CobolField completedField = fieldStack.pop();
                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addChild(completedField);
                }
            }

            if (field.getPicture() != null) {
                field.setStartPosition(currentPosition);

                int fieldLength = calculateActualFieldLength(field);
                field.setLength(fieldLength);
                field.setEndPosition(currentPosition + fieldLength - 1);
                currentPosition += fieldLength;
            } else {
                field.setStartPosition(currentPosition);
            }

            fieldStack.push(field);
        }

        while (!fieldStack.isEmpty()) {
            CobolField completedField = fieldStack.pop();
            if (!fieldStack.isEmpty()) {
                fieldStack.peek().addChild(completedField);
            } else {
                layout.getFields().add(completedField);
            }
        }

        updateGroupFieldPositions(layout.getFields());
        result.getRecordLayouts().add(layout);
    }

    private CobolField createFieldFromToken(CopybookTokenizer.Token token) {
        CobolField field = new CobolField(token.level, token.name);

        if (token.picture != null) {
            field.setPicture(token.picture);
        }

        if (token.usage != null) {
            field.setUsage(token.usage);
        } else {
            field.setUsage("DISPLAY");
        }

        if (token.occurs > 0) {
            field.setOccursCount(token.occurs);
        }

        if (token.redefines != null) {
            field.setRedefines(token.redefines);
        }

        if (token.value != null) {
            field.setValue(token.value);
        }

        return field;
    }

    private void updateRecordLayoutLengths(List<RecordLayout> layouts) {
        for (RecordLayout layout : layouts) {
            updateGroupFieldPositions(layout.getFields());

            int maxEnd = 0;
            for (CobolField field : layout.getFields()) {
                maxEnd = Math.max(maxEnd, field.getEndPosition());
            }
            layout.setLength(maxEnd - layout.getStartPosition() + 1);
        }
    }

    private int calculateMaxRecordLength(List<RecordLayout> layouts) {
        int maxLength = 0;
        for (RecordLayout layout : layouts) {
            maxLength = Math.max(maxLength, layout.getLength());
        }
        return maxLength;
    }

    public String toJson(ParseResult parseResult) throws IOException {
        return JsonUtils.toJson(parseResult);
    }

    public String toPrettyJson(ParseResult parseResult) throws IOException {
        return JsonUtils.toPrettyJson(parseResult);
    }

    public void saveJsonToFile(ParseResult parseResult, Path outputPath) throws IOException {
        String json = toPrettyJson(parseResult);
        Files.writeString(outputPath, json);
    }
}
