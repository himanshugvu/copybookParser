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
        private String fileName;
        private int totalLength;

        public ParseResult() {
            this.fields = new ArrayList<>();
        }

        public List<CobolField> getFields() { return fields; }
        public void setFields(List<CobolField> fields) { this.fields = fields; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public int getTotalLength() { return totalLength; }
        public void setTotalLength(int totalLength) { this.totalLength = totalLength; }
    }

    public ParseResult parseCopybook(Path copybookPath) throws IOException {
        List<String> lines = Files.readAllLines(copybookPath);
        List<CopybookTokenizer.Token> tokens = CopybookTokenizer.tokenize(lines);

        ParseResult result = new ParseResult();
        result.setFileName(copybookPath.getFileName().toString());

        Stack<CobolField> fieldStack = new Stack<>();
        int currentPosition = 1;

        for (CopybookTokenizer.Token token : tokens) {
            CobolField field = createFieldFromToken(token);

            // Pop fields from stack until we find the parent level
            while (!fieldStack.isEmpty() && fieldStack.peek().getLevel() >= field.getLevel()) {
                CobolField completedField = fieldStack.pop();
                if (!fieldStack.isEmpty()) {
                    fieldStack.peek().addChild(completedField);
                } else {
                    result.getFields().add(completedField);
                }
            }

            // Set position for elementary fields (fields with PIC clause)
            if (field.getPicture() != null) {
                field.setStartPosition(currentPosition);

                int fieldLength = field.getLength();
                if (field.getOccursCount() > 0) {
                    fieldLength *= field.getOccursCount();
                }

                field.setLength(fieldLength);
                field.setEndPosition(currentPosition + fieldLength - 1);
                currentPosition += fieldLength;
            } else {
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

        // Update group field positions
        updateGroupFieldPositions(result.getFields());

        result.setTotalLength(currentPosition - 1);

        return result;
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

    private void updateGroupFieldPositions(List<CobolField> fields) {
        for (CobolField field : fields) {
            if (!field.getChildren().isEmpty()) {
                updateGroupFieldPositions(field.getChildren());

                // Update group field position and length based on children
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
