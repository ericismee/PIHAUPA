package piHAUPA;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

public final class ExportBundleTest {
    private ExportBundleTest() { }

    public static void main(String[] args) throws Exception {
        InputData input = InputParser.parseBuiltInSample();
        ComparisonReport comparison = ComparisonReport.run(input);
        Path output = Files.createTempDirectory("pihaupa-test-export-");
        try {
            ExportBundle.writeDirectory(input, comparison, output, "paper-example.txt");
            for (String name : Set.of("BAO_CAO.txt", "bao-cao.csv", "pihaupa-input.txt",
                    "patterns.csv", "performance-by-rows.csv", "comparison.csv")) {
                require(Files.isRegularFile(output.resolve(name)), "Missing export file: " + name);
            }
            String txt = Files.readString(output.resolve("BAO_CAO.txt"), StandardCharsets.UTF_8);
            require(txt.contains("84.000000") && txt.contains("75.000000") && txt.contains("63.500000"),
                    "Paper final AU values must appear in TXT.");
            require(txt.contains("Re-scan Eq.(12)") && txt.contains("TU(ID)"),
                    "TXT must explain re-scan decision.");
            require(txt.contains("Original=") && txt.contains("Tight="),
                    "TXT must compare both policies.");
            String csv = Files.readString(output.resolve("bao-cao.csv"), StandardCharsets.UTF_8);
            require(csv.contains("tightEq12Left") && csv.contains("DB2")
                    && csv.contains("84.000000"), "CSV must include formulas and final AU.");
            require(!Files.exists(output.resolve("BAO_CAO.html")), "Export must not create HTML.");
            require(!Files.exists(output.resolve("report.json")), "Export must not create JSON.");
            try {
                InputParser.parseText("72157607317745768 1808093328 277 159 218 294 1 1\n");
                throw new AssertionError("PIPA metadata must be rejected.");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("PIPA all_data.txt"),
                        "PIPA error must explain the incompatible dataset.");
            }
            System.out.println("Export and input-format tests passed.");
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
