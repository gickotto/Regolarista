package it.regolarista.app;

/**
 * Smooth progress between asynchronous GNSS observations.
 * All times are monotonic milliseconds, distance is metres, speed is metres/second.
 * Prediction is capped after GNSS data becomes stale; observations are assimilated
 * gradually, avoiding a visible step in the regularity bar on every GNSS fix.
 * Not a substitute for accurate positioning or an external calibrated odometer.
 */
public final class ProgressPredictor {
    private static final long MAX_EXTRAPOLATION_MS = 1600;
    private static final double CORRECTION_TIME_CONSTANT_SECONDS = 0.65;
    private boolean initialized;
    private long lastTickMs, lastFixMs;
    private double displayedMeters, observedMeters, speedMetersPerSecond;

    public void reset() {
        initialized = false;
        lastTickMs = lastFixMs = 0;
        displayedMeters = observedMeters = speedMetersPerSecond = 0;
    }

    public void observe(double meters, double speedMps, long nowMs) {
        if (!Double.isFinite(meters) || !Double.isFinite(speedMps)) return;
        meters = Math.max(0, meters);
        speedMps = Math.max(0, Math.min(55, speedMps));
        if (!initialized) {
            displayedMeters = meters;
            lastTickMs = nowMs;
            initialized = true;
        } else {
            tick(nowMs);
        }
        observedMeters = meters;
        speedMetersPerSecond = speedMps;
        lastFixMs = nowMs;
    }

    public double tick(long nowMs) {
        if (!initialized) return 0;
        if (nowMs <= lastTickMs) return displayedMeters;
        // Integrate only the part of the interval for which the last fix remains fresh.
        long until = Math.min(nowMs, lastFixMs + MAX_EXTRAPOLATION_MS);
        double moveSeconds = Math.max(0, until - Math.max(lastTickMs, lastFixMs)) / 1000.0;
        displayedMeters += speedMetersPerSecond * moveSeconds;
        double dtSeconds = (nowMs-lastTickMs) / 1000.0;
        if (nowMs <= lastFixMs + MAX_EXTRAPOLATION_MS) {
            double expectedMeters = observedMeters + speedMetersPerSecond * (nowMs-lastFixMs)/1000.0;
            double alpha = 1 - Math.exp(-dtSeconds / CORRECTION_TIME_CONSTANT_SECONDS);
            displayedMeters += alpha * (expectedMeters-displayedMeters);
        }
        lastTickMs = nowMs;
        return displayedMeters;
    }

    public double meters() { return displayedMeters; }
    public boolean hasFix() { return initialized; }
}
