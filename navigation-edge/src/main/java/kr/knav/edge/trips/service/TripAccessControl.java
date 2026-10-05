package kr.knav.edge.trips.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keeps anonymous per-trip keys out of SQLite and compares them without timing shortcuts. */
@Component
public class TripAccessControl {
    private final String adminKey;

    public TripAccessControl(@Value("${navigation.trip-admin-key:}") String adminKey) {
        if (!adminKey.isBlank() && adminKey.length() < 32)
            throw new IllegalArgumentException("TRIP_ADMIN_KEY must contain at least 32 characters");
        this.adminKey = adminKey;
    }

    public String hash(String accessKey) {
        if (accessKey == null || !accessKey.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Invalid trip access key");
        byte[] decoded;
        try { decoded = Base64.getUrlDecoder().decode(accessKey); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid trip access key"); }
        if (decoded.length != 32) throw new IllegalArgumentException("Invalid trip access key");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(decoded)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    public boolean matches(String expectedHash, String accessKey) {
        if (expectedHash == null || accessKey == null) return false;
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(expectedHash),
                    HexFormat.of().parseHex(hash(accessKey)));
        } catch (IllegalArgumentException exception) { return false; }
    }

    public void requireAdmin(String suppliedKey) {
        if (adminKey.isBlank() || suppliedKey == null || !MessageDigest.isEqual(
                adminKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8)))
            throw new TripAccessDeniedException();
    }
}
