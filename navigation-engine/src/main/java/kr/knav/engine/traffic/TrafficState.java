package kr.knav.engine.traffic;

public record TrafficState(String edgeId, double observedSpeedKmh, long timestamp) {}
