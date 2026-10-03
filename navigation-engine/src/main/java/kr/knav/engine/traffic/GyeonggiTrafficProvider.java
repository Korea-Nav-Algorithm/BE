package kr.knav.engine.traffic;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Fetches Gyeonggi link speeds, retaining the last good snapshot on transient failure. */
@Component
public class GyeonggiTrafficProvider implements TrafficProvider {
    private final String url;
    private final String key;
    private final TrafficEdgeMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private volatile TrafficSnapshot cached = new TrafficSnapshot(Map.of(), 0);
    public GyeonggiTrafficProvider(@Value("${traffic.gyeonggi-url}") String url,
                                   @Value("${traffic.gyeonggi-key}") String key, TrafficEdgeMapper mapper) {
        this.url = url; this.key = key; this.mapper = mapper;
    }
    @Override public synchronized TrafficSnapshot current() {
        long now = System.currentTimeMillis();
        if (now - cached.timestamp() < 60_000) return cached;
        if (url.isBlank() || key.isBlank()) return cached;
        try {
            String separator = url.contains("?") ? "&" : "?";
            URI uri = URI.create(url + separator + "serviceKey=" + URLEncoder.encode(key, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 5_000_000) return cached;
            TrafficSnapshot parsed = parse(response.body(), now);
            cached = parsed;
        } catch (Exception ignored) {
            // Routing continues at base speed or with the previous snapshot.
        }
        return cached;
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
        if (status.getLength() == 0 || !"0".equals(status.item(0).getTextContent().trim()))
            throw new IllegalArgumentException("Traffic provider error");
        Map<String, TrafficState> edges = new HashMap<>();
        NodeList elements = root.getElementsByTagName("linkId");
        for (int index = 0; index < elements.getLength(); index++) {
            Element parent = (Element) elements.item(index).getParentNode();
            NodeList speeds = parent.getElementsByTagName("spd");
            if (speeds.getLength() == 0) continue;
            double speed = Double.parseDouble(speeds.item(0).getTextContent().trim());
            if (!Double.isFinite(speed) || speed <= 0) continue;
            String linkId = elements.item(index).getTextContent().trim();
            ExternalTrafficLink link = new ExternalTrafficLink(linkId, speed, now, null, null);
            mapper.mapExternalLinkToEdge(link).ifPresent(edgeId -> edges.put(edgeId, new TrafficState(edgeId, speed, now)));
        }
        return new TrafficSnapshot(edges, now);
    }
}
