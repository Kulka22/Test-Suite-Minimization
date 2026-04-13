package minimizer.pipeline;

enum MinimizerAlgorithm {
    PBE,
    LP,
    NAIVE,
    GREEDY_ESSENTIAL,
    GENETIC;

    static MinimizerAlgorithm fromProperty(String rawValue) {
        if (rawValue == null) {
            return PBE;
        }
        String normalized = rawValue.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return PBE;
        }
        if ("GA".equals(normalized)) {
            return GENETIC;
        }
        if ("GR".equals(normalized) || "GREEDY".equals(normalized)) {
            return GREEDY_ESSENTIAL;
        }
        return MinimizerAlgorithm.valueOf(normalized);
    }
}
