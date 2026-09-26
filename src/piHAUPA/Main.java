package piHAUPA;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        CliOptions options = CliOptions.parse(args);
        if (options.help) {
            printHelp();
            return;
        }

        InputData input = loadInput(options);
        ComparisonReport comparison = ComparisonReport.run(input);
        AnalysisReport report = comparison.tight();
        printReport(input, report, options.summaryOnly);

        if (options.outputDirectory != null) {
            InputWriter.writeCombined(input, options.outputDirectory.resolve("pihaupa-input.txt"));
            if (input.batches().size() == 3) {
                InputWriter.writeThreeSplitFiles(input, options.outputDirectory.resolve("splits"));
            }
            ReportWriter.writeAll(report, options.outputDirectory);
            ReportWriter.writeComparison(comparison, options.outputDirectory);
            System.out.println("Da xuat ket qua: " + options.outputDirectory.toAbsolutePath());
        }
    }

    private static InputData loadInput(CliOptions options) throws IOException {
        if (options.inputPath == null) {
            return overrideThresholds(InputParser.parseBuiltInSample(), options);
        }

        return overrideThresholds(InputParser.parse(options.inputPath), options);
    }

    private static InputData overrideThresholds(InputData input, CliOptions options) {
        double upper = Double.isNaN(options.upperThreshold) ? input.upperThreshold() : options.upperThreshold;
        double lower = Double.isNaN(options.lowerThreshold) ? input.lowerThreshold() : options.lowerThreshold;
        if (!(lower > 0.0 && lower < upper && upper <= 1.0)) {
            throw new IllegalArgumentException("Thresholds must satisfy 0 < Sl < Su <= 1.");
        }
        return new InputData(upper, lower, input.externalUtilities(), input.batches());
    }

    private static void printReport(InputData input, AnalysisReport report, boolean summaryOnly) {
        System.out.printf(Locale.US, "PIHAUPA - Su=%.6f, Sl=%.6f%n",
                report.upperThreshold(), report.lowerThreshold());
        System.out.printf("Batches: %d, transactions: %d, external items: %d%n%n",
                report.batchCount(), report.transactionCount(), input.externalUtilities().size());

        for (AnalysisReport.BatchView batch : report.batches()) {
            if (summaryOnly) {
                printBatchSummary(batch);
            } else {
                printBatch(batch, report.upperThreshold(), report.lowerThreshold());
            }
        }

        MiningStats stats = report.stats();
        System.out.println("=".repeat(88));
        System.out.println("TONG KET");
        System.out.printf(Locale.US, "Tong thoi gian: %.3f ms%n", report.elapsedNanos() / 1_000_000.0);
        System.out.printf(Locale.US, "Heap tang them: %.3f MB, heap dinh quan sat: %.3f MB%n",
                report.memoryDeltaBytes() / (1024.0 * 1024.0), report.peakMemoryBytes() / (1024.0 * 1024.0));
        System.out.printf(Locale.US, "Tong TU xu ly: %.3f, tong MU xu ly: %.3f%n",
                stats.totalTransactionUtilityProcessed(), stats.totalMaximumUtilityProcessed());
        System.out.printf("So lan re-scan: %d, pattern duyet: %d, node ket hop: %d%n",
                stats.rescanCount(), stats.patternsVisited(), stats.combinedNodes());
        System.out.printf("Pattern-tree entries: %d%n", report.managedPatterns().size());
    }

    private static void printBatch(AnalysisReport.BatchView batch, double upperRatio, double lowerRatio) {
        System.out.println("=".repeat(88));
        System.out.printf("Batch %d - %s%n", batch.index(), batch.name());
        System.out.printf(Locale.US, "Rows: %d (cumulative=%d), batchTU=%.3f, batchMU=%.3f%n",
                batch.transactionCount(), batch.cumulativeTransactionCount(),
                batch.batchTransactionUtility(), batch.batchMaximumUtility());
        System.out.printf(Locale.US,
                "Pattern thresholds: LARGE AU >= %.3f; PRE-LARGE %.3f <= AU < %.3f%n",
                batch.minUtilUpper(), batch.minUtilLower(), batch.minUtilUpper());
        System.out.println("Re-scan condition:");
        if (batch.tightRescanLimit() != null) {
            double originalTU = batch.totalTransactionUtility() - batch.accumulatedTransactionUtility();
            double left = batch.accumulatedMaximumUtility() - upperRatio * batch.accumulatedTransactionUtility();
            double right = (upperRatio - lowerRatio) * originalTU;
            System.out.printf(Locale.US,
                    "  ID since last full scan: TU=%.3f, MU=%.3f; OD at last full scan: TU=%.3f%n",
                    batch.accumulatedTransactionUtility(), batch.accumulatedMaximumUtility(), originalTU);
            System.out.printf(Locale.US,
                    "  Tight Eq.(6), stable form: MU(ID) - Su*TU(ID) >= (Su-Sl)*TU(OD)%n"
                            + "  %.6f %s %.6f -> %s%n",
                    left, batch.rescanTriggered() ? ">=" : "<", right,
                    batch.rescanTriggered() ? "RE-SCAN" : "UPDATE EXISTING TREE");
            if (batch.tightRescanDenominator() > 0.0) {
                System.out.printf(Locale.US, "  Divided form: MU(ID)=%.3f, limit=%.3f%n",
                        batch.accumulatedMaximumUtility(), batch.tightRescanLimit());
            } else {
                System.out.println("  Divided form unavailable (denominator <= 0); stable comparison used.");
            }
            System.out.printf(Locale.US,
                    "  Original Eq.(5), comparison only: TU(ID)=%.3f, limit=%.3f => %s%n",
                    batch.accumulatedTransactionUtility(), batch.originalRescanLimit(),
                    batch.accumulatedTransactionUtility() >= batch.originalRescanLimit()
                            ? "WOULD RE-SCAN" : "WOULD UPDATE");
        } else {
            System.out.println("  Initial DB0 scan; re-scan condition is not applied.");
        }
        System.out.printf("Action: %s%n", batch.action());
        System.out.printf(Locale.US, "TotalTU=%.3f, minUtilUpper=%.3f, minUtilLower=%.3f%n",
                batch.totalTransactionUtility(), batch.minUtilUpper(), batch.minUtilLower());
        System.out.printf(Locale.US, "Time=%.3f ms, heap before/after/peak=%.3f/%.3f/%.3f MB%n",
                batch.elapsedMs(), batch.memoryBeforeMb(), batch.memoryAfterMb(), batch.peakMemoryMb());
        printPatterns("HAUP/LARGE", batch.largePatterns());
        printPatterns("PRE-LARGE", batch.preLargePatterns());
    }

    private static void printBatchSummary(AnalysisReport.BatchView batch) {
        System.out.printf(Locale.US,
                "Batch %d %-8s rows=%d cumulative=%d action=%s time=%.3fms heap=%.3fMB peak=%.3fMB "
                        + "TU=%.3f MU=%.3f large=%d preLarge=%d%n",
                batch.index(), batch.name(), batch.transactionCount(), batch.cumulativeTransactionCount(),
                batch.action(), batch.elapsedMs(), batch.memoryAfterMb(), batch.peakMemoryMb(),
                batch.batchTransactionUtility(), batch.batchMaximumUtility(),
                batch.largePatterns().size(), batch.preLargePatterns().size());
    }

    private static void printPatterns(String title, List<PatternResult> patterns) {
        System.out.printf("%s (%d)%n", title, patterns.size());
        if (patterns.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        for (PatternResult pattern : patterns) {
            System.out.printf(Locale.US, "  %-24s AU=%12.3f sumU=%12.3f length=%d%n",
                    pattern.displayName(), pattern.averageUtility(), pattern.sumUtility(), pattern.length());
        }
    }

    private static void printHelp() {
        System.out.println("""
                PIHAUPA Java
                  java -cp out piHAUPA.Main [options] [input-file]

                Options:
                  --input FILE          Read PIHAUPA text input
                  --su VALUE            Override upper threshold ratio
                  --sl VALUE            Override lower threshold ratio
                  --output-dir DIR      Export input, DB0-DB2, JSON and CSV reports
                  --summary             Print compact per-row-count performance statistics
                  --help                Show this help

                With no input file, the program runs Tables 2-3 of the paper with
                Su=0.23 and Sl=0.10. Command-line --su/--sl override file values.
                """);
    }

    private static final class CliOptions {
        private Path inputPath;
        private Path outputDirectory;
        private boolean summaryOnly;
        private boolean help;
        private double upperThreshold = Double.NaN;
        private double lowerThreshold = Double.NaN;

        private static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--summary" -> options.summaryOnly = true;
                    case "--help", "-h" -> options.help = true;
                    case "--input" -> options.inputPath = Path.of(requireValue(args, ++i, arg));
                    case "--output-dir" -> options.outputDirectory = Path.of(requireValue(args, ++i, arg));
                    case "--su" -> options.upperThreshold = Double.parseDouble(requireValue(args, ++i, arg));
                    case "--sl" -> options.lowerThreshold = Double.parseDouble(requireValue(args, ++i, arg));
                    default -> {
                        if (arg.startsWith("--")) {
                            throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                        if (options.inputPath != null) {
                            throw new IllegalArgumentException("Only one input file is allowed.");
                        }
                        options.inputPath = Path.of(arg);
                    }
                }
            }
            return options;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }
    }
}
