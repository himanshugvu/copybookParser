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
        private String description; // Added for clarity

        public RecordLayout(String name) {
            this.name = name;
            this.fields = new ArrayList<>();
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRedefines() { return redefines; }
        public void setRedefines(String redefines) {
            this.redefines = redefines;
            if (redefines != null) {
                this.description = "Memory overlay of " + redefines + " (same physical location)";
            }
        }
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
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    private static class PositionTracker {
        private int currentPosition = 1;
        private Map<String, Integer> redefinesPositions = new HashMap<>(); // Track REDEFINES positions

        public int getCurrentPosition() { return currentPosition; }
        public void setPosition(int position) { this.currentPosition = position; }
        public void advancePosition(int length) { this.currentPosition += length; }

        public void resetToRedefinesPosition(String redefinesTarget) {
            // All REDEFINES start at position 1 (memory overlay)
            this.currentPosition = 1;
        }
    }

    public ParseResult parseCopybook(Path copybookPath) throws IOException {
        List<String> lines = Files.readAllLines(copybookPath);
        List<CopybookTokenizer.Token> tokens = CopybookTokenizer.tokenize(lines);

        ParseResult result = new ParseResult();
        result.setFileName(copybookPath.getFileName().toString());

        // Separate base records and REDEFINES structures
        Map<String, List<CopybookTokenizer.Token>> structures = groupStructures(tokens);

        // Process base record (the original memory allocation)
        processBaseRecord(structures, result);

        // Process all REDEFINES (memory overlays)
        processRedefinesStructures(structures, result);

        return result;
    }

    private Map<String, List<CopybookTokenizer.Token>> groupStructures(List<CopybookTokenizer.Token> tokens) {
        Map<String, List<CopybookTokenizer.Token>> structures = new HashMap<>();
        List<CopybookTokenizer.Token> currentStructure = new ArrayList<>();
        String currentName = null;

        for (int i = 0; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            // Check for new main structure (01 level)
            if (token.level == 1 && !token.isConditionName) {
                // Save previous structure
                if (currentName != null && !currentStructure.isEmpty()) {
                    structures.put(currentName, new ArrayList<>(currentStructure));
                }

                // Start new structure
                currentName = token.name;
                currentStructure.clear();

                // Add all tokens until next 01 level
                currentStructure.add(token);
                for (int j = i + 1; j < tokens.size(); j++) {
                    CopybookTokenizer.Token nextToken = tokens.get(j);
                    if (nextToken.level == 1 && !nextToken.isConditionName) {
                        break;
                    }
                    currentStructure.add(nextToken);
                    i = j; // Update main loop index
                }
            }
        }

        // Save last structure
        if (currentName != null && !currentStructure.isEmpty()) {
            structures.put(currentName, currentStructure);
        }

        return structures;
    }

    private void processBaseRecord(Map<String, List<CopybookTokenizer.Token>> structures, ParseResult result) {
        // Find the first 01 level record without REDEFINES (this is the base memory allocation)
        for (Map.Entry<String, List<CopybookTokenizer.Token>> entry : structures.entrySet()) {
            List<CopybookTokenizer.Token> tokens = entry.getValue();
            if (!tokens.isEmpty()) {
                CopybookTokenizer.Token firstToken = tokens.get(0);
                if (firstToken.level == 1 && firstToken.redefines == null) {
                    CobolField baseField = processBasicStructure(tokens);

                    // Base record always starts at position 1
                    baseField.setStartPosition(1);

                    // Calculate total length from PIC clause or default
                    int totalLength = baseField.getLength();
                    if (totalLength <= 0) {
                        totalLength = 1000; // Default size
                    }

                    baseField.setEndPosition(totalLength);
                    baseField.setLength(totalLength);

                    result.getFields().add(baseField);
                    result.setTotalLength(totalLength);

                    System.out.println("Base record: " + baseField.getName() + " allocated " + totalLength + " bytes");
                    return; // Only process the first base record
                }
            }
        }
    }

    private void processRedefinesStructures(Map<String, List<CopybookTokenizer.Token>> structures, ParseResult result) {
        for (Map.Entry<String, List<CopybookTokenizer.Token>> entry : structures.entrySet()) {
            List<CopybookTokenizer.Token> tokens = entry.getValue();
            if (!tokens.isEmpty()) {
                CopybookTokenizer.Token firstToken = tokens.get(0);

                // Process structures that REDEFINES others (memory overlays)
                if (firstToken.level == 1 && firstToken.redefines != null) {
                    RecordLayout layout = processRedefinesLayout(tokens, result.getTotalLength());
                    result.getRecordLayouts().add(layout);

                    System.out.println("REDEFINES layout: " + layout.getName() +
                            " overlays " + layout.getRedefines() +
                            " (same memory, length: " + layout.getLength() + ")");
                }
            }
        }
    }

    private CobolField processBasicStructure(List<CopybookTokenizer.Token> tokens) {
        if (tokens.isEmpty()) return null;

        CopybookTokenizer.Token firstToken = tokens.get(0);
        CobolField mainField = createFieldFromToken(firstToken);

        // If it's just a simple field with PIC clause, return it
        if (firstToken.picture != null) {
            return mainField;
        }

        // Otherwise, process children
        Stack<CobolField> fieldStack = new Stack<>();
        PositionTracker positionTracker = new PositionTracker();
        fieldStack.push(mainField);

        for (int i = 1; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            if (token.isConditionName) {
                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addConditionName(token.name, token.value);
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
        while (fieldStack.size() > 1) {
            CobolField completedField = fieldStack.pop();
            processCompletedField(completedField, positionTracker);
            fieldStack.peek().addChild(completedField);
        }

        CobolField result = fieldStack.pop();
        finalizeField(result);

        return result;
    }

    private RecordLayout processRedefinesLayout(List<CopybookTokenizer.Token> tokens, int maxLength) {
        if (tokens.isEmpty()) return null;

        CopybookTokenizer.Token firstToken = tokens.get(0);
        RecordLayout layout = new RecordLayout(firstToken.name);
        layout.setRedefines(firstToken.redefines);
        layout.setStartPosition(1); // All REDEFINES start at position 1 (memory overlay)

        Stack<CobolField> fieldStack = new Stack<>();
        PositionTracker positionTracker = new PositionTracker();

        // Process child fields (skip the 01 level wrapper)
        for (int i = 1; i < tokens.size(); i++) {
            CopybookTokenizer.Token token = tokens.get(i);

            if (token.isConditionName) {
                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addConditionName(token.name, token.value);
                }
                continue;
            }

            // Handle nested REDEFINES within this structure
            if (token.redefines != null) {
                // Reset position to the start of the redefined field
                // Since it's memory overlay, find where the original field starts
                positionTracker.resetToRedefinesPosition(token.redefines);
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

                // Only advance position if this field doesn't REDEFINES another
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

        // Create array elements for OCCURS fields
        createArrayElementsAndCleanup(layout.getFields());

        // Set layout length (cannot exceed the base record length due to memory overlay)
        int layoutLength = Math.min(positionTracker.getCurrentPosition() - 1, maxLength);
        layout.setLength(layoutLength);

        return layout;
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

    private void finalizeField(CobolField field) {
        if (!field.getChildren().isEmpty()) {
            int minStart = Integer.MAX_VALUE;
            int maxEnd = 0;

            for (CobolField child : field.getChildren()) {
                finalizeField(child);
                if (child.getStartPosition() > 0) {
                    minStart = Math.min(minStart, child.getStartPosition());
                    maxEnd = Math.max(maxEnd, child.getEndPosition());
                }
            }

            if (minStart != Integer.MAX_VALUE) {
                if (field.getStartPosition() <= 0) {
                    field.setStartPosition(minStart);
                }
                field.setEndPosition(maxEnd);
                field.setLength(maxEnd - field.getStartPosition() + 1);
            }

            if (field.getDataType() == null) {
                field.setDataType("GROUP");
            }
        }
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

                field.getChildren().clear(); // Clean up redundant children
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
                return 4; // Single precision float
            } else if ("COMP-2".equals(field.getUsage())) {
                return 8; // Double precision float
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
