package dev.tee.stateditor.client;

/** Projected mesh envelope, independent of the Pokémon's world/profile scale. */
final class ModelBounds {
    private double minX = Double.POSITIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    void include(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
    }

    Fit fit(int x, int y, int width, int height, int padding) {
        if (!Double.isFinite(minX) || width <= padding * 2 || height <= padding * 2) return null;
        double scale = Math.min((width - padding * 2) / Math.max(1e-6, maxX - minX),
                (height - padding * 2) / Math.max(1e-6, maxY - minY));
        return new Fit(scale, x + width / 2.0 - (minX + maxX) / 2 * scale,
                y + height / 2.0 - (minY + maxY) / 2 * scale);
    }

    record Fit(double scale, double x, double y) {
    }
}
