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

        public int getCurrentPosition() { return currentPosition; }
        public void setPosition(int position) { this.currentPosition = position; }
        public void advancePosition(int length) { this.currentPosition += length; }
        public void reset() { this.currentPosition = 1; }
    }

    private static class RecordTypeAnalysis {
        boolean isSharedPattern = false;
        CopybookTokenizer.Token sharedRecordTypeField;
        Map<String, String> recordTypeValues = new HashMap<>();
        Map<String, String> layoutNames = new HashMap<>();
    }

    public ParseResult parseCopybook(Path copybookPath) throws IOException {
        List<String> lines = Files.readAllLines(copybookPath);
        List<CopybookTokenizer.Token> tokens = CopybookTokenizer.tokenize(lines);

        ParseResult result = new ParseResult();
        result.setFileName(copybookPath.getFileName().toString());

        // Extract record length from comments
        int recordLength = extractRecordLengthFromComments(lines);
        result.setTotalLength(recordLength);

        // Analyze record type pattern
        RecordTypeAnalysis analysis = analyzeRecordTypePattern(tokens);

        // Create reference field (just for display)
        CobolField referenceField = createReferenceField(tokens, recordLength, analysis);
        if (referenceField != null) {
            result.getFields().add(referenceField);
        }

        // Process actual record layouts
        processRecordLayouts(tokens, result, recordLength, analysis);

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

    private RecordTypeAnalysis analyzeRecordTypePattern(List<CopybookTokenizer.Token> tokens) {
        RecordTypeAnalysis analysis = new RecordTypeAnalysis();

        // Look for shared record type pattern
        for (int i = 0; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            // Check if this field has 88-levels and appears before any REDEFINES
            if (token.picture != null && hasConditionNames(tokens, i)) {
                boolean appearsBeforeRedefines = true;
                for (int j = i + 1; j < tokens.size(); j++) {
                    if (tokens.get(j).redefines != null) {
                        break;
                    } else if (tokens.get(j).level <= token.level && tokens.get(j).picture != null) {
                        appearsBeforeRedefines = false;
                        break;
                    }
                }

                if (appearsBeforeRedefines) {
                    analysis.isSharedPattern = true;
                    analysis.sharedRecordTypeField = token;
                    extractConditionValues(tokens, i, analysis);
                    break;
                }
            }
        }

        return analysis;
    }

    private boolean hasConditionNames(List<CopybookTokenizer.Token> tokens, int fieldIndex) {
        for (int i = fieldIndex + 1; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);
            if (token.level == 88 && token.isConditionName) {
                return true;
            } else if (token.level <= tokens.get(fieldIndex).level) {
                break;
            }
        }
        return false;
    }

    private void extractConditionValues(List<CopybookTokenizer.Token> tokens, int fieldIndex, RecordTypeAnalysis analysis) {
        for (int i = fieldIndex + 1; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);
            if (token.level == 88 && token.isConditionName) {
                analysis.recordTypeValues.put(token.name, token.value);

                // Determine layout name from condition name
                String layoutName = determineLayoutNameFromCondition(token.name);
                if (layoutName != null) {
                    analysis.layoutNames.put(token.value, layoutName);
                }
            } else if (token.level <= tokens.get(fieldIndex).level) {
                break;
            }
        }
    }

    private String determineLayoutNameFromCondition(String conditionName) {
        if (conditionName.contains("HDR")) {
            return "HEADER-RECORD";
        } else if (conditionName.contains("DTL")) {
            return "DETAIL-RECORD";
        } else if (conditionName.contains("TRL") || conditionName.contains("TRAIL")) {
            return "TRAILER-RECORD";
        }
        return null;
    }

    private CobolField createReferenceField(List<CopybookTokenizer.Token> tokens, int recordLength, RecordTypeAnalysis analysis) {
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
                if (analysis.isSharedPattern) {
                    addSharedRecordTypeField(analysis, field);
                }

                return field;
            }
        }
        return null;
    }

    private void addSharedRecordTypeField(RecordTypeAnalysis analysis, CobolField parentField) {
        if (analysis.sharedRecordTypeField != null) {
            CobolField recordTypeField = createFieldFromToken(analysis.sharedRecordTypeField);
            recordTypeField.setStartPosition(1);

            int fieldLength = calculateActualFieldLength(recordTypeField);
            recordTypeField.setLength(fieldLength);
            recordTypeField.setEndPosition(fieldLength);

            // Add condition names
            for (Map.Entry<String, String> entry : analysis.recordTypeValues.entrySet()) {
                recordTypeField.addConditionName(entry.getKey(), entry.getValue());
            }

            parentField.addChild(recordTypeField);
        }
    }

    private void processRecordLayouts(List<CopybookTokenizer.Token> tokens, ParseResult result, int recordLength, RecordTypeAnalysis analysis) {
        if (analysis.isSharedPattern) {
            // Process shared pattern - create layouts for each record type value
            createLayoutsFromSharedPattern(tokens, result, recordLength, analysis);
        } else {
            // Process individual pattern - find separate record structures
            processIndividualRecordLayouts(tokens, result, recordLength);
        }
    }

    private void createLayoutsFromSharedPattern(List<CopybookTokenizer.Token> tokens, ParseResult result, int recordLength, RecordTypeAnalysis analysis) {
        // Find actual record structures (05-level fields after the shared record type)
        Map<String, List<CopybookTokenizer.Token>> recordStructures = groupRecordStructures(tokens, analysis);

        // Create layouts for each record type value
        for (Map.Entry<String, String> entry : analysis.layoutNames.entrySet()) {
            String recordValue = entry.getKey();
            String layoutType = entry.getValue();

            RecordLayout layout = createLayoutForRecordType(tokens, recordStructures, recordLength, analysis, recordValue, layoutType);
            if (layout != null) {
                result.getRecordLayouts().add(layout);
            }
        }
    }

    private Map<String, List<CopybookTokenizer.Token>> groupRecordStructures(List<CopybookTokenizer.Token> tokens, RecordTypeAnalysis analysis) {
        Map<String, List<CopybookTokenizer.Token>> structures = new HashMap<>();

        boolean pastSharedField = false;
        String currentStructure = null;
        List<CopybookTokenizer.Token> currentTokens = new ArrayList<>();

        for (CopybookTokenizer.Token token : tokens) {
            // Skip until we get past the shared record type field
            if (!pastSharedField) {
                if (token == analysis.sharedRecordTypeField) {
                    pastSharedField = true;
                }
                continue;
            }

            // Skip 88-level condition names
            if (token.isConditionName) {
                continue;
            }

            // Check for new 05-level structure
            if (token.level == 5 && !token.isConditionName) {
                // Save previous structure
                if (currentStructure != null && !currentTokens.isEmpty()) {
                    structures.put(currentStructure, new ArrayList<>(currentTokens));
                }

                // Start new structure
                currentStructure = token.name;
                currentTokens.clear();
                currentTokens.add(token);
            } else if (currentStructure != null) {
                currentTokens.add(token);
            }
        }

        // Save last structure
        if (currentStructure != null && !currentTokens.isEmpty()) {
            structures.put(currentStructure, currentTokens);
        }

        return structures;
    }

    private RecordLayout createLayoutForRecordType(List<CopybookTokenizer.Token> tokens, Map<String, List<CopybookTokenizer.Token>> recordStructures, int recordLength, RecordTypeAnalysis analysis, String recordValue, String layoutType) {
        // Determine the actual record structure name
        String structureName = findStructureForLayoutType(recordStructures, layoutType);
        if (structureName == null) {
            // Create a default layout
            return createDefaultLayout(layoutType, recordValue, recordLength, analysis);
        }

        List<CopybookTokenizer.Token> structureTokens = recordStructures.get(structureName);

        RecordLayout layout = new RecordLayout(structureName);
        layout.setStartPosition(1);
        layout.setLength(recordLength);
        layout.getRecordTypeValues().add(recordValue);
        layout.setDescription(layoutType + " - identified when positions 1-" + analysis.sharedRecordTypeField.picture.replaceAll("[^0-9]", "") + " = '" + recordValue + "'");

        // Set REDEFINES if applicable
        CopybookTokenizer.Token firstToken = structureTokens.get(0);
        if (firstToken.redefines != null) {
            layout.setRedefines(firstToken.redefines);
        }

        // Add shared record type field
        CobolField sharedRecordType = createSharedRecordTypeFieldForLayout(analysis);
        layout.getFields().add(sharedRecordType);

        // Process structure fields
        processStructureFields(structureTokens, layout);

        return layout;
    }

    private String findStructureForLayoutType(Map<String, List<CopybookTokenizer.Token>> recordStructures, String layoutType) {
        for (String structureName : recordStructures.keySet()) {
            if ((layoutType.equals("HEADER-RECORD") && structureName.contains("HEADER")) ||
                    (layoutType.equals("DETAIL-RECORD") && structureName.contains("DETAIL")) ||
                    (layoutType.equals("TRAILER-RECORD") && (structureName.contains("TRAILER") || structureName.contains("TRAIL")))) {
                return structureName;
            }
        }
        return null;
    }

    private RecordLayout createDefaultLayout(String layoutType, String recordValue, int recordLength, RecordTypeAnalysis analysis) {
        RecordLayout layout = new RecordLayout(layoutType);
        layout.setStartPosition(1);
        layout.setLength(recordLength);
        layout.getRecordTypeValues().add(recordValue);
        layout.setDescription(layoutType + " - identified when positions 1-" + analysis.sharedRecordTypeField.picture.replaceAll("[^0-9]", "") + " = '" + recordValue + "'");

        // Add shared record type field
        CobolField sharedRecordType = createSharedRecordTypeFieldForLayout(analysis);
        layout.getFields().add(sharedRecordType);

        // Add placeholder data field
        int recordTypeLength = calculateActualFieldLength(createFieldFromToken(analysis.sharedRecordTypeField));
        int dataLength = recordLength - recordTypeLength;

        CobolField dataField = new CobolField(10, layoutType.replace("-RECORD", "-DATA"));
        dataField.setPicture("X(" + dataLength + ")");
        dataField.setStartPosition(recordTypeLength + 1);
        dataField.setEndPosition(recordLength);
        dataField.setLength(dataLength);
        dataField.setDataType("STRING");
        dataField.setUsage("Text/ASCII format (1 byte per character)");

        layout.getFields().add(dataField);

        return layout;
    }

    private CobolField createSharedRecordTypeFieldForLayout(RecordTypeAnalysis analysis) {
        CobolField recordTypeField = createFieldFromToken(analysis.sharedRecordTypeField);
        recordTypeField.setStartPosition(1);

        int fieldLength = calculateActualFieldLength(recordTypeField);
        recordTypeField.setLength(fieldLength);
        recordTypeField.setEndPosition(fieldLength);

        // Add condition names
        for (Map.Entry<String, String> entry : analysis.recordTypeValues.entrySet()) {
            recordTypeField.addConditionName(entry.getKey(), entry.getValue());
        }

        return recordTypeField;
    }

    private void processStructureFields(List<CopybookTokenizer.Token> structureTokens, RecordLayout layout) {
        Stack<CobolField> fieldStack = new Stack<>();
        PositionTracker positionTracker = new PositionTracker();
        positionTracker.setPosition(layout.getFields().get(0).getEndPosition() + 1); // Start after record type field

        // Process fields starting from index 1 (skip the structure header)
        for (int i = 1; i < structureTokens.size(); i++) {
            CopybookTokenizer.Token token = structureTokens.get(i);

            if (token.isConditionName) {
                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addConditionName(token.name, token.value);
                }
                continue;
            }

            // Handle nested REDEFINES
            if (token.redefines != null) {
                int redefinesPos = findRedefinesPosition(layout.getFields(), fieldStack, token.redefines);
                positionTracker.setPosition(redefinesPos);
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

                // Only advance if not a REDEFINES field
                if (token.redefines == null) {
                    positionTracker.advancePosition(fieldLength);
                }
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
    }

    private int findRedefinesPosition(List<CobolField> layoutFields, Stack<CobolField> fieldStack, String redefinesName) {
        // Search in layout fields first
        for (CobolField field : layoutFields) {
            if (field.getName().equals(redefinesName)) {
                return field.getStartPosition();
            }
            int pos = findRedefinesPositionInChildren(field.getChildren(), redefinesName);
            if (pos > 0) return pos;
        }

        // Search in field stack
        for (CobolField field : fieldStack) {
            if (field.getName().equals(redefinesName)) {
                return field.getStartPosition();
            }
            int pos = findRedefinesPositionInChildren(field.getChildren(), redefinesName);
            if (pos > 0) return pos;
        }

        return 1; // Default
    }

    private int findRedefinesPositionInChildren(List<CobolField> children, String redefinesName) {
        for (CobolField child : children) {
            if (child.getName().equals(redefinesName)) {
                return child.getStartPosition();
            }
            int pos = findRedefinesPositionInChildren(child.getChildren(), redefinesName);
            if (pos > 0) return pos;
        }
        return 0;
    }

    private void processIndividualRecordLayouts(List<CopybookTokenizer.Token> tokens, ParseResult result, int recordLength) {
        // Find 01-level records with REDEFINES
        for (int i = 0; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            if (token.level == 1 && token.redefines != null && !token.isConditionName) {
                RecordLayout layout = createIndividualRecordLayout(tokens, i, recordLength);
                if (layout != null) {
                    result.getRecordLayouts().add(layout);
                }
            }
        }
    }

    private RecordLayout createIndividualRecordLayout(List<CopybookTokenizer.Token> tokens, int startIndex, int recordLength) {
        CopybookTokenizer.Token firstToken = tokens.get(startIndex);

        RecordLayout layout = new RecordLayout(firstToken.name);
        layout.setRedefines(firstToken.redefines);
        layout.setStartPosition(1);
        layout.setLength(recordLength);

        Stack<CobolField> fieldStack = new Stack<>();
        PositionTracker positionTracker = new PositionTracker();

        // Process fields within this record
        for (int i = startIndex + 1; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            // Stop when we hit another 01-level record
            if (token.level == 1 && !token.isConditionName) {
                break;
            }

            if (token.isConditionName) {
                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addConditionName(token.name, token.value);

                    // Collect record type values
                    if (isRecordTypeField(fieldStack.peek().getName())) {
                        layout.getRecordTypeValues().add(token.value);
                    }
                }
                continue;
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

        createArrayElementsAndCleanup(layout.getFields());

        return layout;
    }

    private boolean isRecordTypeField(String fieldName) {
        return fieldName != null && (
                fieldName.contains("RECORD-TYPE") ||
                        fieldName.contains("REC-TYPE") ||
                        fieldName.contains("TYPE") ||
                        fieldName.endsWith("-TYPE")
        );
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
