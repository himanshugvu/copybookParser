package org.example.parser;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

public class CobolField {
    @JsonProperty("level")
    private int level;

    @JsonProperty("name")
    private String name;

    @JsonProperty("picture")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String picture;

    @JsonProperty("startPosition")
    private int startPosition;

    @JsonProperty("endPosition")
    private int endPosition;

    @JsonProperty("length")
    private int length;

    @JsonProperty("dataType")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String dataType;

    @JsonProperty("usage")
    private String usage;

    @JsonProperty("signed")
    private boolean signed;

    @JsonProperty("decimal")
    private boolean decimal;

    @JsonProperty("decimalPlaces")
    private int decimalPlaces;

    @JsonProperty("occursCount")
    private int occursCount;

    @JsonProperty("redefines")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String redefines;

    @JsonProperty("value")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String value;

    @JsonProperty("children")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<CobolField> children;

    @JsonProperty("arrayElements")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ArrayElement> arrayElements;

    @JsonProperty("conditionNames")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ConditionName> conditionNames;

    @JsonProperty("arrayIndex")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer arrayIndex;

    public CobolField() {
        this.children = new ArrayList<>();
        this.arrayElements = new ArrayList<>();
        this.conditionNames = new ArrayList<>();
    }

    public CobolField(int level, String name) {
        this();
        this.level = level;
        this.name = name;
    }

    public static class ArrayElement {
        @JsonProperty("index")
        private int index;

        @JsonProperty("startPosition")
        private int startPosition;

        @JsonProperty("endPosition")
        private int endPosition;

        @JsonProperty("length")
        private int length;

        @JsonProperty("fields")
        private List<FieldPosition> fields;

        public ArrayElement(int index, int startPosition, int length) {
            this.index = index;
            this.startPosition = startPosition;
            this.length = length;
            this.endPosition = startPosition + length - 1;
            this.fields = new ArrayList<>();
        }

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public int getStartPosition() { return startPosition; }
        public void setStartPosition(int startPosition) { this.startPosition = startPosition; }
        public int getEndPosition() { return endPosition; }
        public void setEndPosition(int endPosition) { this.endPosition = endPosition; }
        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public List<FieldPosition> getFields() { return fields; }
        public void setFields(List<FieldPosition> fields) { this.fields = fields; }
    }

    public static class FieldPosition {
        @JsonProperty("name")
        private String name;

        @JsonProperty("startPosition")
        private int startPosition;

        @JsonProperty("endPosition")
        private int endPosition;

        @JsonProperty("length")
        private int length;

        @JsonProperty("picture")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String picture;

        @JsonProperty("dataType")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String dataType;

        @JsonProperty("usage")
        private String usage;

        public FieldPosition(String name, int startPosition, int length, String picture, String dataType, String usage) {
            this.name = name;
            this.startPosition = startPosition;
            this.length = length;
            this.endPosition = startPosition + length - 1;
            this.picture = picture;
            this.dataType = dataType;
            this.usage = usage;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getStartPosition() { return startPosition; }
        public void setStartPosition(int startPosition) { this.startPosition = startPosition; }
        public int getEndPosition() { return endPosition; }
        public void setEndPosition(int endPosition) { this.endPosition = endPosition; }
        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public String getPicture() { return picture; }
        public void setPicture(String picture) { this.picture = picture; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public String getUsage() { return usage; }
        public void setUsage(String usage) { this.usage = usage; }
    }

    public static class ConditionName {
        @JsonProperty("name")
        private String name;

        @JsonProperty("value")
        private String value;

        public ConditionName(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public String getValue() { return value; }
    }

    // All getters and setters
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPicture() { return picture; }
    public void setPicture(String picture) {
        this.picture = picture;
        analyzePicture();
    }

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

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }

    public boolean isSigned() { return signed; }
    public void setSigned(boolean signed) { this.signed = signed; }

    public boolean isDecimal() { return decimal; }
    public void setDecimal(boolean decimal) { this.decimal = decimal; }

    public int getDecimalPlaces() { return decimalPlaces; }
    public void setDecimalPlaces(int decimalPlaces) { this.decimalPlaces = decimalPlaces; }

    public int getOccursCount() { return occursCount; }
    public void setOccursCount(int occursCount) { this.occursCount = occursCount; }

    public String getRedefines() { return redefines; }
    public void setRedefines(String redefines) { this.redefines = redefines; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public List<CobolField> getChildren() { return children; }
    public void setChildren(List<CobolField> children) { this.children = children; }

    public void addChild(CobolField child) {
        this.children.add(child);
    }

    public List<ArrayElement> getArrayElements() { return arrayElements; }
    public void setArrayElements(List<ArrayElement> arrayElements) { this.arrayElements = arrayElements; }

    public List<ConditionName> getConditionNames() { return conditionNames; }
    public void setConditionNames(List<ConditionName> conditionNames) { this.conditionNames = conditionNames; }

    public void addConditionName(String name, String value) {
        this.conditionNames.add(new ConditionName(name, value));
    }

    public Integer getArrayIndex() { return arrayIndex; }
    public void setArrayIndex(Integer arrayIndex) { this.arrayIndex = arrayIndex; }

    private void analyzePicture() {
        if (picture == null) {
            this.dataType = "GROUP";
            return;
        }

        String pic = picture.toUpperCase().trim();

        this.signed = pic.startsWith("S") || pic.contains("S");
        this.decimal = pic.contains("V");

        if (decimal) {
            String[] parts = pic.split("V");
            if (parts.length > 1) {
                String decimalPart = parts[1];
                this.decimalPlaces = extractLength(decimalPart);
            }
        }

        if (pic.contains("9")) {
            this.dataType = "NUMBER";
        } else if (pic.contains("X") || pic.contains("A")) {
            this.dataType = "STRING";
        } else {
            this.dataType = "STRING";
        }

        this.length = calculatePictureLength(pic);
    }

    private int extractLength(String picturePart) {
        if (picturePart.contains("(") && picturePart.contains(")")) {
            try {
                int start = picturePart.indexOf("(") + 1;
                int end = picturePart.indexOf(")");
                return Integer.parseInt(picturePart.substring(start, end));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return picturePart.length();
    }

    private int calculatePictureLength(String picture) {
        int totalLength = 0;
        String pic = picture.replaceAll("\\s+", "");

        while (pic.contains("(")) {
            int start = pic.lastIndexOf('(');
            int end = pic.indexOf(')', start);
            if (end != -1) {
                try {
                    int count = Integer.parseInt(pic.substring(start + 1, end));
                    String before = pic.substring(0, start - 1);
                    String after = pic.substring(end + 1);
                    totalLength += count;
                    pic = before + after;
                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    break;
                }
            } else {
                break;
            }
        }

        for (char c : pic.toCharArray()) {
            if (c == 'X' || c == '9' || c == 'A' || c == 'Z') {
                totalLength++;
            }
        }

        return totalLength > 0 ? totalLength : 1;
    }
}
