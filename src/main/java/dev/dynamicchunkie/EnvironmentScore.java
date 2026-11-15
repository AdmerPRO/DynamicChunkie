package dev.dynamicchunkie;

public record EnvironmentScore(double openness, double averageDistance, double horizontal,
        double vertical, double unobstructed, boolean sky, double altitude, boolean elytra,
        double speed, double forward, String environment) {}
