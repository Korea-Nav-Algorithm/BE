package kr.knav.engine.traffic;

public interface TrafficProvider {
    TrafficSnapshot current();
    default String source() { return "UNKNOWN"; }
}
