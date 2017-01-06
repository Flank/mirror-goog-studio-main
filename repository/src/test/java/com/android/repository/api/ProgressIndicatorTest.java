package com.android.repository.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link ProgressIndicator}
 */
public class ProgressIndicatorTest {
    private static final double DELTA = 0.00001;
    @Test
    public void createSubProgress() {
        ProgressIndicator progress = new ProgressIndicatorAdapter() {
            private double mFraction;

            @Override
            public void setFraction(double v) {
                mFraction = v;
            }

            @Override
            public double getFraction() {
                return mFraction;
            }
        };

        progress.setFraction(0.1);
        assertEquals(0.1, progress.getFraction(), DELTA);

        ProgressIndicator sub = progress.createSubProgress(0.6);
        sub.setFraction(0.4);
        assertEquals(0.3, progress.getFraction(), DELTA);
        assertEquals(0.4, sub.getFraction(), DELTA);

        ProgressIndicator subsub = sub.createSubProgress(0.6);
        subsub.setFraction(0.5);
        assertEquals(0.5, subsub.getFraction(), DELTA);
        assertEquals(0.5, sub.getFraction(), DELTA);
        assertEquals(0.35, progress.getFraction(), DELTA);

        // Test boundary cases
        progress.setFraction(0.1);
        sub = progress.createSubProgress(0.1);
        assertEquals(0, sub.getFraction(), DELTA);
        sub.setFraction(0.5);
        assertEquals(0, sub.getFraction(), DELTA);
        assertEquals(0.1, progress.getFraction(), DELTA);

        sub = progress.createSubProgress(0.2);
        sub.setFraction(-1);
        assertEquals(0, sub.getFraction(), DELTA);
        assertEquals(0.1, progress.getFraction(), DELTA);

        sub.setFraction(2);
        assertEquals(1, sub.getFraction(), DELTA);
        assertEquals(0.2, progress.getFraction(), DELTA);
    }
}
