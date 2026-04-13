package minimizer.coverage;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class TestRunRecord {
    private final String uniqueTestId;
    private final String testName;
    private final TestStatus status;
    private final String execFilePath;

    public TestRunRecord(String uniqueTestId, String testName, TestStatus status, String execFilePath) {
        this.uniqueTestId = uniqueTestId;
        this.testName = testName;
        this.status = status;
        this.execFilePath = execFilePath == null ? "" : execFilePath;
    }

    public String uniqueTestId() {
        return uniqueTestId;
    }

    public String testName() {
        return testName;
    }

    public TestStatus status() {
        return status;
    }

    public String execFilePath() {
        return execFilePath;
    }

    public Path resolveExecPath(Path testsCsvPath) {
        if (execFilePath.isEmpty()) {
            return null;
        }
        Path execPath = Paths.get(execFilePath);
        if (execPath.isAbsolute()) {
            return execPath;
        }
        return testsCsvPath.getParent().resolve(execPath).normalize();
    }
}
