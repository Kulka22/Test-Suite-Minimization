package minimizer.coverage;

import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class JunitSuiteRunner {
    private static final String DEFAULT_CLASS_PATTERN = "^(Test.*|.+[.$]Test.*|.*Tests?)$";

    public Path runFullSuite(Path outputDir, String classNamePattern) throws IOException {
        PathUtils.recreateDirectory(outputDir);
        List<String> selectedClasses = findTestClasses(resolvePattern(classNamePattern));
        if (selectedClasses.isEmpty()) {
            throw new IllegalStateException("No test classes matched class pattern: " + resolvePattern(classNamePattern));
        }
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request()
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
            .filters(ClassNameFilter.includeClassNamePatterns(resolvePattern(classNamePattern)));
        for (String className : selectedClasses) {
            builder.selectors(DiscoverySelectors.selectClass(className));
        }
        execute(builder.build(), outputDir);
        return outputDir.resolve(PerTestCoverageListener.TESTS_CSV_NAME);
    }

    public Path runSelectedTests(Path outputDir, List<String> uniqueTestIds) throws IOException {
        if (uniqueTestIds.isEmpty()) {
            throw new IllegalArgumentException("selected test id list is empty");
        }
        PathUtils.recreateDirectory(outputDir);
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request()
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false");
        for (String uniqueTestId : uniqueTestIds) {
            builder.selectors(DiscoverySelectors.selectUniqueId(uniqueTestId));
        }
        execute(builder.build(), outputDir);
        return outputDir.resolve(PerTestCoverageListener.TESTS_CSV_NAME);
    }

    private void execute(LauncherDiscoveryRequest request, Path outputDir) throws IOException {
        Launcher launcher = LauncherFactory.create();
        try (PerTestCoverageListener listener = new PerTestCoverageListener(outputDir)) {
            launcher.registerTestExecutionListeners(listener);
            launcher.execute(request);
        }
    }

    private static Set<Path> findTestClasspathRoots() {
        String classpath = System.getProperty("java.class.path", "");
        String separator = System.getProperty("path.separator");
        Set<Path> roots = new LinkedHashSet<>();
        for (String entry : classpath.split(Pattern.quote(separator))) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            Path path = Paths.get(entry).toAbsolutePath().normalize();
            if (Files.isDirectory(path) && path.toString().contains("test-classes")) {
                roots.add(path);
            }
        }
        if (roots.isEmpty()) {
            Path fallback = Paths.get("target", "test-classes").toAbsolutePath().normalize();
            if (Files.isDirectory(fallback)) {
                roots.add(fallback);
            }
        }
        return roots;
    }

    private static List<String> findTestClasses(String classNamePattern) throws IOException {
        Pattern pattern = Pattern.compile(classNamePattern);
        List<String> testClasses = new ArrayList<>();
        for (Path root : findTestClasspathRoots()) {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String path = file.toString();
                    if (!path.endsWith(".class")) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path relative = root.relativize(file);
                    String className = relative.toString()
                        .replace('\\', '.')
                        .replace('/', '.')
                        .replaceAll("\\.class$", "");
                    if (className.contains("$")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if ("module-info".equals(className) || className.endsWith(".package-info")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (pattern.matcher(className).matches()) {
                        testClasses.add(className);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return testClasses;
    }

    public static List<String> readSelectedIds(Path idsFile) throws IOException {
        List<String> ids = new ArrayList<>();
        for (String line : Files.readAllLines(idsFile)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    private static String resolvePattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return DEFAULT_CLASS_PATTERN;
        }
        return pattern;
    }
}
