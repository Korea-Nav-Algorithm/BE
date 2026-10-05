package kr.knav.edge.places.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.knav.common.Coordinate;
import kr.knav.edge.places.dto.PlaceResult;
import kr.knav.edge.places.service.PlaceSearchProvider;
import kr.knav.edge.places.service.PlaceSearchUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Adapts Naver's non-login local search without exposing credentials to the browser. */
@Component
public class NaverPlaceClient implements PlaceSearchProvider {
    private static final double COORDINATE_SCALE = 10_000_000d;
    private final ObjectMapper mapper;
    private final String url;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public NaverPlaceClient(ObjectMapper mapper, @Value("${places.naver-url}") String url,
                            @Value("${places.naver-client-id}") String clientId,
                            @Value("${places.naver-client-secret}") String clientSecret) {
        this.mapper = mapper;
        this.url = url;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override public List<PlaceResult> search(String query) {
        if (clientId.isBlank() || clientSecret.isBlank()) throw new PlaceSearchUnavailableException();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url + "?query=" + encoded + "&display=5"))
                .timeout(Duration.ofSeconds(3))
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 512_000)
                throw new PlaceSearchUnavailableException();
            NaverResponse parsed = mapper.readValue(response.body(), NaverResponse.class);
            if (parsed.items() == null || parsed.items().size() > 5)
                throw new PlaceSearchUnavailableException();
            List<PlaceResult> places = new ArrayList<>();
            for (NaverItem item : parsed.items()) places.add(toPlace(item));
            return List.copyOf(places);
        } catch (PlaceSearchUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            // Keep upstream URLs, response bodies and credentials out of public errors.
            throw new PlaceSearchUnavailableException();
        }
    }

    @Override public String source() { return "NAVER"; }

    private PlaceResult toPlace(NaverItem item) {
        try {
            // Naver local search changed from KATECH to WGS84 integer coordinates in 2023.
            double lng = Long.parseLong(item.mapx()) / COORDINATE_SCALE;
            double lat = Long.parseLong(item.mapy()) / COORDINATE_SCALE;
            String name = HtmlUtils.htmlUnescape(item.title().replaceAll("(?i)</?b>", "")).trim();
            if (name.isBlank() || !Double.isFinite(lat) || !Double.isFinite(lng)
                    || Math.abs(lat) > 90 || Math.abs(lng) > 180)
                throw new PlaceSearchUnavailableException();
            String address = item.roadAddress() == null || item.roadAddress().isBlank()
                    ? item.address() : item.roadAddress();
            String id = "naver-" + UUID.nameUUIDFromBytes((name + ":" + item.mapx() + ":" + item.mapy())
                    .getBytes(StandardCharsets.UTF_8));
            return new PlaceResult(id, name, address == null ? "" : address, new Coordinate(lat, lng));
        } catch (NumberFormatException | NullPointerException exception) {
            throw new PlaceSearchUnavailableException();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NaverResponse(List<NaverItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NaverItem(String title, String address, String roadAddress, String mapx, String mapy) {}
}
