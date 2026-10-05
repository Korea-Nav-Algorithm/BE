package kr.knav.engine.graph;

/** OSM via-node restriction between two road ways. Via-way relations need a separate model. */
public record TurnRestriction(long fromWayId, long viaNodeId, long toWayId,
                              boolean only, boolean uTurn) {}
