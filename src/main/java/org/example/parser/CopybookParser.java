package org.example.parser;

import org.example.parser.util.JsonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class CopybookParser {

    // ... (Keep existing ParseResult and RecordLayout classes unchanged)

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

        Stack<CobolField> fieldStack = new Stack<>();
        PositionTracker positionTracker = new PositionTracker();
        int basePosition = 1;

        for (CopybookTokenizer.Token token : tokens) {
            CobolField field = createFieldFromToken(token);

            if (field.getLevel() == 1 && field.getRedefines() != null) {
                positionTracker.setPosition(basePosition);
                RecordLayout layout = new RecordLayout(field.getName());
                layout.setRedefines(field.getRedefines());
                layout.setStartPosition(basePosition);
                processRecordLayout(tokens, tokens.indexOf(token), layout, result);
                continue;
            }

            while (!fieldStack.isEmpty() && fieldStack.peek().getLevel() >= field.getLevel()) {
                CobolField completedField = fieldStack.pop();
                processCompletedField(completedField, positionTracker);

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
                result.getFields().add(completedField);
            }
        }

        // Create simplified array elements and clean up redundant children
        createArrayElementsAndCleanup(result.getFields());

        finalizeGroupFields(result.getFields());
        updateRecordLayoutLengths(result.getRecordLayouts());
        result.setTotalLength(positionTracker.getCurrentPosition() - 1);

        return result;
    }

    private void createArrayElementsAndCleanup(List<CobolField> fields) {
        for (CobolField field : fields) {
            if (field.getOccursCount() > 0) {
                // Calculate single occurrence length
                int singleOccurrenceLength = calculateGroupFieldLength(field);
                int currentPos = field.getStartPosition();

                // Create array elements for each occurrence
                for (int i = 1; i <= field.getOccursCount(); i++) {
                    CobolField.ArrayElement arrayElement = new CobolField.ArrayElement(i, currentPos, singleOccurrenceLength);

                    // Add field positions for this occurrence
                    addFieldPositionsToArrayElement(field.getChildren(), arrayElement, currentPos);

                    field.getArrayElements().add(arrayElement);
                    currentPos += singleOccurrenceLength;
                }

                // Clear children since we now have arrayElements
                field.getChildren().clear();
            } else {
                // Recursively process children for non-OCCURS fields
                createArrayElementsAndCleanup(field.getChildren());
            }
        }
    }

    private void addFieldPositionsToArrayElement(List<CobolField> children, CobolField.ArrayElement arrayElement, int basePosition) {
        int currentPos = basePosition;

        for (CobolField child : children) {
            if (child.getPicture() != null) {
                // Elementary field
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
                // Group field - recursively add its children
                addFieldPositionsToArrayElement(child.getChildren(), arrayElement, currentPos);
                currentPos += calculateGroupFieldLength(child);
            }
        }
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

    private void finalizeGroupFields(List<CobolField> fields) {
        for (CobolField field : fields) {
            // Only process children if they exist (non-OCCURS fields)
            if (!field.getChildren().isEmpty()) {
                finalizeGroupFields(field.getChildren());

                if (field.getLength() == 0) {
                    int minStart = Integer.MAX_VALUE;
                    int maxEnd = 0;

                    for (CobolField child : field.getChildren()) {
                        if (child.getStartPosition() > 0) {
                            minStart = Math.min(minStart, child.getStartPosition());
                            maxEnd = Math.max(maxEnd, child.getEndPosition());
                        }
                    }

                    if (minStart != Integer.MAX_VALUE) {
                        field.setStartPosition(minStart);
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
        PositionTracker positionTracker = new PositionTracker();
        positionTracker.setPosition(layout.getStartPosition());
        boolean inCurrentLayout = false;

        for (int i = startIndex; i < allTokens.size(); i++) {
            CopybookTokenizer.Token token = allTokens.get(i);
            CobolField field = createFieldFromToken(token);

            if (field.getLevel() == 1 && i > startIndex) break;

            if (field.getLevel() == 1) {
                inCurrentLayout = true;
                fieldStack.push(field);
                continue;
            }

            if (!inCurrentLayout) continue;

            while (!fieldStack.isEmpty() && fieldStack.peek().getLevel() >= field.getLevel()) {
                CobolField completedField = fieldStack.pop();
                processCompletedField(completedField, positionTracker);

                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addChild(completedField);
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
        finalizeGroupFields(layout.getFields());
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
            finalizeGroupFields(layout.getFields());

            int maxEnd = 0;
            for (CobolField field : layout.getFields()) {
                maxEnd = Math.max(maxEnd, field.getEndPosition());
            }
            layout.setLength(maxEnd - layout.getStartPosition() + 1);
        }
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
