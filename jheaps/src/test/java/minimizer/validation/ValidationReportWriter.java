package minimizer.validation;

import minimizer.coverage.PathUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ValidationReportWriter {
    public static final String SUMMARY_FILE = "validation-summary.txt";
    public static final String MISSING_FILE = "missing-elements.txt";

    private ValidationReportWriter() {
    }

    public static void write(Path outputDir, CoverageValidationResult result) throws IOException {
        PathUtils.recreateDirectory(outputDir);
        writeSummary(outputDir.resolve(SUMMARY_FILE), result);
        writeMissing(outputDir.resolve(MISSING_FILE), result);
    }

    private static void writeSummary(Path file, CoverageValidationResult result) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("coverage_preserved=" + result.coveragePreserved());
        lines.add("baseline_elements=" + result.baselineElementCount());
        lines.add("selected_elements=" + result.selectedElementCount());
        lines.add("missing_elements_count=" + result.missingElementsCount());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void writeMissing(Path file, CoverageValidationResult result) throws IOException {
        Files.write(file, result.missingElements(), StandardCharsets.UTF_8);
    }
}
