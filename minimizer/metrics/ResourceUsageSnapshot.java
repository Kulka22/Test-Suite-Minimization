package minimizer.metrics;

public final class ResourceUsageSnapshot {
    private final long sampleIntervalMs;
    private final long peakHeapUsedBytes;
    private final long peakProcessCommittedBytes;
    private final double peakProcessCpuPercent;

    public ResourceUsageSnapshot(
        long sampleIntervalMs,
        long peakHeapUsedBytes,
        long peakProcessCommittedBytes,
        double peakProcessCpuPercent
    ) {
        this.sampleIntervalMs = sampleIntervalMs;
        this.peakHeapUsedBytes = Math.max(peakHeapUsedBytes, 0L);
        this.peakProcessCommittedBytes = peakProcessCommittedBytes;
        this.peakProcessCpuPercent = peakProcessCpuPercent;
    }

    public long sampleIntervalMs() {
        return sampleIntervalMs;
    }

    public long peakHeapUsedBytes() {
        return peakHeapUsedBytes;
    }

    public double peakHeapUsedMb() {
        return bytesToMb(peakHeapUsedBytes);
    }

    public boolean hasPeakProcessCommittedBytes() {
        return peakProcessCommittedBytes >= 0L;
    }

    public long peakProcessCommittedBytes() {
        return peakProcessCommittedBytes;
    }

    public double peakProcessCommittedMb() {
        return hasPeakProcessCommittedBytes() ? bytesToMb(peakProcessCommittedBytes) : Double.NaN;
    }

    public boolean hasPeakProcessCpuPercent() {
        return !Double.isNaN(peakProcessCpuPercent);
    }

    public double peakProcessCpuPercent() {
        return peakProcessCpuPercent;
    }

    private static double bytesToMb(long bytes) {
        return bytes / (1024.0d * 1024.0d);
    }
}
