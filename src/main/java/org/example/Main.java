package org.example;

import org.example.parser.CobolField;
import org.example.parser.CopybookParser;
import org.example.parser.util.FileUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) {
        try {
            CliOptions options = parseArguments(args);

            if (options.showHelp) {
                showHelp();
                return;
            }

            if (options.showVersion) {
                showVersion();
                return;
            }

            if (options.inputFile == null) {
                System.err.println("Error: Input file is required");
                showUsage();
                System.exit(1);
            }

            // Validate input file
            if (!FileUtils.isValidCopybookFile(options.inputFile)) {
                System.err.println("Error: Invalid or non-existent copybook file: " + options.inputFile);
                System.exit(1);
            }

            // Determine output file
            if (options.outputFile == null) {
                options.outputFile = FileUtils.removeExtension(options.inputFile) + ".json";
            }

            if (options.verbose) {
                System.out.println("COBOL Copybook Parser v1.0.0");
                System.out.println("Input file: " + options.inputFile);
                System.out.println("Output file: " + options.outputFile);
                System.out.println("Excluding 88-level condition names for fixed-length file processing...");
                System.out.println("Parsing copybook...");
            }

            // Parse the copybook
            Path copybookPath = Paths.get(options.inputFile);
            CopybookParser parser = new CopybookParser();
            CopybookParser.ParseResult result = parser.parseCopybook(copybookPath);

            // Generate JSON
            String json;
            if (options.prettyPrint) {
                json = parser.toPrettyJson(result);
            } else {
                json = parser.toJson(result);
            }

            // Save to file
            FileUtils.writeJsonFile(json, options.outputFile);

            // Display results
            if (options.verbose) {
                System.out.println("\nParsing completed successfully!");
                System.out.println("File: " + result.getFileName());
                System.out.println("Total record length: " + result.getTotalLength() + " bytes");
                System.out.println("Number of data fields: " + countFields(result.getFields()));
                if (!result.getRecordLayouts().isEmpty()) {
                    System.out.println("Record layouts found: " + result.getRecordLayouts().size());
                    for (var layout : result.getRecordLayouts()) {
                        System.out.println("  - " + layout.getName() +
                            (layout.getRedefines() != null ? " (REDEFINES " + layout.getRedefines() + ")" : "") +
                            " - Length: " + layout.getLength() + " bytes");
                    }
                }
                System.out.println("JSON saved to: " + options.outputFile);
                System.out.println("\nNote: 88-level condition names excluded from output for fixed-length file processing.");
            } else {
                System.out.println("Successfully parsed " + options.inputFile + " -> " + options.outputFile);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (e.getMessage().contains("88-level") || e.getMessage().contains("condition")) {
                System.err.println("Note: 88-level condition names are automatically excluded for fixed-length file processing.");
            }
            System.exit(1);
        }
    }

    // Rest of the methods remain the same...
    private static CliOptions parseArguments(String[] args) {
        CliOptions options = new CliOptions();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "-h", "--help" -> options.showHelp = true;
                case "-V", "--version" -> options.showVersion = true;
                case "-v", "--verbose" -> options.verbose = true;
                case "-p", "--pretty" -> options.prettyPrint = true;
                case "--no-pretty" -> options.prettyPrint = false;
                case "-o", "--output" -> {
                    if (i + 1 < args.length) {
                        options.outputFile = args[++i];
                    } else {
                        throw new IllegalArgumentException("Option " + arg + " requires an argument");
                    }
                }
                default -> {
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    } else if (options.inputFile == null) {
                        options.inputFile = arg;
                    } else {
                        throw new IllegalArgumentException("Multiple input files not supported: " + arg);
                    }
                }
            }
        }

        return options;
    }

    private static void showHelp() {
        System.out.println("Usage: copybook-parser [OPTIONS] <input-file>");
        System.out.println();
        System.out.println("Parse COBOL copybook files and convert to JSON format for fixed-length file processing");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <input-file>              Input copybook file path");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o, --output <file>       Output JSON file path (default: <input>.json)");
        System.out.println("  -v, --verbose             Enable verbose output");
        System.out.println("  -p, --pretty              Pretty print JSON output (default: true)");
        System.out.println("      --no-pretty           Disable pretty printing");
        System.out.println("  -h, --help                Show this help message and exit");
        System.out.println("  -V, --version             Show version information and exit");
        System.out.println();
        System.out.println("Note: 88-level condition names are automatically excluded from output");
        System.out.println("      as they don't represent physical data positions in fixed-length files.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  copybook-parser customer.cbl");
        System.out.println("  copybook-parser customer.cbl -o output.json");
        System.out.println("  copybook-parser customer.cbl -v");
    }

    private static void showUsage() {
        System.out.println("Usage: copybook-parser [OPTIONS] <input-file>");
        System.out.println("Try 'copybook-parser --help' for more information.");
    }

    private static void showVersion() {
        System.out.println("COBOL Copybook Parser v1.0.0 (Fixed-Length File Optimized)");
        System.out.println("Java " + System.getProperty("java.version"));
    }

    private static int countFields(java.util.List<CobolField> fields) {
        int count = 0;
        for (CobolField field : fields) {
            count++;
            count += countFields(field.getChildren());
        }
        return count;
    }

    // Inner class to hold CLI options
    private static class CliOptions {
        String inputFile;
        String outputFile;
        boolean verbose = false;
        boolean prettyPrint = true;
        boolean showHelp = false;
        boolean showVersion = false;
    }
}
