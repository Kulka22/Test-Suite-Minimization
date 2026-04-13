package minimizer.coverage;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class CoverageAnalyzer {
    private final List<ClassBinary> classBinaries;
    private final CoverageMetric metric;

    CoverageAnalyzer(Path classesDir, CoverageMetric metric) {
        this.classBinaries = loadClassBinaries(classesDir);
        this.metric = metric;
    }

    Set<String> analyzeExec(Path execFile) throws IOException {
        if (execFile == null || !Files.exists(execFile) || Files.size(execFile) == 0L) {
            return Collections.emptySet();
        }

        ExecFileLoader loader = new ExecFileLoader();
        loader.load(execFile.toFile());

        CoverageBuilder builder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), builder);
        for (ClassBinary classBinary : classBinaries) {
            analyzer.analyzeClass(classBinary.bytes, classBinary.location);
        }

        return CoverageElementExtractor.extract(builder.getClasses(), metric);
    }

    private static List<ClassBinary> loadClassBinaries(Path classesDir) {
        List<ClassBinary> binaries = new ArrayList<>();
        try {
            Files.walkFileTree(classesDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.toString().endsWith(".class")) {
                        return FileVisitResult.CONTINUE;
                    }
                    String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                    if ("module-info.class".equals(fileName)) {
                        return FileVisitResult.CONTINUE;
                    }
                    byte[] bytes = Files.readAllBytes(file);
                    String location = classesDir.relativize(file).toString().replace('\\', '/');
                    binaries.add(new ClassBinary(bytes, location));
                    return FileVisitResult.CONTINUE;
                }
            });
            return binaries;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to preload class binaries from " + classesDir, ex);
        }
    }

    private static final class ClassBinary {
        private final byte[] bytes;
        private final String location;

        private ClassBinary(byte[] bytes, String location) {
            this.bytes = bytes;
            this.location = location;
        }
    }
}
