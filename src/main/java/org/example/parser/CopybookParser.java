package org.example.parser;

import org.example.parser.util.JsonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        private int endPosition;
        private int length;
        private List<String> recordTypeValues;

        public RecordLayout(String name) {
            this.name = name;
            this.fields = new ArrayList<>();
            this.recordTypeValues = new ArrayList<>();
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
        public int getEndPosition() { return endPosition; }
        public void setEndPosition(int endPosition) { this.endPosition = endPosition; }
        public int getLength() { return length; }
        public void setLength(int length) {
            this.length = length;
            this.endPosition = this.startPosition + length - 1;
        }
        public List<String> getRecordTypeValues() { return recordTypeValues; }
        public void setRecordTypeValues(List<String> recordTypeValues) { this.recordTypeValues = recordTypeValues; }
    }

    private static class PositionTracker {
        private int currentPosition = 1;

        public int getCurrentPosition() { return currentPosition; }
        public void setPosition(int position) { this.currentPosition = position; }
        public void advancePosition(int length) { this.currentPosition += length; }
        public void reset() { this.currentPosition = 1; }
    }

    public ParseResult parseCopybook(Path copybookPath) throws IOException {
        List<String> lines = Files.readAllLines(copybookPath);
        List<CopybookTokenizer.Token> tokens = CopybookTokenizer.tokenize(lines);

        ParseResult result = new ParseResult();
        result.setFileName(copybookPath.getFileName().toString());

        // Extract record length from comments
        int recordLength = extractRecordLengthFromComments(lines);
        result.setTotalLength(recordLength);

        // Create reference field (just for display)
        CobolField referenceField = createReferenceField(tokens, recordLength);
        if (referenceField != null) {
            result.getFields().add(referenceField);
        }

        // Process actual record layouts
        processRecordLayouts(tokens, result, recordLength);

        return result;
    }

    private int extractRecordLengthFromComments(List<String> lines) {
        Pattern pattern = Pattern.compile("\\*\\s*REC\\s+LEN\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return 300; // Default
    }

    private CobolField createReferenceField(List<CopybookTokenizer.Token> tokens, int recordLength) {
        // Find the main reference area (01 level without REDEFINES)
        for (CopybookTokenizer.Token token : tokens) {
            if (token.level == 1 && token.redefines == null && !token.isConditionName) {
                CobolField field = new CobolField(token.level, token.name);
                field.setStartPosition(1);
                field.setLength(recordLength);
                field.setEndPosition(recordLength);
                field.setDataType("GROUP");
                field.setUsage("Text/ASCII format (1 byte per character)");

                // Add the shared record type field if it exists
                addSharedFields(tokens, field);

                return field;
            }
        }
        return null;
    }

    private void addSharedFields(List<CopybookTokenizer.Token> tokens, CobolField parentField) {
        Stack<CobolField> fieldStack = new Stack<>();
        PositionTracker positionTracker = new PositionTracker();
        fieldStack.push(parentField);

        for (CopybookTokenizer.Token token : tokens) {
            // Stop when we hit REDEFINES or another 01 level
            if ((token.level == 1 && token.redefines != null) ||
                    (token.level <= 5 && token.redefines != null)) {
                break;
            }

            if (token.level == 1 || token.isConditionName) {
                if (token.isConditionName && !fieldStack.isEmpty()) {
                    fieldStack.peek().addConditionName(token.name, token.value);
                }
                continue;
            }

            CobolField field = createFieldFromToken(token);

            // Pop completed fields
            while (fieldStack.size() > 1 && fieldStack.peek().getLevel() >= field.getLevel()) {
                CobolField completedField = fieldStack.pop();
                processCompletedField(completedField, positionTracker);
                fieldStack.peek().addChild(completedField);
            }

            field.setStartPosition(positionTracker.getCurrentPosition());

            if (field.getPicture() != null) {
                int fieldLength = calculateActualFieldLength(field);
                field.setLength(fieldLength);
                field.setEndPosition(positionTracker.getCurrentPosition() + fieldLength - 1);
                positionTracker.advancePosition(fieldLength);
            }

            fieldStack.push(field);
        }

        // Process remaining fields
        while (fieldStack.size() > 1) {
            CobolField completedField = fieldStack.pop();
            processCompletedField(completedField, positionTracker);
            fieldStack.peek().addChild(completedField);
        }
    }

    private void processRecordLayouts(List<CopybookTokenizer.Token> tokens, ParseResult result, int recordLength) {
        // Find record structures that have REDEFINES
        for (int i = 0; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            // Look for 05-level structures (HEADER-RECORD, DETAIL-RECORD, etc.)
            if (token.level == 5 && !token.isConditionName) {
                // Check if this is a REDEFINES or a base structure
                if (token.redefines != null) {
                    // This is a REDEFINES structure - create a record layout
                    RecordLayout layout = createRecordLayout(tokens, i, recordLength);
                    if (layout != null) {
                        result.getRecordLayouts().add(layout);
                    }
                } else {
                    // This might be a base structure - also create a layout
                    RecordLayout layout = createRecordLayout(tokens, i, recordLength);
                    if (layout != null) {
                        result.getRecordLayouts().add(layout);
                    }
                }
            }
        }
    }

    private RecordLayout createRecordLayout(List<CopybookTokenizer.Token> tokens, int startIndex, int recordLength) {
        CopybookTokenizer.Token firstToken = tokens.get(startIndex);

        // Create layout name based on the structure
        String layoutName = determineLayoutName(firstToken.name);
        RecordLayout layout = new RecordLayout(layoutName);

        if (firstToken.redefines != null) {
            layout.setRedefines(firstToken.redefines);
        }

        layout.setStartPosition(1);
        layout.setLength(recordLength);

        // Determine record type values
        determineRecordTypeValues(firstToken.name, layout);

        Stack<CobolField> fieldStack = new Stack<>();
        PositionTracker positionTracker = new PositionTracker();

        // Process fields within this structure
        for (int i = startIndex + 1; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            // Stop when we hit another 05-level structure
            if (token.level == 5 && !token.isConditionName) {
                break;
            }

            if (token.isConditionName) {
                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addConditionName(token.name, token.value);
                }
                continue;
            }

            // Handle nested REDEFINES
            if (token.redefines != null) {
                positionTracker.reset();
            }

            CobolField field = createFieldFromToken(token);

            // Pop completed fields
            while (!fieldStack.isEmpty() && fieldStack.peek().getLevel() >= field.getLevel()) {
                CobolField completedField = fieldStack.pop();
                processCompletedField(completedField, positionTracker);

                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addChild(completedField);
                } else {
                    layout.getFields().add(completedField);
                }
            }

            field.setStartPosition(positionTracker.getCurrentPosition());

            if (field.getPicture() != null) {
                int fieldLength = calculateActualFieldLength(field);
                field.setLength(fieldLength);
                field.setEndPosition(positionTracker.getCurrentPosition() + fieldLength - 1);
                positionTracker.advancePosition(fieldLength);
            }

            fieldStack.push(field);
        }

        // Process remaining fields
        while (!fieldStack.isEmpty()) {
            CobolField completedField = fieldStack.pop();
            processCompletedField(completedField, positionTracker);

            if (!fieldStack.isEmpty()) {
                fieldStack.peek().addChild(completedField);
            } else {
                layout.getFields().add(completedField);
            }
        }

        // Handle OCCURS fields
        createArrayElementsAndCleanup(layout.getFields());

        return layout;
    }

    private String determineLayoutName(String structureName) {
        if (structureName.contains("HEADER")) {
            return "HEADER-RECORD";
        } else if (structureName.contains("DETAIL")) {
            return "DETAIL-RECORD";
        } else if (structureName.contains("TRAILER") || structureName.contains("TRAIL")) {
            return "TRAILER-RECORD";
        }
        return structureName;
    }

    private void determineRecordTypeValues(String structureName, RecordLayout layout) {
        if (structureName.contains("HEADER")) {
            layout.getRecordTypeValues().add("00");
        } else if (structureName.contains("DETAIL")) {
            layout.getRecordTypeValues().add("01");
        } else if (structureName.contains("TRAILER") || structureName.contains("TRAIL")) {
            layout.getRecordTypeValues().add("99");
        }
    }

    // ... (Keep all other helper methods: processCompletedField, calculateActualFieldLength, etc.)

    private void processCompletedField(CobolField field, PositionTracker positionTracker) {
        if (field.getPicture() == null && !field.getChildren().isEmpty()) {
            int groupLength = calculateGroupFieldLength(field);

            if (field.getOccursCount() > 0) {
                int totalLength = groupLength * field.getOccursCount();
                field.setLength(totalLength);
                field.setEndPosition(field.getStartPosition() + totalLength - 1);
                positionTracker.setPosition(field.getEndPosition() + 1);
            } else {
                field.setLength(groupLength);
                field.setEndPosition(field.getStartPosition() + groupLength - 1);
            }

            field.setDataType("GROUP");
        }
    }

    private int calculateGroupFieldLength(CobolField groupField) {
        int totalLength = 0;

        for (CobolField child : groupField.getChildren()) {
            if (child.getPicture() != null) {
                totalLength += calculateActualFieldLength(child);
            } else {
                totalLength += calculateGroupFieldLength(child);
            }
        }

        return totalLength;
    }

    private void createArrayElementsAndCleanup(List<CobolField> fields) {
        for (CobolField field : fields) {
            if (field.getOccursCount() > 0) {
                int singleOccurrenceLength = calculateGroupFieldLength(field);
                int currentPos = field.getStartPosition();

                for (int i = 1; i <= field.getOccursCount(); i++) {
                    CobolField.ArrayElement arrayElement = new CobolField.ArrayElement(i, currentPos, singleOccurrenceLength);
                    addFieldPositionsToArrayElement(field.getChildren(), arrayElement, currentPos);
                    field.getArrayElements().add(arrayElement);
                    currentPos += singleOccurrenceLength;
                }

                field.getChildren().clear();
            } else {
                createArrayElementsAndCleanup(field.getChildren());
            }
        }
    }

    private void addFieldPositionsToArrayElement(List<CobolField> children, CobolField.ArrayElement arrayElement, int basePosition) {
        int currentPos = basePosition;

        for (CobolField child : children) {
            if (child.getPicture() != null) {
                int fieldLength = calculateActualFieldLength(child);
                CobolField.FieldPosition fieldPosition = new CobolField.FieldPosition(
                        child.getName(),
                        currentPos,
                        fieldLength,
                        child.getPicture(),
                        child.getDataType(),
                        child.getUsage()
                );
                arrayElement.getFields().add(fieldPosition);
                currentPos += fieldLength;
            } else {
                addFieldPositionsToArrayElement(child.getChildren(), arrayElement, currentPos);
                currentPos += calculateGroupFieldLength(child);
            }
        }
    }

    private int calculateActualFieldLength(CobolField field) {
        if (field.getLength() > 0 && field.getPicture() != null) {
            int baseLength = field.getLength();

            if ("COMP-3".equals(field.getUsage()) || "PACKED-DECIMAL".equals(field.getUsage())) {
                String pic = field.getPicture();
                int totalDigits = extractTotalDigits(pic);
                return (totalDigits + 1) / 2;
            } else if ("COMP".equals(field.getUsage()) || "BINARY".equals(field.getUsage())) {
                String pic = field.getPicture();
                int totalDigits = extractTotalDigits(pic);
                if (totalDigits <= 4) return 2;
                else if (totalDigits <= 9) return 4;
                else return 8;
            } else if ("COMP-1".equals(field.getUsage())) {
                return 4;
            } else if ("COMP-2".equals(field.getUsage())) {
                return 8;
            }

            return baseLength;
        }
        return 1;
    }

    private int extractTotalDigits(String picture) {
        if (picture == null) return 0;

        int totalDigits = 0;
        String pic = picture.toUpperCase().replaceAll("\\s+", "");
        pic = pic.replace("S", "").replace("V", "");

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

        for (char c : pic.toCharArray()) {
            if (c == '9') totalDigits++;
        }

        return totalDigits;
    }

    private String getMeaningfulUsage(String usage) {
        if (usage == null) usage = "DISPLAY";

        return switch (usage.toUpperCase()) {
            case "DISPLAY" -> "Text/ASCII format (1 byte per character)";
            case "COMP" -> "Binary format (2, 4, or 8 bytes)";
            case "COMP-1" -> "Single precision floating point (4 bytes)";
            case "COMP-2" -> "Double precision floating point (8 bytes)";
            case "COMP-3" -> "Packed decimal format (space efficient)";
            case "COMP-4" -> "Binary format (same as COMP)";
            case "COMP-5" -> "Native binary format";
            case "BINARY" -> "Binary format (2, 4, or 8 bytes)";
            case "PACKED-DECIMAL" -> "Packed decimal format (space efficient)";
            case "INDEX" -> "Index data item (4 bytes)";
            case "POINTER" -> "Pointer data item (4 bytes)";
            default -> usage + " (custom format)";
        };
    }

    private CobolField createFieldFromToken(CopybookTokenizer.Token token) {
        CobolField field = new CobolField(token.level, token.name);

        if (token.picture != null) {
            field.setPicture(token.picture);
        } else {
            field.setDataType("GROUP");
        }

        String usage = token.usage != null ? token.usage : "DISPLAY";
        field.setUsage(getMeaningfulUsage(usage));

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
