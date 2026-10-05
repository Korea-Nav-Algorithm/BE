package kr.knav.engine.traffic;

import java.io.IOException;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import jakarta.annotation.PreDestroy;
import javax.net.ssl.SSLException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Fetches Gyeonggi link speeds, retaining the last good snapshot on transient failure. */
@Component
public class GyeonggiTrafficProvider implements TrafficProvider {
    private static final Logger log = LoggerFactory.getLogger(GyeonggiTrafficProvider.class);
    private static final long REFRESH_MILLIS = 60_000;
    private static final long MAX_AGE_MILLIS = 300_000;
    private static final long MAX_OBSERVATION_AGE_MILLIS = 900_000;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter OBSERVATION_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final String url;
    private final String key;
    private final boolean enabled;
    private final List<String> routeIds;
    private final TrafficEdgeMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gyeonggi-traffic-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile TrafficSnapshot cached = new TrafficSnapshot(Map.of(), 0);
    private volatile long lastAttempt;
    public GyeonggiTrafficProvider(@Value("${traffic.gyeonggi-url}") String url,
                                   @Value("${traffic.gyeonggi-key}") String key,
                                   @Value("${traffic.provider}") String provider,
                                   @Value("${traffic.gyeonggi-route-ids:}") String configuredRouteIds,
                                   TrafficEdgeMapper mapper) {
        this.url = url; this.key = key; this.mapper = mapper; this.enabled = "gyeonggi".equals(provider);
        this.routeIds = configuredRouteIds.isBlank() ? List.of("") : Arrays.stream(configuredRouteIds.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        if (this.routeIds.isEmpty()) throw new IllegalArgumentException("No valid traffic route IDs");
    }
    @EventListener(ApplicationReadyEvent.class) public void warmUp() { refreshAsync(); }
    @PreDestroy public void shutdown() { executor.shutdownNow(); }
    @Override public TrafficSnapshot current() {
        long now = System.currentTimeMillis();
        if (now - lastAttempt >= REFRESH_MILLIS) refreshAsync();
        return now - cached.timestamp() <= MAX_AGE_MILLIS ? cached : new TrafficSnapshot(Map.of(), 0);
    }
    @Override public String source() { return current().edges().isEmpty() ? "UNKNOWN" : "GYEONGGI"; }

    private void refreshAsync() {
        if (!enabled || url.isBlank() || key.isBlank() || !refreshing.compareAndSet(false, true)) return;
        lastAttempt = System.currentTimeMillis();
        executor.execute(() -> {
            try { refresh(); }
            finally { refreshing.set(false); }
        });
    }

    private void refresh() {
        try {
            Map<String, TrafficState> edges = new HashMap<>();
            long now = System.currentTimeMillis();
            for (String routeId : routeIds) {
                String separator = url.contains("?") ? "&" : "?";
                String query = "serviceKey=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
                if (!routeId.isBlank()) query += "&routeId=" + URLEncoder.encode(routeId, StandardCharsets.UTF_8);
                URI uri = URI.create(url + separator + query);
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new TrafficFeedException("http_status");
                if (response.body().length() > 32_000_000) throw new TrafficFeedException("response_too_large");
                edges.putAll(parse(response.body(), now).edges());
            }
            if (edges.isEmpty()) throw new TrafficFeedException("no_mapped_edges");
            cached = new TrafficSnapshot(edges, now);
            log.info("event=traffic_refresh status=ready mappedDirectedEdges={} requestedRoads={}",
                    edges.size(), routeIds.size());
        } catch (Exception exception) {
            // Exception messages can contain a URL with the secret service key.
            log.warn("event=traffic_refresh status=unavailable reason={} retainedDirectedEdges={}",
                    failureReason(exception), cached.edges().size());
        }
    }
    private String failureReason(Exception exception) {
        if (exception instanceof TrafficFeedException feed) return feed.reason;
        if (exception instanceof HttpTimeoutException) return "timeout";
        if (exception instanceof SSLException) return "tls";
        if (exception instanceof UnknownHostException) return "dns";
        if (exception instanceof ConnectException) return "connect";
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
        if (exception instanceof IOException) return "io";
        return "invalid_response";
    }
    private TrafficSnapshot parse(String xml, long now) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Element root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
        NodeList status = root.getElementsByTagName("headerCd");
        if (status.getLength() == 0) throw new TrafficFeedException("missing_header");
        String headerCode = status.item(0).getTextContent().trim();
        if (!headerCode.matches("[0-9]{1,2}")) throw new TrafficFeedException("invalid_header");
        if (!"0".equals(headerCode)) throw new TrafficFeedException("upstream_code_" + headerCode);
        Map<String, TrafficState> edges = new HashMap<>();
        NodeList elements = root.getElementsByTagName("linkId");
        for (int index = 0; index < elements.getLength(); index++) {
            Element parent = (Element) elements.item(index).getParentNode();
            NodeList speeds = parent.getElementsByTagName("spd");
            NodeList dates = parent.getElementsByTagName("collDate");
            if (speeds.getLength() == 0 || dates.getLength() == 0) continue;
            double speed;
            try { speed = Double.parseDouble(speeds.item(0).getTextContent().trim()); }
            catch (NumberFormatException invalidSpeed) { continue; }
            if (!Double.isFinite(speed) || speed <= 0) continue;
            long observedAt = observationTime(dates.item(0).getTextContent());
            if (observedAt <= 0 || observedAt > now + 60_000 || now - observedAt > MAX_OBSERVATION_AGE_MILLIS)
                continue;
            String linkId = elements.item(index).getTextContent().trim();
            ExternalTrafficLink link = new ExternalTrafficLink(linkId, speed, observedAt, null, null);
            for (String edgeId : mapper.mapExternalLinkToEdges(link))
                edges.put(edgeId, new TrafficState(edgeId, speed, observedAt));
        }
        return new TrafficSnapshot(edges, now);
    }
    private long observationTime(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 12) digits += "00";
        if (digits.length() > 14) digits = digits.substring(0, 14);
        if (digits.length() != 14) return 0;
        try { return LocalDateTime.parse(digits, OBSERVATION_DATE).atZone(KOREA).toInstant().toEpochMilli(); }
        catch (RuntimeException invalidDate) { return 0; }
    }
    private static final class TrafficFeedException extends Exception {
        private final String reason;
        private TrafficFeedException(String reason) { this.reason = reason; }
    }
}
