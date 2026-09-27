package piHAUPA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Human-readable TXT plus flat CSV; shared by desktop, CLI and web. */
public final class ExportBundle {
    private ExportBundle() { }

    public static void writeDirectory(InputData input, ComparisonReport comparison,
            Path directory, String sourceName) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("BAO_CAO.txt"), reportText(input, comparison, sourceName),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("bao-cao.csv"), reportCsv(input, comparison, sourceName),
                StandardCharsets.UTF_8);
        InputWriter.writeCombined(input, directory.resolve("pihaupa-input.txt"));
        Files.writeString(directory.resolve("patterns.csv"), ReportWriter.patternsCsv(comparison.tight()),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("performance-by-rows.csv"),
                ReportWriter.performanceCsv(comparison.tight()), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("comparison.csv"), ReportWriter.comparisonCsv(comparison),
                StandardCharsets.UTF_8);
    }

    public static String reportText(InputData input, ComparisonReport comparison, String sourceName) {
        AnalysisReport tight = comparison.tight();
        AnalysisReport original = comparison.original();
        AnalysisReport.BatchView last = tight.batches().get(tight.batches().size() - 1);
        StringBuilder out = new StringBuilder();
        out.append("BÁO CÁO PIHAUPA\n==============================\n")
                .append("Nguồn: ").append(safeSource(sourceName)).append('\n')
                .append("Giao dịch: ").append(tight.transactionCount()).append(" | Batch: ")
                .append(tight.batchCount()).append(" | Item: ").append(input.externalUtilities().size()).append('\n')
                .append("Su = ").append(n(input.upperThreshold())).append(" | Sl = ")
                .append(n(input.lowerThreshold())).append("\n\n")
                .append("1. KẾT LUẬN\n")
                .append("HAUP cuối Original/Tight: ").append(comparison.sameHaups() ? "TRÙNG KHỚP" : "KHÁC NHAU")
                .append("\nLARGE / HAUP cuối: ").append(last.largePatterns().size())
                .append(" | PRE-LARGE đang quản lý: ").append(last.preLargePatterns().size()).append('\n');
        patternList(out, last.largePatterns());
        out.append("\n2. CÔNG THỨC VÀ NGƯỠNG\n")
                .append("U(P,T) = tổng IU(i,T) × EU(i); AU(P) = sumU(P) / |P|.\n")
                .append("Upper = Su × TU(DB); Lower = Sl × TU(DB).\n")
                .append("LARGE nếu AU >= Upper; PRE-LARGE nếu Lower <= AU < Upper; còn lại SMALL.\n")
                .append("Tight re-scan: MU(ID) - Su×TU(ID) >= (Su-Sl)×TU(OD).\n")
                .append("OD: dữ liệu tại lần full scan gần nhất; ID: dữ liệu mới tích lũy.\n\n")
                .append("3. SO SÁNH THUẬT TOÁN\n");
        compare(out, "Re-scan", original.stats().rescanCount(), tight.stats().rescanCount(), "lần");
        compare(out, "Pattern duyệt", original.stats().patternsVisited(), tight.stats().patternsVisited(), "mẫu");
        compare(out, "Node kết hợp", original.stats().combinedNodes(), tight.stats().combinedNodes(), "node");
        compare(out, "Thời gian", original.elapsedNanos() / 1e6, tight.elapsedNanos() / 1e6, "ms");
        compare(out, "JVM heap peak", original.peakMemoryBytes() / 1048576.0,
                tight.peakMemoryBytes() / 1048576.0, "MB");
        out.append("\n4. EXTERNAL UTILITY\n");
        input.externalUtilities().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.append("  ").append(e.getKey()).append(" = ").append(n(e.getValue())).append('\n'));
        out.append("\n5. DIỄN TIẾN THEO BATCH (TIGHT)\n");
        for (AnalysisReport.BatchView batch : tight.batches()) {
            out.append("\n[").append(batch.name()).append("] ").append(batch.action()).append('\n')
                    .append("Giao dịch: ").append(batch.transactionCount())
                    .append(" | TU batch: ").append(n(batch.batchTransactionUtility()))
                    .append(" | MU batch: ").append(n(batch.batchMaximumUtility())).append('\n')
                    .append("TU tích lũy: ").append(n(batch.totalTransactionUtility()))
                    .append(" | Upper: ").append(n(batch.minUtilUpper()))
                    .append(" | Lower: ").append(n(batch.minUtilLower())).append('\n');
            if (batch.tightRescanLimit() != null) {
                double oldTu = batch.totalTransactionUtility() - batch.accumulatedTransactionUtility();
                double left = batch.accumulatedMaximumUtility()
                        - input.upperThreshold() * batch.accumulatedTransactionUtility();
                double right = (input.upperThreshold() - input.lowerThreshold()) * oldTu;
                out.append("Re-scan Eq.(12): ").append(n(left))
                        .append(batch.rescanTriggered() ? " >= " : " < ").append(n(right))
                        .append(" | TU(ID): ").append(n(batch.accumulatedTransactionUtility()))
                        .append(" | MU(ID): ").append(n(batch.accumulatedMaximumUtility()))
                        .append(" | TU(OD): ").append(n(oldTu)).append('\n');
            }
            out.append("Thời gian batch: ").append(n(batch.elapsedMs())).append(" ms")
                    .append(" | Heap sau batch: ").append(n(batch.memoryAfterMb())).append(" MB\n")
                    .append("LARGE (mẫu, AU, sumU, |P|):\n");
            patternList(out, batch.largePatterns());
            out.append("PRE-LARGE đang quản lý (mẫu, AU, sumU, |P|):\n");
            patternList(out, batch.preLargePatterns());
        }
        out.append("\nGHI CHÚ\n")
                .append("PRE-LARGE là ứng viên trong pattern tree; khi không re-scan có thể thiếu tập pre-large theo vét cạn.\n")
                .append("Thời gian và heap là phép đo lần chạy, có thể thay đổi giữa các lần chạy.\n")
                .append("pihaupa-input.txt chứa toàn bộ giao dịch và IU; các CSV chứa dữ liệu dạng bảng.\n");
        return out.toString();
    }

    public static String reportCsv(InputData input, ComparisonReport comparison, String sourceName) {
        AnalysisReport tight = comparison.tight();
        AnalysisReport original = comparison.original();
        StringBuilder csv = new StringBuilder("section,batch,metric,pattern,type,value,unit,originalEq5,tightEq6,description\n");
        row(csv, "input", "", "source", "", "", safeSource(sourceName), "", "", "", "Input file name");
        row(csv, "input", "", "Su", "", "", n(input.upperThreshold()), "ratio", "", "", "Upper threshold");
        row(csv, "input", "", "Sl", "", "", n(input.lowerThreshold()), "ratio", "", "", "Lower threshold");
        row(csv, "input", "", "transactionCount", "", "", Integer.toString(tight.transactionCount()), "rows", "", "", "All batches");
        for (Map.Entry<String, Double> e : input.externalUtilities().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            row(csv, "externalUtility", "", "EU", e.getKey(), "", n(e.getValue()), "utility", "", "", "Per item");
        }
        row(csv, "comparison", "", "sameFinalHaups", "", "", Boolean.toString(comparison.sameHaups()), "", "", "", "Final LARGE sets match");
        compareCsv(csv, "rescanCount", original.stats().rescanCount(), tight.stats().rescanCount(), "times");
        compareCsv(csv, "patternsVisited", original.stats().patternsVisited(), tight.stats().patternsVisited(), "patterns");
        compareCsv(csv, "combinedNodes", original.stats().combinedNodes(), tight.stats().combinedNodes(), "nodes");
        compareCsv(csv, "elapsedMs", original.elapsedNanos() / 1e6, tight.elapsedNanos() / 1e6, "ms");
        compareCsv(csv, "peakMemoryMb", original.peakMemoryBytes() / 1048576.0,
                tight.peakMemoryBytes() / 1048576.0, "MB");
        for (AnalysisReport.BatchView batch : tight.batches()) {
            String b = batch.name();
            row(csv, "batch", b, "transactionCount", "", "", Integer.toString(batch.transactionCount()), "rows", "", "", "Rows in batch");
            row(csv, "batch", b, "batchTU", "", "", n(batch.batchTransactionUtility()), "utility", "", "", "TU of batch");
            row(csv, "batch", b, "batchMU", "", "", n(batch.batchMaximumUtility()), "utility", "", "", "MU of batch");
            row(csv, "batch", b, "totalTU", "", "", n(batch.totalTransactionUtility()), "utility", "", "", "Cumulative TU");
            row(csv, "batch", b, "upper", "", "", n(batch.minUtilUpper()), "utility", "", "", "Su times total TU");
            row(csv, "batch", b, "lower", "", "", n(batch.minUtilLower()), "utility", "", "", "Sl times total TU");
            row(csv, "batch", b, "rescanTriggered", "", "", Boolean.toString(batch.rescanTriggered()), "", "", "", batch.action());
            if (batch.tightRescanLimit() != null) {
                double oldTu = batch.totalTransactionUtility() - batch.accumulatedTransactionUtility();
                double left = batch.accumulatedMaximumUtility()
                        - input.upperThreshold() * batch.accumulatedTransactionUtility();
                double right = (input.upperThreshold() - input.lowerThreshold()) * oldTu;
                row(csv, "batch", b, "tightEq12Left", "", "", n(left), "utility", "", "", "MU(ID)-Su*TU(ID)");
                row(csv, "batch", b, "tightEq12Right", "", "", n(right), "utility", "", "", "(Su-Sl)*TU(OD)");
            }
            row(csv, "batch", b, "elapsedMs", "", "", n(batch.elapsedMs()), "ms", "", "", "Observed batch time");
            row(csv, "batch", b, "memoryAfterMb", "", "", n(batch.memoryAfterMb()), "MB", "", "", "JVM heap after batch");
            patternsCsv(csv, b, "LARGE", batch.largePatterns());
            patternsCsv(csv, b, "PRE-LARGE", batch.preLargePatterns());
        }
        return csv.toString();
    }

    private static void patternsCsv(StringBuilder csv, String batch, String type, List<PatternResult> patterns) {
        for (PatternResult pattern : patterns) {
            String description = "sumU=" + n(pattern.sumUtility()) + "; length=" + pattern.length();
            row(csv, "pattern", batch, "AU", pattern.displayName(), type,
                    n(pattern.averageUtility()), "utility", "", "", description);
        }
    }

    private static void compareCsv(StringBuilder csv, String metric, double oldValue, double tightValue, String unit) {
        row(csv, "comparison", "", metric, "", "", "", unit, n(oldValue), n(tightValue), "Original Eq.(5) vs Tight Eq.(6)");
    }

    private static void row(StringBuilder out, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) out.append(',');
            out.append('"').append(cells[i].replace("\"", "\"\"")).append('"');
        }
        out.append('\n');
    }

    private static void patternList(StringBuilder out, List<PatternResult> patterns) {
        if (patterns.isEmpty()) {
            out.append("  (không có)\n");
            return;
        }
        for (PatternResult p : patterns) {
            out.append("  ").append(p.displayName()).append(" | AU=").append(n(p.averageUtility()))
                    .append(" | sumU=").append(n(p.sumUtility())).append(" | |P|=")
                    .append(p.length()).append('\n');
        }
    }

    private static void compare(StringBuilder out, String name, double oldValue, double tightValue, String unit) {
        out.append(name).append(": Original=").append(n(oldValue)).append(' ').append(unit)
                .append(" | Tight=").append(n(tightValue)).append(' ').append(unit).append('\n');
    }

    private static String n(double value) { return String.format(Locale.US, "%.6f", value); }

    private static String safeSource(String source) {
        String name = source == null ? "Dữ liệu chưa đặt tên" : source.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", " ").trim();
        return name.isEmpty() ? "Dữ liệu chưa đặt tên" : name.substring(0, Math.min(120, name.length()));
    }
}
