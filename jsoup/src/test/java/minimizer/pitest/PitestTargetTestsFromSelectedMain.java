package minimizer.pitest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PitestTargetTestsFromSelectedMain {
    private static final Pattern CLASS_IN_UNIQUE_ID = Pattern.compile("\\[class:([^\\]]+)]");

    private PitestTargetTestsFromSelectedMain() {
    }

    public static void main(String[] args) throws Exception {
        String algorithm = resolveAlgorithm(System.getProperty("minimizer.algorithm", "PBE"));
        Path idsFile = resolveIdsFile(algorithm);
        Path outputFile = resolveOutputFile(algorithm);

        String includeRegex = System.getProperty("minimizer.pit.includeClassRegex", "").trim();
        Pattern includePattern = includeRegex.isEmpty() ? null : Pattern.compile(includeRegex);

        if (!Files.exists(idsFile)) {
            throw new IllegalStateException("selected-test-ids.txt file does not exist: " + idsFile);
        }

        List<String> lines = Files.readAllLines(idsFile, StandardCharsets.UTF_8);
        Set<String> selectedClasses = new LinkedHashSet<>();
        int nonEmptyIds = 0;
        int parsedClassIds = 0;
        int skippedInvalid = 0;
        int skippedByFilter = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            nonEmptyIds++;
            String className = extractClassName(trimmed);
            if (className == null) {
                skippedInvalid++;
                continue;
            }
            if (includePattern != null && !includePattern.matcher(className).matches()) {
                skippedByFilter++;
                continue;
            }
            selectedClasses.add(className);
            parsedClassIds++;
        }

        if (selectedClasses.isEmpty()) {
            throw new IllegalStateException(
                "No PIT target test classes extracted from selected IDs file: " + idsFile
            );
        }

        Files.createDirectories(outputFile.getParent());
        String targetTestsValue = String.join(",", selectedClasses);
        Files.write(outputFile, (targetTestsValue + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));

        System.out.println("Minimizer algorithm: " + algorithm);
        System.out.println("Selected IDs file: " + idsFile);
        System.out.println("Total non-empty selected IDs: " + nonEmptyIds);
        System.out.println("IDs with parsed class: " + parsedClassIds);
        System.out.println("Skipped IDs without [class:...]: " + skippedInvalid);
        if (includePattern != null) {
            System.out.println("Applied class include regex: " + includeRegex);
            System.out.println("Skipped IDs by include regex: " + skippedByFilter);
        }
        System.out.println("Unique selected test classes for PIT: " + selectedClasses.size());
        System.out.println("Output targetTests file: " + outputFile);
        System.out.println("Output targetTests value: " + targetTestsValue);
    }

    private static String resolveAlgorithm(String rawAlgorithm) {
        String normalized = rawAlgorithm == null ? "" : rawAlgorithm.trim().toUpperCase();
        if ("GA".equals(normalized)) {
            return "GENETIC";
        }
        if ("GR".equals(normalized) || "GREEDY".equals(normalized)) {
            return "GREEDY_ESSENTIAL";
        }
        if ("PBE".equals(normalized)
            || "LP".equals(normalized)
            || "NAIVE".equals(normalized)
            || "GREEDY_ESSENTIAL".equals(normalized)
            || "GENETIC".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException(
            "Unsupported minimizer.algorithm: " + rawAlgorithm
                + ". Expected PBE, LP, NAIVE, GREEDY_ESSENTIAL, GENETIC, GA or GR."
        );
    }

    private static Path resolveIdsFile(String algorithm) {
        String explicit = System.getProperty("minimizer.pit.selectedIdsFile", "").trim();
        if (!explicit.isEmpty()) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }

        Path outputRoot = Paths.get(System.getProperty("minimizer.outputDir", "target/minimizer"))
            .toAbsolutePath()
            .normalize();
        String algorithmDir = algorithmDir(algorithm);
        return outputRoot.resolve(algorithmDir).resolve("selected-test-ids.txt");
    }

    private static Path resolveOutputFile(String algorithm) {
        String explicit = System.getProperty("minimizer.pit.outputFile", "").trim();
        if (!explicit.isEmpty()) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }

        Path outputRoot = Paths.get(System.getProperty("minimizer.outputDir", "target/minimizer"))
            .toAbsolutePath()
            .normalize();
        String fileName = algorithmDir(algorithm) + "-target-tests.txt";
        return outputRoot.resolve("pit").resolve(fileName);
    }

    private static String algorithmDir(String algorithm) {
        if ("LP".equals(algorithm)) {
            return "lp";
        }
        if ("PBE".equals(algorithm)) {
            return "pbe";
        }
        if ("NAIVE".equals(algorithm)) {
            return "naive";
        }
        if ("GREEDY_ESSENTIAL".equals(algorithm)) {
            return "greedy-essential";
        }
        if ("GENETIC".equals(algorithm)) {
            return "genetic";
        }
        throw new IllegalArgumentException("Unsupported minimizer.algorithm: " + algorithm);
    }

    private static String extractClassName(String uniqueId) {
        Matcher matcher = CLASS_IN_UNIQUE_ID.matcher(uniqueId);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }
}
