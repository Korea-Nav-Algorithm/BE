package kr.knav.edge.global;

import java.util.Map;
import kr.knav.edge.routes.service.EngineUnavailableException;
import kr.knav.edge.routes.service.EngineRouteNotFoundException;
import kr.knav.edge.routes.service.RouteNotFoundException;
import kr.knav.edge.places.service.PlaceSearchUnavailableException;
import kr.knav.edge.trips.service.TripNotFoundException;
import kr.knav.edge.trips.service.TripAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(EngineUnavailableException.class)
    ResponseEntity<Map<String, String>> engine(EngineUnavailableException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "ENGINE_UNAVAILABLE"));
    }
    @ExceptionHandler(PlaceSearchUnavailableException.class)
    ResponseEntity<Map<String, String>> placeSearch(PlaceSearchUnavailableException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "PLACE_SEARCH_UNAVAILABLE"));
    }
    @ExceptionHandler(EngineRouteNotFoundException.class)
    ResponseEntity<Map<String, String>> noRoute(EngineRouteNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "ROUTE_NOT_FOUND"));
    }
    @ExceptionHandler(TripNotFoundException.class)
    ResponseEntity<Map<String, String>> missing(TripNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "TRIP_NOT_FOUND"));
    }
    @ExceptionHandler(TripAccessDeniedException.class)
    ResponseEntity<Map<String, String>> tripAccessDenied(TripAccessDeniedException ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "ACCESS_DENIED"));
    }
    @ExceptionHandler(RouteNotFoundException.class)
    ResponseEntity<Map<String, String>> routeMissing(RouteNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ROUTE_NOT_FOUND"));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, String>> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REQUEST"));
    }
}
