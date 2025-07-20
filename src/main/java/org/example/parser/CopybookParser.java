package org.example.parser;

import org.example.parser.util.JsonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        private int endPosition;
        private int length;

        public RecordLayout(String name) {
            this.name = name;
            this.fields = new ArrayList<>();
        }

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
            if (this.startPosition > 0) {
                this.endPosition = this.startPosition + length - 1;
            }
        }
    }

    private static class PositionTracker {
        private int currentPosition = 1;

        public int getCurrentPosition() { return currentPosition; }
        public void setPosition(int position) { this.currentPosition = position; }
        public void advancePosition(int length) { this.currentPosition += length; }
    }

    public ParseResult parseCopybook(Path copybookPath) throws IOException {
        List<String> lines = Files.readAllLines(copybookPath);
        List<CopybookTokenizer.Token> tokens = CopybookTokenizer.tokenize(lines);

        ParseResult result = new ParseResult();
        result.setFileName(copybookPath.getFileName().toString());

        // Find and process base record first
        CobolField baseRecord = null;

        int i = 0;
        while (i < tokens.size()) {
            CopybookTokenizer.Token token = tokens.get(i);

            // Process base record (first 01 level without REDEFINES)
            if (token.level == 1 && token.redefines == null && baseRecord == null) {
                baseRecord = createFieldFromToken(token);

                // Properly set positions for base record
                baseRecord.setStartPosition(1);
                int recordLength = baseRecord.getLength();
                if (recordLength > 0) {
                    baseRecord.setEndPosition(recordLength);
                } else {
                    // If no length specified, default to reasonable size
                    recordLength = 250; // or extract from PIC clause
                    baseRecord.setLength(recordLength);
                    baseRecord.setEndPosition(recordLength);
                }

                result.getFields().add(baseRecord);
                result.setTotalLength(recordLength);
                i++;
                continue;
            }

            // Process REDEFINES layouts
            if (token.level == 1 && token.redefines != null) {
                List<CopybookTokenizer.Token> layoutTokens = extractLayoutTokens(tokens, i);
                processRedefinesLayout(layoutTokens, result);
                i += layoutTokens.size();
                continue;
            }

            i++;
        }

        return result;
    }

    // Enhanced createFieldFromToken to handle length calculation better
    private CobolField createFieldFromToken(CopybookTokenizer.Token token) {
        CobolField field = new CobolField(token.level, token.name);

        if (token.picture != null) {
            field.setPicture(token.picture);
            // The setPicture method should automatically calculate length
        } else {
            field.setDataType("GROUP");
        }

        // Set meaningful usage description
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

    // ... (Keep all other existing methods: processRedefinesLayout, extractLayoutTokens, etc.)

    private List<CopybookTokenizer.Token> extractLayoutTokens(List<CopybookTokenizer.Token> allTokens, int startIndex) {
        List<CopybookTokenizer.Token> layoutTokens = new ArrayList<>();

        for (int i = startIndex; i < allTokens.size(); i++) {
            CopybookTokenizer.Token token = allTokens.get(i);

            if (token.level == 1 && i > startIndex) {
                break;
            }

            layoutTokens.add(token);
        }

        return layoutTokens;
    }

    private void processRedefinesLayout(List<CopybookTokenizer.Token> tokens, ParseResult result) {
        if (tokens.isEmpty()) return;

        CopybookTokenizer.Token firstToken = tokens.get(0);
        RecordLayout layout = new RecordLayout(firstToken.name);
        layout.setRedefines(firstToken.redefines);
        layout.setStartPosition(1);

        Stack<CobolField> fieldStack = new Stack<>();
        PositionTracker positionTracker = new PositionTracker();
        positionTracker.setPosition(1);

        for (int i = 1; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            if (token.isConditionName) {
                if (!fieldStack.isEmpty()) {
                    CobolField parentField = fieldStack.peek();
                    parentField.addConditionName(token.name, token.value);
                }
                continue;
            }

            CobolField field = createFieldFromToken(token);

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

        while (!fieldStack.isEmpty()) {
            CobolField completedField = fieldStack.pop();
            processCompletedField(completedField, positionTracker);

            if (!fieldStack.isEmpty()) {
                fieldStack.peek().addChild(completedField);
            } else {
                layout.getFields().add(completedField);
            }
        }

        createArrayElementsAndCleanup(layout.getFields());

        layout.setLength(positionTracker.getCurrentPosition() - 1);
        result.getRecordLayouts().add(layout);
    }

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
                        getMeaningfulUsage(child.getUsage())
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
