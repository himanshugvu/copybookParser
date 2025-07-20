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
        private int endPosition;
        private int length;
        private List<String> recordTypeValues;
        private String description;

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
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    private static class PositionTracker {
        private int currentPosition = 1;
        private Map<String, Integer> redefinesPositions = new HashMap<>();

        public int getCurrentPosition() { return currentPosition; }
        public void setPosition(int position) { this.currentPosition = position; }
        public void advancePosition(int length) { this.currentPosition += length; }

        public void saveRedefinesPosition(String fieldName, int position) {
            redefinesPositions.put(fieldName, position);
        }

        public void restoreRedefinesPosition(String fieldName) {
            Integer savedPosition = redefinesPositions.get(fieldName);
            if (savedPosition != null) {
                this.currentPosition = savedPosition;
            }
        }
    }

    public ParseResult parseCopybook(Path copybookPath) throws IOException {
        List<String> lines = Files.readAllLines(copybookPath);
        List<CopybookTokenizer.Token> tokens = CopybookTokenizer.tokenize(lines);

        ParseResult result = new ParseResult();
        result.setFileName(copybookPath.getFileName().toString());

        // Process the complete structure
        processCompleteStructure(tokens, result);

        return result;
    }

    private void processCompleteStructure(List<CopybookTokenizer.Token> tokens, ParseResult result) {
        if (tokens.isEmpty()) return;

        // Find the main 01-level record
        CopybookTokenizer.Token mainRecord = tokens.get(0);
        if (mainRecord.level != 1) return;

        result.setTotalLength(300); // From comment: REC LEN : 300

        Stack<CobolField> fieldStack = new Stack<>();
        PositionTracker positionTracker = new PositionTracker();

        // Create main record field
        CobolField mainField = createFieldFromToken(mainRecord);
        mainField.setStartPosition(1);
        mainField.setLength(300);
        mainField.setEndPosition(300);
        fieldStack.push(mainField);

        // Track shared record type field
        CobolField recordTypeField = null;

        // Process all tokens
        for (int i = 1; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            // Handle 88-level condition names
            if (token.isConditionName) {
                if (!fieldStack.isEmpty()) {
                    CobolField parentField = fieldStack.peek();
                    parentField.addConditionName(token.name, token.value);

                    // Track record type values
                    if (isRecordTypeField(parentField.getName())) {
                        recordTypeField = parentField;
                    }
                }
                continue;
            }

            // Handle REDEFINES
            if (token.redefines != null) {
                // Find position of the field being redefined
                int redefinesPosition = findFieldStartPosition(fieldStack, token.redefines);
                positionTracker.setPosition(redefinesPosition);
                positionTracker.saveRedefinesPosition(token.name, redefinesPosition);
            }

            CobolField field = createFieldFromToken(token);

            // Pop completed fields
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

        // Process remaining fields
        while (!fieldStack.isEmpty()) {
            CobolField completedField = fieldStack.pop();
            processCompletedField(completedField, positionTracker);

            if (!fieldStack.isEmpty()) {
                fieldStack.peek().addChild(completedField);
            } else {
                result.getFields().add(completedField);
            }
        }

        // Create record layouts from the structure
        createRecordLayouts(result, recordTypeField);

        // Handle OCCURS fields
        for (CobolField field : result.getFields()) {
            createArrayElementsAndCleanup(field.getChildren());
        }
    }

    private void createRecordLayouts(ParseResult result, CobolField recordTypeField) {
        if (result.getFields().isEmpty()) return;

        CobolField mainRecord = result.getFields().get(0);

        // Find header and detail record structures
        CobolField headerRecord = null;
        CobolField detailRecord = null;

        for (CobolField child : mainRecord.getChildren()) {
            if ("CAONPOST-HEADER-RECORD".equals(child.getName())) {
                headerRecord = child;
            } else if ("CAONPOST-DETAIL-RECORD".equals(child.getName())) {
                detailRecord = child;
            }
        }

        // Create header record layout
        if (headerRecord != null) {
            RecordLayout headerLayout = new RecordLayout("HEADER-RECORD");
            headerLayout.setStartPosition(1);
            headerLayout.setLength(300);
            headerLayout.setDescription("Header record layout (identified by CAONPOST-RECORD-TYPE = '00')");
            headerLayout.getRecordTypeValues().add("00");

            // Add shared record type field
            if (recordTypeField != null) {
                headerLayout.getFields().add(copyField(recordTypeField));
            }

            // Add header-specific fields
            for (CobolField field : headerRecord.getChildren()) {
                headerLayout.getFields().add(copyField(field));
            }

            result.getRecordLayouts().add(headerLayout);
        }

        // Create detail record layout
        if (detailRecord != null) {
            RecordLayout detailLayout = new RecordLayout("DETAIL-RECORD");
            detailLayout.setStartPosition(1);
            detailLayout.setLength(300);
            detailLayout.setDescription("Detail record layout (identified by CAONPOST-RECORD-TYPE = '01')");
            detailLayout.getRecordTypeValues().add("01");

            // Add shared record type field
            if (recordTypeField != null) {
                detailLayout.getFields().add(copyField(recordTypeField));
            }

            // Add detail-specific fields
            for (CobolField field : detailRecord.getChildren()) {
                detailLayout.getFields().add(copyField(field));
            }

            result.getRecordLayouts().add(detailLayout);
        }

        // Create trailer record layout (placeholder since not fully defined in the sample)
        RecordLayout trailerLayout = new RecordLayout("TRAILER-RECORD");
        trailerLayout.setStartPosition(1);
        trailerLayout.setLength(300);
        trailerLayout.setDescription("Trailer record layout (identified by CAONPOST-RECORD-TYPE = '99')");
        trailerLayout.getRecordTypeValues().add("99");

        // Add shared record type field
        if (recordTypeField != null) {
            trailerLayout.getFields().add(copyField(recordTypeField));
        }

        result.getRecordLayouts().add(trailerLayout);
    }

    private CobolField copyField(CobolField original) {
        CobolField copy = new CobolField(original.getLevel(), original.getName());
        copy.setPicture(original.getPicture());
        copy.setStartPosition(original.getStartPosition());
        copy.setEndPosition(original.getEndPosition());
        copy.setLength(original.getLength());
        copy.setDataType(original.getDataType());
        copy.setUsage(original.getUsage());
        copy.setSigned(original.isSigned());
        copy.setDecimal(original.isDecimal());
        copy.setDecimalPlaces(original.getDecimalPlaces());
        copy.setOccursCount(original.getOccursCount());
        copy.setRedefines(original.getRedefines());
        copy.setValue(original.getValue());

        // Copy condition names
        for (CobolField.ConditionName condName : original.getConditionNames()) {
            copy.addConditionName(condName.getName(), condName.getValue());
        }

        // Recursively copy children
        for (CobolField child : original.getChildren()) {
            copy.addChild(copyField(child));
        }

        return copy;
    }

    private boolean isRecordTypeField(String fieldName) {
        return fieldName != null && (
                fieldName.contains("RECORD-TYPE") ||
                        fieldName.equals("CAONPOST-RECORD-TYPE")
        );
    }

    private int findFieldStartPosition(Stack<CobolField> fieldStack, String fieldName) {
        // Search through the field stack to find the named field
        for (CobolField field : fieldStack) {
            if (field.getName().equals(fieldName)) {
                return field.getStartPosition();
            }
            int position = searchInChildren(field, fieldName);
            if (position > 0) return position;
        }
        return 1; // Default position
    }

    private int searchInChildren(CobolField field, String fieldName) {
        for (CobolField child : field.getChildren()) {
            if (child.getName().equals(fieldName)) {
                return child.getStartPosition();
            }
            int position = searchInChildren(child, fieldName);
            if (position > 0) return position;
        }
        return 0;
    }

    // ... (Keep all other existing methods: processCompletedField, calculateActualFieldLength, etc.)

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
