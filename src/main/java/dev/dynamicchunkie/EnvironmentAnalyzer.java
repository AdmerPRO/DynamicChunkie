package dev.dynamicchunkie;

import net.minecraft.client.Minecraft;

public final class EnvironmentAnalyzer {
    private final RaycastAnalyzer rays = new RaycastAnalyzer();
    public EnvironmentScore analyze(Minecraft client, DynamicChunkieConfig c) {
        var p = client.player;
        var r = rays.sample(client.level, p);
        boolean sky = c.enableSkyDetection && client.level.canSeeSky(p.blockPosition());
        double altitude = p.getY();
        double directional = c.enableDirectionalVisibility ?
                dev.dynamicchunkie.adaptive.AnalysisPolicy.directional(r.horizontal(),r.front(),r.gaze()) : r.horizontal();
        double open = openness(r.average(), directional, r.vertical(), r.clear(), sky, altitude, c.enableAltitudeFactor);
        boolean flight = p.isFallFlying();
        double speed = p.getDeltaMovement().length() * 20;
        if (c.enableElytraOptimization && flight)
            open = Math.min(1, open + 0.10 * Math.min(1, speed / 30) * r.horizontal() * r.forward());
        double predicted=0;
        boolean exitAhead=false;
        if (c.enablePrediction && speed>=.8) {
            predicted=rays.preview(client.level,p,c.predictionLookaheadMs);
            exitAhead=c.enableSkyDetection && !sky && rays.previewSky() && predicted>.55;
            double alignment=p.getDeltaMovement().normalize().dot(p.getLookAngle());
            if(flight)open=Math.min(1,open+dev.dynamicchunkie.adaptive.AnalysisPolicy.predictionBoost(
                    speed,r.horizontal(),r.forward(),r.gaze(),predicted,alignment));
            // Only a loaded, unobstructed future position can predict a sky opening.
            if(exitAhead)open=Math.min(1,open+.08*predicted);
            else if(sky && rays.previewSky() && predicted>.7 && predicted>r.horizontal()+.15)
                open=Math.min(1,open+.06*(predicted-r.horizontal()));
            if(flight && speed>8 && r.forward()<.15)open=Math.max(0,open-.12);
        }
        double longAxis=Math.max(Math.min(r.front(),r.back()),Math.min(r.left(),r.right()));
        double shortAxis=Math.min(Math.max(r.front(),r.back()),Math.max(r.left(),r.right()));
        String name = exitAhead ? "Cave Exit Ahead" : r.down()>.8 && r.horizontal()>.35 ? "Chasm / Open Drop" :
                !sky && longAxis>.45 && shortAxis<.25 ? "Narrow Passage" :
                open<.13 ? "Narrow Tunnel" : !sky && r.horizontal()<.35 && r.up()<.4 ? "Enclosed Room" :
                !sky ? "Large Cave / Covered Area" : r.horizontal()>.7 && r.up()>.8 && altitude>120 ? "Open Summit" :
                open<.78 ? "Surface" : "High Open Sky";
        return new EnvironmentScore(open, r.average(), r.horizontal(), r.vertical(), r.clear(),
                sky, altitude, flight, speed, r.forward(), name, r.front(),r.back(),r.left(),r.right(),r.gaze(),predicted);
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
