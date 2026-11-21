package dev.tee.stateditor.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelBoundsTest {
    @Test
    void hugeOffCentreModelsFitInsideThePaddedViewport() {
        ModelBounds bounds = new ModelBounds();
        bounds.include(-5000, -20000);
        bounds.include(9000, 4000);
        var fit = bounds.fit(10, 39, 136, 88, 6);
        assertTrue(fit.scale() > 0);
        assertTrue(fit.scale() < 0.01);
        assertTrue(fit.x() - 5000 * fit.scale() >= 16 - 1e-6);
        assertTrue(fit.x() + 9000 * fit.scale() <= 140 + 1e-6);
        assertEquals(45, fit.y() - 20000 * fit.scale(), 1e-6);
        assertEquals(121, fit.y() + 4000 * fit.scale(), 1e-6);
    }

    @Test
    void wideModelsUseWidthNotHeightToFit() {
        ModelBounds bounds = new ModelBounds();
        bounds.include(-100, -1);
        bounds.include(100, 1);
        var fit = bounds.fit(0, 0, 24, 24, 2);
        assertEquals(0.1, fit.scale(), 1e-6);
        assertEquals(12, fit.x(), 1e-6);
        assertEquals(12, fit.y(), 1e-6);
    }

    @Test
    void emptyAndNonFiniteGeometryIsNotRendered() {
        ModelBounds bounds = new ModelBounds();
        bounds.include(Double.NaN, 10);
        bounds.include(10, Double.POSITIVE_INFINITY);
        assertNull(bounds.fit(0, 0, 24, 24, 2));
    }

    @Test
    void observedAnimationBoundsOnlyExpandToAvoidBreathingZoom() {
        ModelBounds envelope = new ModelBounds();
        envelope.include(-5, -10);
        envelope.include(5, 0);
        double scale = envelope.fit(0, 0, 100, 100, 5).scale();
        envelope.include(-1, -1);
        envelope.include(1, 0);
        assertEquals(scale, envelope.fit(0, 0, 100, 100, 5).scale());
    }
}
