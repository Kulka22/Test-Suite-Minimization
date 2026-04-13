package minimizer.pipeline;

import minimizer.coverage.*;
import minimizer.lp.LpMinimizer;
import minimizer.lp.LpResult;
import minimizer.lp.LpResultWriter;
import minimizer.metrics.ResourceUsageSampler;
import minimizer.metrics.ResourceUsageSnapshot;
import minimizer.pbe.PbeMinimizer;
import minimizer.pbe.PbeResult;
import minimizer.pbe.PbeResultWriter;
import minimizer.validation.CoverageValidationResult;
import minimizer.validation.CoverageValidator;
import minimizer.validation.ValidationReportWriter;

import java.nio.file.Path;
import java.util.*;

public final class MinimizerPipelineMain {
    private static final long RESOURCE_SAMPLE_INTERVAL_MS = 200L;

    private MinimizerPipelineMain() {
    }

    public static void main(String[] args) throws Exception {
        boolean relaunchedChild = JacocoJvmRelauncher.alreadyRelaunched();
        String command = args.length == 0 ? "pipeline" : args[0].trim().toLowerCase();
        if (requiresCoverageCollection(command) && !JacocoAgentClient.isAgentActive() && !relaunchedChild) {
            int code = JacocoJvmRelauncher.relaunchWithJacocoAgent(MinimizerPipelineMain.class, args);
            if (code != 0) {
                throw new IllegalStateException("Relaunched JVM exited with code " + code);
            }
            return;
        }

        PipelineOptions options = PipelineOptions.fromSystemProperties();
        int exitCode = 0;
        try {
            MinimizerPipelineMain pipeline = new MinimizerPipelineMain();
            pipeline.run(command, options);
        } catch (Exception ex) {
            exitCode = 1;
            throw ex;
        } finally {
            if (relaunchedChild) {
                System.exit(exitCode);
            }
        }
    }

    private void run(String command, PipelineOptions options) throws Exception {
        switch (command) {
            case "collect-full":
                collectFull(options);
                break;
            case "minimize":
                minimize(options);
                break;
            case "collect-selected":
                collectSelected(options);
                break;
            case "validate":
                validate(options);
                break;
            case "pipeline":
                collectFull(options);
                minimize(options);
                collectSelected(options);
                validate(options);
                break;
            default:
                printUsage();
        }
    }

    private void collectFull(PipelineOptions options) throws Exception {
        ensureJacocoAgent("collect-full");
        JunitSuiteRunner runner = new JunitSuiteRunner();
        Path testsCsv = runner.runFullSuite(options.paths().fullRunDir(), options.classNamePattern());
        System.out.println("Full test run metadata: " + testsCsv);
    }

    private void minimize(PipelineOptions options) throws Exception {
        ResourceUsageSampler resourceSampler = ResourceUsageSampler.start(RESOURCE_SAMPLE_INTERVAL_MS);
        List<TestRunRecord> sourceRecords = TestsCsv.read(options.paths().fullTestsCsv());
        try {
            EnumMap<TestStatus, Integer> statusCount = new EnumMap<>(TestStatus.class);
            for (TestStatus status : TestStatus.values()) {
                statusCount.put(status, 0);
            }
            for (TestRunRecord record : sourceRecords) {
                statusCount.put(record.status(), statusCount.get(record.status()) + 1);
            }

            System.out.println("Minimize input tests: total=" + sourceRecords.size()
                + ", success=" + statusCount.get(TestStatus.SUCCESS)
                + ", failed=" + statusCount.get(TestStatus.FAILED)
                + ", aborted=" + statusCount.get(TestStatus.ABORTED)
                + ", skipped=" + statusCount.get(TestStatus.SKIPPED));
            System.out.println("Minimizer algorithm: " + options.minimizerAlgorithm());

            long matrixStart = System.nanoTime();
            CoverageMatrixBuilder builder = new CoverageMatrixBuilder();
            CoverageMatrix matrix = builder.build(
                options.paths().fullTestsCsv(),
                options.classesDir(),
                options.coverageMetric(),
                true
            );
            long matrixMillis = (System.nanoTime() - matrixStart) / 1_000_000L;

            System.out.println("Matrix size: tests=" + matrix.testCount() + ", elements=" + matrix.elementCount() + ", metric=" + options.coverageMetric());
            switch (options.minimizerAlgorithm()) {
                case LP:
                    minimizeWithLp(options, matrix, matrixMillis, resourceSampler);
                    return;
                case PBE:
                    minimizeWithPbe(options, matrix, matrixMillis, resourceSampler);
                    return;
                case NAIVE:
                case GREEDY_ESSENTIAL:
                case GENETIC:
                    minimizeWithHeuristic(options, matrix, matrixMillis, resourceSampler);
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported minimizer algorithm: " + options.minimizerAlgorithm());
            }
        } finally {
            resourceSampler.stopAndSnapshot();
        }
    }

    private void minimizeWithPbe(
        PipelineOptions options,
        CoverageMatrix matrix,
        long matrixMillis,
        ResourceUsageSampler resourceSampler
    ) throws Exception {
        long solverStart = System.nanoTime();
        PbeMinimizer minimizer = new PbeMinimizer();
        PbeResult result = minimizer.minimize(matrix, options.pbeConfig());
        long solverMillis = (System.nanoTime() - solverStart) / 1_000_000L;
        ResourceUsageSnapshot resourceSnapshot = resourceSampler.stopAndSnapshot();
        PbeResultWriter.write(
            options.paths().pbeDir(),
            result,
            matrix,
            options.coverageMetric(),
            matrixMillis,
            solverMillis,
            resourceSnapshot
        );

        System.out.println("PBE reductions: candidates=" + result.candidateTestsCount()
            + ", preprocessedAway=" + result.preprocessedAwayCount()
            + ", essential=" + result.essentialCount()
            + ", remainingTestsForSolver=" + result.remainingTestsForSolverCount()
            + ", remainingElementsForSolver=" + result.remainingElementsForSolverCount());
        System.out.println("Solver model: constraintsBeforeReduction=" + result.constraintsBeforeReductionCount()
            + ", constraintsAfterReduction=" + result.constraintsAfterReductionCount()
            + ", selectedBySolver=" + result.solverCount());
        System.out.println("PBE selected tests: " + result.selectedTestIds().size());
        System.out.println("Matrix build ms: " + matrixMillis);
        System.out.println("PBE solve ms: " + solverMillis);
        printResourceMetrics(resourceSnapshot);
        System.out.println("Selected IDs file: " + options.paths().selectedIdsFile(options.minimizerAlgorithm()));
    }

    private void minimizeWithHeuristic(
        PipelineOptions options,
        CoverageMatrix matrix,
        long matrixMillis,
        ResourceUsageSampler resourceSampler
    ) throws Exception {
        HeuristicMinimizer minimizer = new HeuristicMinimizer();
        HeuristicResult result = minimizer.minimize(matrix, options.minimizerAlgorithm());
        long solverMillis = result.solveNanos() / 1_000_000L;
        ResourceUsageSnapshot resourceSnapshot = resourceSampler.stopAndSnapshot();
        HeuristicResultWriter.write(
            options.paths().minimizerDir(options.minimizerAlgorithm()),
            result,
            matrix,
            options.coverageMetric(),
            matrixMillis,
            solverMillis,
            resourceSnapshot
        );

        System.out.println("Heuristic algorithm: " + result.algorithmName() + " (" + options.minimizerAlgorithm() + ")");
        System.out.println("Heuristic selected tests: " + result.selectedTestIds().size());
        System.out.println("Heuristic reported coverage %: " + formatDouble(result.coveragePercent()));
        System.out.println("Matrix build ms: " + matrixMillis);
        System.out.println("Heuristic solve ms: " + solverMillis);
        printResourceMetrics(resourceSnapshot);
        System.out.println("Selected IDs file: " + options.paths().selectedIdsFile(options.minimizerAlgorithm()));
    }

    private void minimizeWithLp(
        PipelineOptions options,
        CoverageMatrix matrix,
        long matrixMillis,
        ResourceUsageSampler resourceSampler
    ) throws Exception {
        long solverStart = System.nanoTime();
        LpMinimizer minimizer = new LpMinimizer();
        LpResult result = minimizer.minimize(matrix, options.lpConfig());
        long solverMillis = (System.nanoTime() - solverStart) / 1_000_000L;
        ResourceUsageSnapshot resourceSnapshot = resourceSampler.stopAndSnapshot();
        LpResultWriter.write(
            options.paths().lpDir(),
            result,
            matrix,
            options.coverageMetric(),
            matrixMillis,
            solverMillis,
            resourceSnapshot
        );

        System.out.println("LP reductions: candidates=" + result.candidateTestsCount()
            + ", preprocessedAway=" + result.preprocessedAwayCount()
            + ", emptyRemoved=" + result.emptyTestsRemovedCount()
            + ", essential=" + result.essentialCount()
            + ", remainingTestsForSolver=" + result.remainingTestsForSolverCount()
            + ", remainingElementsForSolver=" + result.remainingElementsForSolverCount());
        System.out.println("LP solver model: constraintsBeforeReduction=" + result.constraintsBeforeReductionCount()
            + ", constraintsAfterReduction=" + result.constraintsAfterReductionCount()
            + ", selectedBySolver=" + result.solverCount());
        System.out.println("LP selected tests: " + result.selectedTestIds().size());
        System.out.println("Matrix build ms: " + matrixMillis);
        System.out.println("LP solve ms: " + solverMillis);
        printResourceMetrics(resourceSnapshot);
        System.out.println("Selected IDs file: " + options.paths().selectedIdsFile(options.minimizerAlgorithm()));
    }

    private static void printResourceMetrics(ResourceUsageSnapshot snapshot) {
        System.out.println("Peak RAM MB (heap used): " + formatDouble(snapshot.peakHeapUsedMb()));
        if (snapshot.hasPeakProcessCommittedBytes()) {
            System.out.println("Peak RAM MB (process committed virtual): " + formatDouble(snapshot.peakProcessCommittedMb()));
        } else {
            System.out.println("Peak RAM MB (process committed virtual): n/a");
        }
        if (snapshot.hasPeakProcessCpuPercent()) {
            System.out.println("Peak CPU % (process, sampled): " + formatDouble(snapshot.peakProcessCpuPercent()));
        } else {
            System.out.println("Peak CPU % (process, sampled): n/a");
        }
        System.out.println("Resource sample interval ms: " + snapshot.sampleIntervalMs());
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void collectSelected(PipelineOptions options) throws Exception {
        ensureJacocoAgent("collect-selected");
        List<String> selectedIds = JunitSuiteRunner.readSelectedIds(
            options.paths().selectedIdsFile(options.minimizerAlgorithm())
        );
        JunitSuiteRunner runner = new JunitSuiteRunner();
        Path testsCsv = runner.runSelectedTests(options.paths().selectedRunDir(), selectedIds);
        System.out.println("Selected test run metadata: " + testsCsv);
    }

    private void validate(PipelineOptions options) throws Exception {
        CoverageMatrixBuilder builder = new CoverageMatrixBuilder();
        CoverageMatrix baseline = builder.build(
            options.paths().fullTestsCsv(),
            options.classesDir(),
            options.coverageMetric(),
            true
        );
        CoverageMatrix selected = builder.build(
            options.paths().selectedTestsCsv(),
            options.classesDir(),
            options.coverageMetric(),
            true
        );

        Set<String> baselineElements = new HashSet<>(baseline.elements());
        Set<String> selectedElements = new HashSet<>(selected.elements());

        CoverageValidator validator = new CoverageValidator();
        CoverageValidationResult validation = validator.validate(baselineElements, selectedElements);
        ValidationReportWriter.write(options.paths().validationDir(), validation);

        System.out.println("Coverage preserved: " + validation.coveragePreserved());
        System.out.println("Missing elements count: " + validation.missingElementsCount());
    }

    private static boolean requiresCoverageCollection(String command) {
        return "collect-full".equals(command) || "collect-selected".equals(command) || "pipeline".equals(command);
    }

    private static void ensureJacocoAgent(String command) {
        if (!JacocoAgentClient.isAgentActive()) {
            throw new IllegalStateException("JaCoCo agent is not active for command " + command + ".");
        }
    }

    private static void printUsage() {
        System.out.println("Usage: MinimizerPipelineMain <command>");
        System.out.println("Commands: collect-full | minimize | collect-selected | validate | pipeline");
        System.out.println("Optional property: -Dminimizer.algorithm=PBE|LP|NAIVE|GREEDY_ESSENTIAL|GENETIC|GA|GR (default PBE)");
    }
}
