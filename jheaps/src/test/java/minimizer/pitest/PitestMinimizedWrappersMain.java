package minimizer.pitest;

import javax.tools.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PitestMinimizedWrappersMain {
    private static final Pattern CLASS_IN_UNIQUE_ID = Pattern.compile("\\[class:([^\\]]+)]");
    private static final Pattern RUNNER_IN_UNIQUE_ID = Pattern.compile("\\[runner:([^\\]]+)]");
    private static final Pattern TEST_OWNER_IN_UNIQUE_ID = Pattern.compile("\\[test:[^\\(\\]]+\\(([^\\)]+)\\)\\]");

    private PitestMinimizedWrappersMain() {
    }

    public static void main(String[] args) throws Exception {
        String algorithm = resolveAlgorithm(System.getProperty("minimizer.algorithm", "PBE"));
        Path idsFile = resolveIdsFile(algorithm);
        Path outputFile = resolveOutputFile(algorithm);
        Path generatedSourcesRoot = resolveGeneratedSourcesRoot(algorithm);
        Path generatedClassesDir = resolveGeneratedClassesDir();

        String includeRegex = System.getProperty("minimizer.pit.includeClassRegex", "").trim();
        Pattern includePattern = includeRegex.isEmpty() ? null : Pattern.compile(includeRegex);
        String wrapperSuffix = resolveWrapperSuffix(System.getProperty("minimizer.pit.wrapperSuffix", "Min"));

        if (!Files.exists(idsFile)) {
            throw new IllegalStateException("selected IDs file does not exist: " + idsFile);
        }

        List<String> lines = Files.readAllLines(idsFile, StandardCharsets.UTF_8);
        Set<String> selectedClasses = extractClasses(lines, includePattern);
        if (selectedClasses.isEmpty()) {
            throw new IllegalStateException("No selected classes extracted from IDs file: " + idsFile);
        }

        recreateDirectory(generatedSourcesRoot);
        Files.createDirectories(generatedClassesDir);

        Map<String, Path> wrapperToSource = new LinkedHashMap<>();
        for (String originalClass : selectedClasses) {
            String wrapperClass = toWrapperClassName(originalClass, wrapperSuffix);
            Path sourceFile = writeWrapperSource(generatedSourcesRoot, wrapperClass, originalClass);
            wrapperToSource.put(wrapperClass, sourceFile);
        }

        compileSources(new ArrayList<>(wrapperToSource.values()), generatedClassesDir);

        Files.createDirectories(outputFile.getParent());
        String targetTestsCsv = String.join(",", wrapperToSource.keySet());
        Files.write(outputFile, (targetTestsCsv + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));

        System.out.println("Minimizer algorithm: " + algorithm);
        System.out.println("Selected IDs file: " + idsFile);
        System.out.println("Selected original test classes: " + selectedClasses.size());
        if (includePattern != null) {
            System.out.println("Applied class include regex: " + includeRegex);
        }
        System.out.println("Generated wrappers: " + wrapperToSource.size());
        System.out.println("Generated source root: " + generatedSourcesRoot);
        System.out.println("Generated classes dir: " + generatedClassesDir);
        System.out.println("PIT targetTests file: " + outputFile);
        System.out.println("PIT targetTests value: " + targetTestsCsv);
    }

    private static Set<String> extractClasses(List<String> ids, Pattern includePattern) {
        Set<String> classes = new LinkedHashSet<>();
        for (String line : ids) {
            String trimmed = normalizeUniqueId(line);
            if (trimmed.isEmpty()) {
                continue;
            }
            String className = extractClassName(trimmed);
            if (className == null) {
                continue;
            }
            if (includePattern != null && !includePattern.matcher(className).matches()) {
                continue;
            }
            classes.add(className);
        }
        return classes;
    }

    private static Path writeWrapperSource(Path sourceRoot, String wrapperClassFqn, String originalClassFqn) throws Exception {
        int lastDot = wrapperClassFqn.lastIndexOf('.');
        String packageName = lastDot >= 0 ? wrapperClassFqn.substring(0, lastDot) : "";
        String simpleName = lastDot >= 0 ? wrapperClassFqn.substring(lastDot + 1) : wrapperClassFqn;

        Path packageDir = sourceRoot;
        if (!packageName.isEmpty()) {
            packageDir = sourceRoot.resolve(packageName.replace('.', File.separatorChar));
        }
        Files.createDirectories(packageDir);

        Path file = packageDir.resolve(simpleName + ".java");
        StringBuilder src = new StringBuilder();
        if (!packageName.isEmpty()) {
            src.append("package ").append(packageName).append(";\n\n");
        }
        src.append("import minimizer.pitest.MinimizedUniqueIdRunner;\n");
        src.append("import org.junit.jupiter.api.Test;\n\n");
        src.append("public class ").append(simpleName).append(" {\n");
        src.append("    @Test\n");
        src.append("    void runMinimizedSelection() throws Exception {\n");
        src.append("        MinimizedUniqueIdRunner.runSelectedIdsForClass(\"").append(originalClassFqn).append("\");\n");
        src.append("    }\n");
        src.append("}\n");

        Files.write(file, src.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static void compileSources(List<Path> sourceFiles, Path classesDir) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("System Java compiler is unavailable. Run with a JDK (not JRE).");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                fileManager.getJavaFileObjectsFromFiles(toFiles(sourceFiles));
            List<String> options = new ArrayList<>();
            options.add("-source");
            options.add("1.8");
            options.add("-target");
            options.add("1.8");
            options.add("-classpath");
            options.add(buildCompileClasspath());
            options.add("-d");
            options.add(classesDir.toAbsolutePath().normalize().toString());
            Boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (ok == null || !ok) {
                StringBuilder error = new StringBuilder("Failed to compile generated PIT wrapper classes.");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    error.append(System.lineSeparator())
                        .append(diagnostic.getKind())
                        .append(": ")
                        .append(diagnostic.getMessage(null));
                }
                throw new IllegalStateException(error.toString());
            }
        }
    }

    private static List<File> toFiles(List<Path> paths) {
        List<File> files = new ArrayList<>(paths.size());
        for (Path path : paths) {
            files.add(path.toFile());
        }
        return files;
    }

    private static String buildCompileClasspath() {
        Set<String> entries = new LinkedHashSet<>();
        addClasspathEntries(entries, System.getProperty("java.class.path", ""));

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) contextClassLoader;
            for (URL url : urlClassLoader.getURLs()) {
                if (!"file".equalsIgnoreCase(url.getProtocol())) {
                    continue;
                }
                try {
                    entries.add(Paths.get(url.toURI()).toAbsolutePath().normalize().toString());
                } catch (Exception ignored) {
                }
            }
        }

        return String.join(System.getProperty("path.separator"), entries);
    }

    private static void addClasspathEntries(Set<String> entries, String rawClasspath) {
        if (rawClasspath == null || rawClasspath.trim().isEmpty()) {
            return;
        }
        String separator = System.getProperty("path.separator");
        for (String entry : rawClasspath.split(Pattern.quote(separator))) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            entries.add(entry.trim());
        }
    }

    private static void recreateDirectory(Path dir) throws Exception {
        if (Files.exists(dir)) {
            List<Path> paths = new ArrayList<>();
            Files.walk(dir).forEach(paths::add);
            paths.sort(Comparator.reverseOrder());
            for (Path path : paths) {
                Files.delete(path);
            }
        }
        Files.createDirectories(dir);
    }

    private static String toWrapperClassName(String originalClassFqn, String wrapperSuffix) {
        int lastDot = originalClassFqn.lastIndexOf('.');
        String packageName = lastDot >= 0 ? originalClassFqn.substring(0, lastDot) : "";
        String simpleName = lastDot >= 0 ? originalClassFqn.substring(lastDot + 1) : originalClassFqn;
        String safeSimpleName = toJavaIdentifier(simpleName);
        String wrapperSimple = safeSimpleName + wrapperSuffix;
        if (packageName.isEmpty()) {
            return wrapperSimple;
        }
        return packageName + "." + wrapperSimple;
    }

    private static String toJavaIdentifier(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "GeneratedMinTest";
        }
        StringBuilder result = new StringBuilder(raw.length() + 4);
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (i == 0) {
                result.append(Character.isJavaIdentifierStart(ch) ? ch : '_');
            } else {
                result.append(Character.isJavaIdentifierPart(ch) ? ch : '_');
            }
        }
        return result.toString();
    }

    private static String resolveWrapperSuffix(String rawSuffix) {
        String suffix = rawSuffix == null ? "" : rawSuffix.trim();
        if (suffix.isEmpty()) {
            suffix = "Min";
        }
        return toJavaIdentifier(suffix);
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
        String fileName = algorithmDir(algorithm) + "-target-tests-minwrappers.txt";
        return outputRoot.resolve("pit").resolve(fileName);
    }

    private static Path resolveGeneratedSourcesRoot(String algorithm) {
        String explicit = System.getProperty("minimizer.pit.generatedSourcesDir", "").trim();
        if (!explicit.isEmpty()) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }
        Path outputRoot = Paths.get(System.getProperty("minimizer.outputDir", "target/minimizer"))
            .toAbsolutePath()
            .normalize();
        String algorithmDir = algorithmDir(algorithm);
        return outputRoot.resolve("pit").resolve("generated-src").resolve(algorithmDir);
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

    private static Path resolveGeneratedClassesDir() {
        String explicit = System.getProperty("minimizer.pit.generatedClassesDir", "").trim();
        if (!explicit.isEmpty()) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }
        return Paths.get("target", "test-classes").toAbsolutePath().normalize();
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
