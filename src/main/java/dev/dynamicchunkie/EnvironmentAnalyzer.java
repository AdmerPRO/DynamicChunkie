package dev.dynamicchunkie;

import net.minecraft.client.Minecraft;

public final class EnvironmentAnalyzer {
    private final RaycastAnalyzer rays = new RaycastAnalyzer();
    public EnvironmentScore analyze(Minecraft client, DynamicChunkieConfig c) {
        var p = client.player;
        var r = rays.sample(client.level, p);
        boolean sky = c.enableSkyDetection && client.level.canSeeSky(p.blockPosition());
        double altitude = p.getY();
        double open = openness(r.average(), r.horizontal(), r.vertical(), r.clear(), sky, altitude, c.enableAltitudeFactor);
        boolean flight = p.isFallFlying();
        double speed = p.getDeltaMovement().length() * 20;
        if (c.enableElytraOptimization && flight)
            open = Math.min(1, open + 0.10 * Math.min(1, speed / 30) * r.horizontal() * r.forward());
        String name = open < .13 ? "Narrow Tunnel" : open < .30 ? "Small Cave" :
                !sky ? "Large Cave / Covered Area" : open < .78 ? "Surface" :
                altitude < 160 ? "Mountain / Open Area" : "High Open Sky";
        return new EnvironmentScore(open, r.average(), r.horizontal(), r.vertical(), r.clear(),
                sky, altitude, flight, speed, r.forward(), name);
    }
    static double openness(double average, double horizontal, double vertical, double clear,
                           boolean sky, double altitude, boolean altitudeEnabled) {
        double base = .40 * average / 32 + .30 * horizontal + .10 * vertical + .10 * clear + (sky ? .10 : 0);
        double bonus = altitudeEnabled ? .20 * Math.max(0, Math.min(1, (altitude - 80) / 120)) *
                Math.max(0, (horizontal - .55) / .45) : 0;
        return Math.max(0, Math.min(1, base + bonus));
    }
    static int target(EnvironmentScore environment, double smoothed, DynamicChunkieConfig c) {
        double cap = c.surfaceDistance;
        if (c.enableSkyDetection && !environment.sky()) cap = c.undergroundOpenDistance;
        else if (c.enableAltitudeFactor && environment.horizontal() > .55) {
            double mountain = Math.max(0, Math.min(1, (environment.altitude() - 96) / 64));
            double high = Math.max(0, Math.min(1, (environment.altitude() - 160) / 80));
            cap += mountain * (c.mountainDistance - c.surfaceDistance)
                    + high * (c.highAltitudeDistance - c.mountainDistance);
        }
        return c.bound((int)Math.round(Math.min(cap, target(smoothed, c))));
    }
    static double target(double score, DynamicChunkieConfig c) {
        double[] knots = {0, .13, .30, .65, .82, 1};
        int[] values = {c.tunnelDistance, c.smallCaveDistance, c.largeCaveDistance,
                c.surfaceDistance, c.mountainDistance, c.highAltitudeDistance};
        for (int i = 1; i < knots.length; i++)
            if (score <= knots[i]) return values[i-1] + (values[i] - values[i-1]) *
                    (score - knots[i-1]) / (knots[i] - knots[i-1]);
        return values[5];
    }
}
