package kr.knav.engine.traffic;

import java.util.Optional;

public interface TrafficEdgeMapper { Optional<String> mapExternalLinkToEdge(ExternalTrafficLink link); }
