package dev.dynamicchunkie;

public record EnvironmentScore(double openness, double averageDistance, double horizontal,
        double vertical, double unobstructed, boolean sky, double altitude, boolean elytra,
        double speed, double forward, String environment,
        double front, double back, double left, double right, double gaze, double predicted) {
    public EnvironmentScore(double openness,double averageDistance,double horizontal,double vertical,
            double unobstructed,boolean sky,double altitude,boolean elytra,double speed,double forward,String environment) {
        this(openness,averageDistance,horizontal,vertical,unobstructed,sky,altitude,elytra,speed,forward,environment,
                horizontal,horizontal,horizontal,horizontal,forward,0);
    }
}
