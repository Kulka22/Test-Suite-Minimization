package minimizer.pitest;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinimizedUniqueIdRunner {
    private static final Pattern CLASS_IN_UNIQUE_ID = Pattern.compile("\\[class:([^\\]]+)]");
    private static final Pattern RUNNER_IN_UNIQUE_ID = Pattern.compile("\\[runner:([^\\]]+)]");
    private static final Pattern TEST_OWNER_IN_UNIQUE_ID = Pattern.compile("\\[test:[^\\(\\]]+\\(([^\\)]+)\\)\\]");

    private static final Object CACHE_LOCK = new Object();
    private static Path cachedIdsFile;
    private static Map<String, List<String>> cachedIdsByClass;

    private MinimizedUniqueIdRunner() {
    }

    public static void runSelectedIdsForClass(String testClassName) throws Exception {
        if (testClassName == null || testClassName.trim().isEmpty()) {
            throw new IllegalArgumentException("testClassName is empty");
        }

        Path idsFile = resolveIdsFile();
        Map<String, List<String>> idsByClass = loadIdsByClass(idsFile);
        List<String> selectedIds = idsByClass.get(testClassName);
        if (selectedIds == null || selectedIds.isEmpty()) {
            throw new IllegalStateException("No selected unique IDs found for class " + testClassName + " in " + idsFile);
        }

        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request()
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false");
        for (String uniqueId : selectedIds) {
            builder.selectors(DiscoverySelectors.selectUniqueId(uniqueId));
        }

        LauncherDiscoveryRequest request = builder.build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        if (summary.getTestsFoundCount() == 0) {
            throw new IllegalStateException("Selected IDs were not discovered for class " + testClassName + ": " + selectedIds.size());
        }
        if (summary.getTestsFailedCount() > 0 || summary.getTestsAbortedCount() > 0) {
            throw new IllegalStateException(buildFailureMessage(testClassName, summary));
        }
    }

    private static Map<String, List<String>> loadIdsByClass(Path idsFile) throws Exception {
        synchronized (CACHE_LOCK) {
            if (cachedIdsByClass != null && idsFile.equals(cachedIdsFile)) {
                return cachedIdsByClass;
            }

            List<String> lines = Files.readAllLines(idsFile, StandardCharsets.UTF_8);
            Map<String, List<String>> byClass = new LinkedHashMap<>();
            for (String line : lines) {
                String trimmed = normalizeUniqueId(line);
                if (trimmed.isEmpty()) {
                    continue;
                }
                String className = extractClassName(trimmed);
                if (className == null) {
                    continue;
                }
                byClass.computeIfAbsent(className, ignored -> new ArrayList<>()).add(trimmed);
            }

            Map<String, List<String>> immutableByClass = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : byClass.entrySet()) {
                immutableByClass.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            cachedIdsByClass = Collections.unmodifiableMap(immutableByClass);
            cachedIdsFile = idsFile;
            return cachedIdsByClass;
        }
    }

    private static String buildFailureMessage(String testClassName, TestExecutionSummary summary) {
        StringBuilder message = new StringBuilder();
        message.append("Selected tests failed for class ").append(testClassName)
            .append(": failed=").append(summary.getTestsFailedCount())
            .append(", aborted=").append(summary.getTestsAbortedCount());
        List<TestExecutionSummary.Failure> failures = summary.getFailures();
        if (!failures.isEmpty()) {
            TestExecutionSummary.Failure first = failures.get(0);
            message.append(". First failure: ").append(first.getTestIdentifier().getDisplayName());
            if (first.getException() != null && first.getException().getMessage() != null) {
                message.append(" - ").append(first.getException().getMessage());
            }
        }
        return message.toString();
    }

    private static Path resolveIdsFile() {
        String explicit = System.getProperty("minimizer.pit.selectedIdsFile", "").trim();
        if (!explicit.isEmpty()) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }
        String algorithm = resolveAlgorithm(System.getProperty("minimizer.algorithm", "PBE"));
        Path root = Paths.get(System.getProperty("minimizer.outputDir", "target/minimizer"))
            .toAbsolutePath()
            .normalize();
        String algorithmDir = algorithmDir(algorithm);
        return root.resolve(algorithmDir).resolve("selected-test-ids.txt");
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
        Matcher classMatcher = CLASS_IN_UNIQUE_ID.matcher(uniqueId);
        if (classMatcher.find()) {
            return classMatcher.group(1);
        }
        Matcher runnerMatcher = RUNNER_IN_UNIQUE_ID.matcher(uniqueId);
        if (runnerMatcher.find()) {
            return runnerMatcher.group(1);
        }
        Matcher ownerMatcher = TEST_OWNER_IN_UNIQUE_ID.matcher(uniqueId);
        if (ownerMatcher.find()) {
            return ownerMatcher.group(1);
        }
        return null;
    }

    private static String normalizeUniqueId(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        String cleaned = rawLine;
        if (!cleaned.isEmpty() && cleaned.charAt(0) == '\uFEFF') {
            cleaned = cleaned.substring(1);
        }
        return cleaned.trim();
    }
}
