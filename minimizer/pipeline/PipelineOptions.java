package minimizer.pipeline;

import minimizer.coverage.CoverageMetric;
import minimizer.lp.LpConfig;
import minimizer.pbe.PbeConfig;

import java.nio.file.Path;
import java.nio.file.Paths;

final class PipelineOptions {
    private final PipelinePaths paths;
    private final Path classesDir;
    private final String classNamePattern;
    private final CoverageMetric coverageMetric;
    private final MinimizerAlgorithm minimizerAlgorithm;
    private final PbeConfig pbeConfig;
    private final LpConfig lpConfig;

    private PipelineOptions(
        PipelinePaths paths,
        Path classesDir,
        String classNamePattern,
        CoverageMetric coverageMetric,
        MinimizerAlgorithm minimizerAlgorithm,
        PbeConfig pbeConfig,
        LpConfig lpConfig
    ) {
        this.paths = paths;
        this.classesDir = classesDir;
        this.classNamePattern = classNamePattern;
        this.coverageMetric = coverageMetric;
        this.minimizerAlgorithm = minimizerAlgorithm;
        this.pbeConfig = pbeConfig;
        this.lpConfig = lpConfig;
    }

    static PipelineOptions fromSystemProperties() {
        PipelinePaths paths = PipelinePaths.fromSystemProperties();
        Path classesDir = Paths.get(System.getProperty("minimizer.classesDir", "target/classes"))
            .toAbsolutePath()
            .normalize();
        String classPattern = System.getProperty("minimizer.classPattern", "");
        String metricRaw = System.getProperty("minimizer.metric", "LINE").trim().toUpperCase();
        CoverageMetric metric = CoverageMetric.valueOf(metricRaw);
        MinimizerAlgorithm minimizerAlgorithm = MinimizerAlgorithm.fromProperty(
            System.getProperty("minimizer.algorithm", "PBE")
        );

        boolean withSubsetPreprocessing = Boolean.parseBoolean(
            System.getProperty("minimizer.pbe.enableSubsetPreprocessing", "false")
        );
        boolean withConstraintReduction = Boolean.parseBoolean(
            System.getProperty("minimizer.pbe.enableConstraintReduction", "true")
        );
        int timeoutSeconds = Integer.parseInt(
            System.getProperty("minimizer.pbe.timeoutSeconds", "600")
        );
        PbeConfig pbeConfig = new PbeConfig(withSubsetPreprocessing, withConstraintReduction, timeoutSeconds);

        boolean lpWithSubsetPreprocessing = Boolean.parseBoolean(
            System.getProperty("minimizer.lp.enableSubsetPreprocessing", "true")
        );
        boolean lpWithConstraintReduction = Boolean.parseBoolean(
            System.getProperty("minimizer.lp.enableConstraintReduction", "true")
        );
        int lpTimeoutSeconds = Integer.parseInt(
            System.getProperty("minimizer.lp.timeoutSeconds", "600")
        );
        LpConfig lpConfig = new LpConfig(lpWithSubsetPreprocessing, lpWithConstraintReduction, lpTimeoutSeconds);
        return new PipelineOptions(paths, classesDir, classPattern, metric, minimizerAlgorithm, pbeConfig, lpConfig);
    }

    PipelinePaths paths() {
        return paths;
    }

    Path classesDir() {
        return classesDir;
    }

    String classNamePattern() {
        return classNamePattern;
    }

    CoverageMetric coverageMetric() {
        return coverageMetric;
    }

    MinimizerAlgorithm minimizerAlgorithm() {
        return minimizerAlgorithm;
    }

    PbeConfig pbeConfig() {
        return pbeConfig;
    }

    LpConfig lpConfig() {
        return lpConfig;
    }
}
