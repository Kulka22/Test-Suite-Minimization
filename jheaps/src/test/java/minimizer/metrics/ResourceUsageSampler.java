package minimizer.metrics;

import java.lang.management.ManagementFactory;
import java.util.concurrent.*;

public final class ResourceUsageSampler {
    private static final long UNKNOWN_PROCESS_MEMORY = -1L;

    private final long sampleIntervalMs;
    private final Runtime runtime;
    private final com.sun.management.OperatingSystemMXBean osBean;
    private final int processors;
    private final ScheduledExecutorService scheduler;

    private ScheduledFuture<?> sampleTask;
    private long peakHeapUsedBytes;
    private long peakProcessCommittedBytes;
    private double peakProcessCpuPercent;
    private long previousProcessCpuTimeNanos;
    private long previousWallTimeNanos;
    private boolean started;
    private ResourceUsageSnapshot finalSnapshot;

    private ResourceUsageSampler(long sampleIntervalMs) {
        if (sampleIntervalMs <= 0L) {
            throw new IllegalArgumentException("sampleIntervalMs must be > 0");
        }
        this.sampleIntervalMs = sampleIntervalMs;
        this.runtime = Runtime.getRuntime();
        this.processors = Math.max(1, runtime.availableProcessors());
        this.osBean = resolveOperatingSystemMxBean();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("minimize-resource-sampler"));
        this.peakHeapUsedBytes = 0L;
        this.peakProcessCommittedBytes = UNKNOWN_PROCESS_MEMORY;
        this.peakProcessCpuPercent = Double.NaN;
        this.previousProcessCpuTimeNanos = Long.MIN_VALUE;
        this.previousWallTimeNanos = Long.MIN_VALUE;
    }

    public static ResourceUsageSampler start(long sampleIntervalMs) {
        ResourceUsageSampler sampler = new ResourceUsageSampler(sampleIntervalMs);
        sampler.startSampling();
        return sampler;
    }

    public synchronized ResourceUsageSnapshot stopAndSnapshot() {
        if (finalSnapshot != null) {
            return finalSnapshot;
        }

        if (sampleTask != null) {
            sampleTask.cancel(false);
        }

        sampleOnce(System.nanoTime());
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(1L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        finalSnapshot = new ResourceUsageSnapshot(
            sampleIntervalMs,
            peakHeapUsedBytes,
            peakProcessCommittedBytes,
            peakProcessCpuPercent
        );
        return finalSnapshot;
    }

    private synchronized void startSampling() {
        if (started) {
            return;
        }
        started = true;
        sampleOnce(System.nanoTime());
        sampleTask = scheduler.scheduleAtFixedRate(
            this::sampleSafely,
            sampleIntervalMs,
            sampleIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }

    private void sampleSafely() {
        try {
            synchronized (this) {
                sampleOnce(System.nanoTime());
            }
        } catch (Throwable ignored) {
            // Metrics collection must never fail minimize.
        }
    }

    private void sampleOnce(long wallTimeNanos) {
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        if (heapUsed > peakHeapUsedBytes) {
            peakHeapUsedBytes = heapUsed;
        }

        if (osBean == null) {
            previousWallTimeNanos = wallTimeNanos;
            return;
        }

        long committed = osBean.getCommittedVirtualMemorySize();
        if (committed >= 0L && committed > peakProcessCommittedBytes) {
            peakProcessCommittedBytes = committed;
        }

        long processCpuTime = osBean.getProcessCpuTime();
        if (processCpuTime >= 0L && previousProcessCpuTimeNanos >= 0L && previousWallTimeNanos > 0L) {
            long wallDelta = wallTimeNanos - previousWallTimeNanos;
            long cpuDelta = processCpuTime - previousProcessCpuTimeNanos;
            if (wallDelta > 0L && cpuDelta >= 0L) {
                double cpuPercent = (cpuDelta / (double) wallDelta) * 100.0d / processors;
                if (Double.isFinite(cpuPercent) && cpuPercent >= 0.0d) {
                    if (cpuPercent > 100.0d) {
                        cpuPercent = 100.0d;
                    }
                    if (Double.isNaN(peakProcessCpuPercent) || cpuPercent > peakProcessCpuPercent) {
                        peakProcessCpuPercent = cpuPercent;
                    }
                }
            }
        }

        previousProcessCpuTimeNanos = processCpuTime;
        previousWallTimeNanos = wallTimeNanos;
    }

    private static ThreadFactory daemonFactory(String threadName) {
        return runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static com.sun.management.OperatingSystemMXBean resolveOperatingSystemMxBean() {
        java.lang.management.OperatingSystemMXBean mxBean = ManagementFactory.getOperatingSystemMXBean();
        if (mxBean instanceof com.sun.management.OperatingSystemMXBean) {
            return (com.sun.management.OperatingSystemMXBean) mxBean;
        }
        return null;
    }
}
