package minimizer.pipeline;

import java.nio.file.Path;
import java.nio.file.Paths;

final class PipelinePaths {
    private final Path root;

    private PipelinePaths(Path root) {
        this.root = root;
    }

    static PipelinePaths fromSystemProperties() {
        String rootDir = System.getProperty("minimizer.outputDir", "target/minimizer");
        return new PipelinePaths(Paths.get(rootDir).toAbsolutePath().normalize());
    }

    Path root() {
        return root;
    }

    Path fullRunDir() {
        return root.resolve("full-run");
    }

    Path selectedRunDir() {
        return root.resolve("selected-run");
    }

    Path pbeDir() {
        return root.resolve("pbe");
    }

    Path lpDir() {
        return root.resolve("lp");
    }

    Path naiveDir() {
        return root.resolve("naive");
    }

    Path greedyEssentialDir() {
        return root.resolve("greedy-essential");
    }

    Path geneticDir() {
        return root.resolve("genetic");
    }

    Path minimizerDir(MinimizerAlgorithm algorithm) {
        switch (algorithm) {
            case LP:
                return lpDir();
            case NAIVE:
                return naiveDir();
            case GREEDY_ESSENTIAL:
                return greedyEssentialDir();
            case GENETIC:
                return geneticDir();
            case PBE:
            default:
                return pbeDir();
        }
    }

    Path validationDir() {
        return root.resolve("validation");
    }

    Path fullTestsCsv() {
        return fullRunDir().resolve("tests.csv");
    }

    Path selectedTestsCsv() {
        return selectedRunDir().resolve("tests.csv");
    }

    Path selectedIdsFile() {
        return selectedIdsFile(MinimizerAlgorithm.PBE);
    }

    Path selectedIdsFile(MinimizerAlgorithm algorithm) {
        return minimizerDir(algorithm).resolve("selected-test-ids.txt");
    }
}
