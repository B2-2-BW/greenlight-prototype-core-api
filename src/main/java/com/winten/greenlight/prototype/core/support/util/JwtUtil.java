package com.winten.greenlight.prototype.core.support.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.auth0.jwt.JWT.*;

@Component
public class JwtUtil {
    private static final String SECRET_KEY = "your-256-bit-secret-key-1234567890abcdef1234567890abcdef";
    private static final long EXPIRATION_TIME = 30 * 60 * 1000;
    private final Algorithm algorithm;

    public JwtUtil() {
        this.algorithm = Algorithm.HMAC256(SECRET_KEY);
    }

    public String generateToken(String queueId, String actionId, long score) {
        return JWT.create()
            .withSubject(queueId)
            .withClaim("actionId", actionId)
            .withClaim("score", score)
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .sign(algorithm);
    }

    public DecodedJWT validateToken(String token) {
        try {
            JWTVerifier verifier = require(algorithm).build();
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            throw new RuntimeException("Invalid or expired JWT token: " + e.getMessage());
        }
    }
}
