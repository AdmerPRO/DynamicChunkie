package dev.dynamicchunkie;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentAnalyzerTest {
    private double score(double distance, double horizontal, double vertical, double clear, boolean sky, double y) {
        return EnvironmentAnalyzer.openness(distance, horizontal, vertical, clear, sky, y, true);
    }
    @Test void tunnelLargeCaveAndSurfaceHaveIncreasingTargets() {
        var c = new DynamicChunkieConfig();
        double tunnel = score(2, .08, .03, 0, false, 20);
        double cave = score(22, .8, .4, .3, false, 20);
        double surface = score(27, 1, .5, .7, true, 64);
        assertTrue(tunnel < cave && cave < surface);
        assertTrue(EnvironmentAnalyzer.target(tunnel, RuntimeSettings.capture(c)) < 4);
        assertTrue(EnvironmentAnalyzer.target(cave, RuntimeSettings.capture(c)) >= 10);
        assertTrue(EnvironmentAnalyzer.target(surface, RuntimeSettings.capture(c)) >= 16);
    }
    @Test void highEnclosedRoomGetsNoAltitudeBonus() {
        assertEquals(score(3, .1, .1, 0, false, 20), score(3, .1, .1, 0, false, 240));
    }
    @Test void altitudeOnlyIncreasesOpenTerrain() {
        assertTrue(score(30, 1, .5, .8, true, 220) > score(30, 1, .5, .8, true, 64));
        assertEquals(1, score(32, 1, 1, 1, true, 300));
    }
    @Test void targetsAreContinuousAndBoundedForDefaultProfile() {
        var c = new DynamicChunkieConfig();
        double previous = 2;
        for (int i = 0; i <= 1000; i++) {
            double value = EnvironmentAnalyzer.target(i / 1000.0, RuntimeSettings.capture(c));
            assertTrue(value >= previous && value <= 32);
            assertTrue(value - previous < .1);
            previous = value;
        }
    }
    @Test void openCavesAndSeaLevelSurfaceDoNotRequestMountainDistances() {
        var c = new DynamicChunkieConfig();
        var cave = new EnvironmentScore(.9, 32, 1, 1, 1, false, 20, false, 0, 1, "Cave");
        var surface = new EnvironmentScore(.9, 32, 1, .5, 1, true, 64, false, 0, 1, "Surface");
        var mountain = new EnvironmentScore(1, 32, 1, 1, 1, true, 240, false, 0, 1, "Mountain");
        assertEquals(12, EnvironmentAnalyzer.target(cave, .9, RuntimeSettings.capture(c)));
        assertEquals(16, EnvironmentAnalyzer.target(surface, .9, RuntimeSettings.capture(c)));
        assertEquals(16, EnvironmentAnalyzer.target(mountain, 1, RuntimeSettings.capture(c)));
        c.mountainDistance = 24;
        c.highAltitudeDistance = 32;
        assertEquals(32, EnvironmentAnalyzer.target(mountain, 1, RuntimeSettings.capture(c)));
    }
    @Test void invalidConfigurationIsBoundedAndFinite() {
        var c = new DynamicChunkieConfig();
        c.minRenderDistance = -20; c.maxRenderDistance = 900;
        c.analysisIntervalMs = 0;
        c.smoothingFactor = Double.POSITIVE_INFINITY; c.tunnelDistance = -1;
        c.validate();
        assertEquals(2, c.minRenderDistance); assertEquals(32, c.maxRenderDistance);
        assertEquals(250, c.analysisIntervalMs);
        assertEquals(.35, c.smoothingFactor); assertEquals(2, c.tunnelDistance);
    }
    @Test void configuredBoundsConstrainEveryProfileValue() {
        var c = new DynamicChunkieConfig();
        c.minRenderDistance = 6; c.maxRenderDistance = 12; c.validate();
        for (int i = 0; i <= 100; i++) {
            double target = EnvironmentAnalyzer.target(i / 100.0, RuntimeSettings.capture(c));
            assertTrue(target >= 6 && target <= 12);
        }
    }
}
