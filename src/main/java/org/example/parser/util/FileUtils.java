package org.example.parser.util;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FileUtils {

    public static List<String> readCopybookFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Copybook file not found: " + filePath);
        }

        if (!Files.isReadable(path)) {
            throw new IOException("Cannot read copybook file: " + filePath);
        }

        return Files.readAllLines(path);
    }

    public static void writeJsonFile(String json, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        Files.writeString(path, json);
    }

    public static boolean isValidCopybookFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }

        Path path = Paths.get(filePath);
        return Files.exists(path) && Files.isReadable(path);
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? "" : fileName.substring(lastDot + 1);
    }

    public static String removeExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? fileName : fileName.substring(0, lastDot);
    }
}
