package org.example.parser;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class CobolField {
    @JsonProperty("level")
    private int level;

    @JsonProperty("name")
    private String name;

    @JsonProperty("picture")
    private String picture;

    @JsonProperty("startPosition")
    private int startPosition;

    @JsonProperty("endPosition")
    private int endPosition;

    @JsonProperty("length")
    private int length;

    @JsonProperty("dataType")
    private String dataType;

    @JsonProperty("usage")
    private String usage;

    @JsonProperty("signed")
    private boolean signed;

    @JsonProperty("numeric")
    private boolean numeric;

    @JsonProperty("decimal")
    private boolean decimal;

    @JsonProperty("decimalPlaces")
    private int decimalPlaces;

    @JsonProperty("occursCount")
    private int occursCount;

    @JsonProperty("redefines")
    private String redefines;

    @JsonProperty("value")
    private String value;

    @JsonProperty("children")
    private List<CobolField> children;

    public CobolField() {
        this.children = new ArrayList<>();
    }

    public CobolField(int level, String name) {
        this();
        this.level = level;
        this.name = name;
    }

    // Getters and Setters
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
        this.endPosition = this.startPosition + length - 1;
    }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }

    public boolean isSigned() { return signed; }
    public void setSigned(boolean signed) { this.signed = signed; }

    public boolean isNumeric() { return numeric; }
    public void setNumeric(boolean numeric) { this.numeric = numeric; }

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

    private void analyzePicture() {
        if (picture == null) return;

        String pic = picture.toUpperCase().trim();

        // Check if numeric
        this.numeric = pic.contains("9") || pic.contains("S9");

        // Check if signed
        this.signed = pic.startsWith("S") || pic.contains("S");

        // Check if decimal
        this.decimal = pic.contains("V");

        // Calculate decimal places
        if (decimal) {
            String[] parts = pic.split("V");
            if (parts.length > 1) {
                String decimalPart = parts[1];
                this.decimalPlaces = extractLength(decimalPart);
            }
        }

        // Determine data type
        if (pic.contains("9")) {
            this.dataType = signed ? "SIGNED_NUMERIC" : "NUMERIC";
        } else if (pic.contains("X")) {
            this.dataType = "ALPHANUMERIC";
        } else if (pic.contains("A")) {
            this.dataType = "ALPHABETIC";
        } else {
            this.dataType = "ALPHANUMERIC";
        }

        // Calculate length
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

        // Handle parentheses notation like X(10), 9(5), etc.
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

        // Count remaining characters (excluding format specifiers)
        for (char c : pic.toCharArray()) {
            if (c == 'X' || c == '9' || c == 'A' || c == 'Z') {
                totalLength++;
            }
        }

        return totalLength > 0 ? totalLength : 1;
    }
}

