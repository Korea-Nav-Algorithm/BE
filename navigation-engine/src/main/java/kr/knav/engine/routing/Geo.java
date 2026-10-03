package kr.knav.engine.routing;

import kr.knav.common.Coordinate;

public final class Geo {
    private Geo() {}
    public static double meters(Coordinate first, Coordinate second) {
        double latitudeDelta = Math.toRadians(second.lat() - first.lat());
        double longitudeDelta = Math.toRadians(second.lng() - first.lng());
        double latitude = Math.toRadians(first.lat());
        double secondLatitude = Math.toRadians(second.lat());
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(latitude) * Math.cos(secondLatitude) * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 6371000 * 2 * Math.asin(Math.min(1, Math.sqrt(haversine)));
    }
}
