package com.android.resources;

import junit.framework.TestCase;

public class DensityTest extends TestCase {
    public void testMethods() {
        assertTrue(Density.MEDIUM.isRecommended());
        assertTrue(Density.MEDIUM.isValidValueForDevice());
        assertFalse(Density.MEDIUM.isFakeValue());
        assertEquals("Medium Density", Density.MEDIUM.getLongDisplayValue());
        assertEquals("Medium Density", Density.MEDIUM.getShortDisplayValue());
        assertEquals(160, Density.MEDIUM.getDpiValue());
        assertEquals(4, Density.MEDIUM.since());
        assertEquals("mdpi", Density.MEDIUM.getResourceValue());

        assertTrue(Density.MEDIUM.compareTo(Density.HIGH) > 0);
        assertTrue(Density.HIGH.compareTo(Density.MEDIUM) < 0);
    }

    public void testDpiDensities() {
        assertFalse(Density.DPI_300.isRecommended());
        assertTrue(Density.DPI_300.isValidValueForDevice());
    }
}