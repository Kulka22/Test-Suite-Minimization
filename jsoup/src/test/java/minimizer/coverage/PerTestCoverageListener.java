package minimizer.coverage;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class PerTestCoverageListener implements TestExecutionListener, AutoCloseable {
    static final String EXEC_DIR_NAME = "exec";
    static final String TESTS_CSV_NAME = "tests.csv";

    private final Path outputDir;
    private final Path execDir;
    private final JacocoAgentClient jacoco;
    private final AtomicInteger seq = new AtomicInteger(0);
    private final List<TestRunRecord> records = Collections.synchronizedList(new ArrayList<>());

    PerTestCoverageListener(Path outputDir) throws IOException {
        this.outputDir = outputDir;
        this.execDir = outputDir.resolve(EXEC_DIR_NAME);
        Files.createDirectories(this.execDir);
        this.jacoco = JacocoAgentClient.connect();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            jacoco.reset();
        }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (!testIdentifier.isTest()) {
            return;
        }
        records.add(new TestRunRecord(
            testIdentifier.getUniqueId(),
            describe(testIdentifier),
            TestStatus.SKIPPED,
            ""
        ));
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!testIdentifier.isTest()) {
            return;
        }
        TestStatus status = toStatus(testExecutionResult.getStatus());
        String execRelativePath = "";
        if (status != TestStatus.SKIPPED) {
            byte[] data = jacoco.dumpExecutionData(true);
            int id = seq.incrementAndGet();
            Path execFile = execDir.resolve(String.format("test-%05d.exec", id));
            try {
                Files.write(execFile, data);
                execRelativePath = outputDir.relativize(execFile).toString().replace('\\', '/');
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write per-test coverage file: " + execFile, e);
            }
        }
        records.add(new TestRunRecord(
            testIdentifier.getUniqueId(),
            describe(testIdentifier),
            status,
            execRelativePath
        ));
    }

    @Override
    public void close() throws IOException {
        TestsCsv.write(outputDir.resolve(TESTS_CSV_NAME), new ArrayList<>(records));
    }

    private static TestStatus toStatus(TestExecutionResult.Status status) {
        if (status == TestExecutionResult.Status.SUCCESSFUL) {
            return TestStatus.SUCCESS;
        }
        if (status == TestExecutionResult.Status.ABORTED) {
            return TestStatus.ABORTED;
        }
        return TestStatus.FAILED;
    }

    private static String describe(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
            .map(PerTestCoverageListener::formatSource)
            .orElse(testIdentifier.getDisplayName());
    }

    private static String formatSource(TestSource source) {
        if (source instanceof MethodSource) {
            MethodSource methodSource = (MethodSource) source;
            return methodSource.getClassName() + "#" + methodSource.getMethodName();
        }
        if (source instanceof ClassSource) {
            ClassSource classSource = (ClassSource) source;
            return classSource.getClassName();
        }
        return source.toString();
    }
}
