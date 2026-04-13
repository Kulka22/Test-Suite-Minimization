package minimizer.pipeline;

import org.jacoco.agent.rt.RT;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

final class JacocoJvmRelauncher {
    private static final String RELAUNCH_MARKER = "MINIMIZER_JACOCO_RELAUNCHED";

    private JacocoJvmRelauncher() {
    }

    static boolean alreadyRelaunched() {
        return "1".equals(System.getenv(RELAUNCH_MARKER));
    }

    static int relaunchWithJacocoAgent(Class<?> mainClass, String[] args) throws IOException, InterruptedException {
        String agentJar = findJacocoAgentJar();
        if (agentJar == null) {
            throw new IllegalStateException("Cannot find org.jacoco.agent runtime jar on classpath.");
        }

        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = buildClasspathForRelaunch();

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-javaagent:" + agentJar + "=output=none,dumponexit=false,includes=org.jsoup.*");
        addMinimizerSystemProperties(command, System.getProperties());
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass.getName());
        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put(RELAUNCH_MARKER, "1");
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        return process.waitFor();
    }

    private static void addMinimizerSystemProperties(List<String> command, Properties properties) {
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith("minimizer.")) {
                continue;
            }
            String value = properties.getProperty(name, "");
            command.add("-D" + name + "=" + value);
        }
    }

    private static String buildClasspathForRelaunch() {
        Set<String> entries = new LinkedHashSet<>();
        String systemClasspath = System.getProperty("java.class.path", "");
        addClasspathEntries(entries, systemClasspath);

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) contextClassLoader;
            for (URL url : urlClassLoader.getURLs()) {
                if (!"file".equalsIgnoreCase(url.getProtocol())) {
                    continue;
                }
                try {
                    Path entry = Paths.get(url.toURI()).toAbsolutePath().normalize();
                    entries.add(entry.toString());
                } catch (Exception ignored) {
                }
            }
        }
        return String.join(System.getProperty("path.separator"), entries);
    }

    private static void addClasspathEntries(Set<String> entries, String rawClasspath) {
        String separator = System.getProperty("path.separator");
        if (rawClasspath == null || rawClasspath.trim().isEmpty()) {
            return;
        }
        for (String entry : rawClasspath.split(java.util.regex.Pattern.quote(separator))) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            entries.add(entry.trim());
        }
    }

    private static String findJacocoAgentJar() {
        try {
            Path fromClass = Paths.get(RT.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath()
                .normalize();
            if (Files.isRegularFile(fromClass)) {
                return fromClass.toString();
            }
        } catch (Exception ignored) {
        }

        String classpath = System.getProperty("java.class.path", "");
        String separator = System.getProperty("path.separator");
        for (String entry : classpath.split(java.util.regex.Pattern.quote(separator))) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            Path path = Paths.get(entry);
            String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
            if (fileName.contains("org.jacoco.agent") && fileName.endsWith(".jar") && Files.isRegularFile(path)) {
                return path.toAbsolutePath().normalize().toString();
            }
        }
        return null;
    }
}
