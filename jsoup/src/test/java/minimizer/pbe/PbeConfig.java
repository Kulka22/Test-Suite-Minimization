package minimizer.pbe;

public final class PbeConfig {
    private final boolean enableSubsetPreprocessing;
    private final boolean enableConstraintReduction;
    private final int solverTimeoutSeconds;

    public PbeConfig(boolean enableSubsetPreprocessing, boolean enableConstraintReduction, int solverTimeoutSeconds) {
        this.enableSubsetPreprocessing = enableSubsetPreprocessing;
        this.enableConstraintReduction = enableConstraintReduction;
        if (solverTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("solverTimeoutSeconds must be > 0");
        }
        this.solverTimeoutSeconds = solverTimeoutSeconds;
    }

    public boolean enableSubsetPreprocessing() {
        return enableSubsetPreprocessing;
    }

    public boolean enableConstraintReduction() {
        return enableConstraintReduction;
    }

    public int solverTimeoutSeconds() {
        return solverTimeoutSeconds;
    }
}
