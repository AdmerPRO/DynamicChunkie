package dev.dynamicchunkie;

import net.minecraft.client.Minecraft;

public final class EnvironmentAnalyzer {
    private static final double[] SCORE_KNOTS={0,.13,.30,.65,.82,1};
    private final RaycastAnalyzer rays = new RaycastAnalyzer();
    public EnvironmentScore analyze(Minecraft client, DynamicChunkieConfig draft) {
        return evaluate(client, RuntimeSettings.capture(draft), new RaycastAnalyzer().sample(client.level,client.player,0,Long.MAX_VALUE));
    }

    public RaycastAnalyzer.Sample sample(Minecraft client, long revision, long deadline) {
        return rays.sample(client.level,client.player,revision,deadline);
    }
    public void reset() { rays.reset(); }
    public EnvironmentScore evaluate(Minecraft client, RuntimeSettings c, RaycastAnalyzer.Sample r) {
        var p = client.player;
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
            double confidence=Math.min(1, .35 + .4*r.forward() + .25*r.gaze());
            int horizon=(int)(c.predictionLookaheadMs*confidence*Math.min(1,.4+speed/30));
            var preview=rays.preview(client.level,p,horizon);
            predicted=preview.openness();
            exitAhead=c.enableSkyDetection && !sky && preview.sky() && predicted>.55;
            double alignment=p.getDeltaMovement().normalize().dot(p.getLookAngle());
            if(flight)open=Math.min(1,open+dev.dynamicchunkie.adaptive.AnalysisPolicy.predictionBoost(
                    speed,r.horizontal(),r.forward(),r.gaze(),predicted,alignment));
            // Only a loaded, unobstructed future position can predict a sky opening.
            if(exitAhead)open=Math.min(1,open+.08*predicted);
            else if(sky && preview.sky() && predicted>.7 && predicted>r.horizontal()+.15)
                open=Math.min(1,open+.06*(predicted-r.horizontal()));
            if(dev.dynamicchunkie.adaptive.AnalysisPolicy.approachingWall(flight,speed,r.forward()))open=Math.max(0,open-.12);
        }
        String name=EnvironmentClassifier.classify(r,sky,altitude,open,exitAhead);
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
    static int target(EnvironmentScore environment, double smoothed, RuntimeSettings c) {
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
    static double target(double score, RuntimeSettings c) {
        for (int i = 1; i < SCORE_KNOTS.length; i++)
            if (score <= SCORE_KNOTS[i]) {
                int lower=profileValue(i-1,c),upper=profileValue(i,c);
                return lower+(upper-lower)*(score-SCORE_KNOTS[i-1])/(SCORE_KNOTS[i]-SCORE_KNOTS[i-1]);
            }
        return c.highAltitudeDistance;
    }
    private static int profileValue(int index,RuntimeSettings c) {
        return switch(index) {
            case 0 -> c.tunnelDistance;
            case 1 -> c.smallCaveDistance;
            case 2 -> c.largeCaveDistance;
            case 3 -> c.surfaceDistance;
            case 4 -> c.mountainDistance;
            default -> c.highAltitudeDistance;
        };
    }
}
