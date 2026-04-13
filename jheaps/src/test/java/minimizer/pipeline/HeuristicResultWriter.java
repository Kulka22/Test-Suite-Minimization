package minimizer.pipeline;

import minimizer.coverage.CoverageMatrix;
import minimizer.coverage.CoverageMetric;
import minimizer.coverage.PathUtils;
import minimizer.coverage.TestRunRecord;
import minimizer.metrics.ResourceUsageSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class HeuristicResultWriter {
    static final String SELECTED_IDS_FILE = "selected-test-ids.txt";
    static final String SELECTED_TESTS_FILE = "selected-tests.csv";
    static final String SUMMARY_FILE = "heuristic-summary.txt";

    private HeuristicResultWriter() {
    }

    static void write(
        Path outputDir,
        HeuristicResult result,
        CoverageMatrix matrix,
        CoverageMetric metric,
        long matrixBuildMs,
        long solveMs,
        ResourceUsageSnapshot resourceSnapshot
    ) throws IOException {
        PathUtils.recreateDirectory(outputDir);
        writeIds(outputDir.resolve(SELECTED_IDS_FILE), result.selectedTestIds());
        writeSelectedTestsCsv(outputDir.resolve(SELECTED_TESTS_FILE), result.selectedTestIds(), matrix.tests());
        writeSummary(outputDir.resolve(SUMMARY_FILE), result, matrix, metric, matrixBuildMs, solveMs, resourceSnapshot);
    }

    private static void writeIds(Path file, List<String> selectedIds) throws IOException {
        Files.write(file, selectedIds, StandardCharsets.UTF_8);
    }

    private static void writeSelectedTestsCsv(Path file, List<String> selectedIds, List<TestRunRecord> allTests) throws IOException {
        Map<String, TestRunRecord> byId = new HashMap<>();
        for (TestRunRecord test : allTests) {
            byId.put(test.uniqueTestId(), test);
        }

        List<String> lines = new ArrayList<>();
        lines.add("unique_test_id,test_name,status");
        for (String id : selectedIds) {
            TestRunRecord record = byId.get(id);
            if (record != null) {
                lines.add(csv(record.uniqueTestId()) + "," + csv(record.testName()) + "," + record.status().name());
            }
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void writeSummary(
        Path file,
        HeuristicResult result,
        CoverageMatrix matrix,
        CoverageMetric metric,
        long matrixBuildMs,
        long solveMs,
        ResourceUsageSnapshot resourceSnapshot
    ) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("algorithm=" + result.algorithm().name());
        lines.add("algorithm_name=" + result.algorithmName());
        lines.add("coverage_metric=" + metric.name());
        lines.add("source_tests=" + matrix.testCount());
        lines.add("covered_elements=" + matrix.elementCount());
        lines.add("selected_tests=" + result.selectedTestIds().size());
        lines.add("reported_coverage_percent=" + formatDouble(result.coveragePercent()));
        lines.add("matrix_build_ms=" + matrixBuildMs);
        lines.add("solve_ms=" + solveMs);
        lines.add("resource_sampling_interval_ms=" + resourceSnapshot.sampleIntervalMs());
        lines.add("peak_heap_mb=" + formatDouble(resourceSnapshot.peakHeapUsedMb()));
        if (resourceSnapshot.hasPeakProcessCommittedBytes()) {
            lines.add("peak_process_memory_mb=" + formatDouble(resourceSnapshot.peakProcessCommittedMb()));
        } else {
            lines.add("peak_process_memory_mb=n/a");
        }
        if (resourceSnapshot.hasPeakProcessCpuPercent()) {
            lines.add("peak_cpu_percent=" + formatDouble(resourceSnapshot.peakProcessCpuPercent()));
        } else {
            lines.add("peak_cpu_percent=n/a");
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (!value.contains(",") && !value.contains("\"")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
