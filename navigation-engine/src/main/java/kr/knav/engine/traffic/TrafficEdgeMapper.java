package kr.knav.engine.traffic;

import java.util.List;

/** One measured traffic link can span several directed OSM graph edges. */
public interface TrafficEdgeMapper { List<String> mapExternalLinkToEdges(ExternalTrafficLink link); }
