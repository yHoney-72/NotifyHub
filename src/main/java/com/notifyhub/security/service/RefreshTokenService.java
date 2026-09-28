package com.notifyhub.security.service;

import com.notifyhub.security.entity.AppUser;
import com.notifyhub.security.entity.RefreshToken;
import com.notifyhub.security.repository.RefreshTokenRepository;
import com.notifyhub.security.exception.InvalidRefreshTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${security.refresh-token.expiration}")
    private long expiration;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String createRefreshToken(
            AppUser user,
            String userAgent,
            String ipAddress) {

        String rawToken = generateToken();
        String tokenFamily = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken(
                user,
                hashToken(rawToken),
                tokenFamily,
                LocalDateTime.now().plusSeconds(expiration),
                userAgent,
                ipAddress
        );

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Transactional
    public RefreshToken validateAndRevoke(
            String rawToken,
            String userAgent,
            String ipAddress) {

        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken =
                refreshTokenRepository.findByTokenHash(tokenHash)
                        .orElseThrow(() ->
                        new InvalidRefreshTokenException(
                                        "Invalid refresh token"
                                )
                        );

        if (refreshToken.isRevoked()) {
            revokeFamily(refreshToken.getTokenFamily());

            throw new InvalidRefreshTokenException(
                    "Refresh token reuse detected"
            );
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshToken.setRevoked(true);
            refreshToken.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(refreshToken);

            throw new InvalidRefreshTokenException(
                    "Refresh token expired"
            );
        }

        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(LocalDateTime.now());

        refreshTokenRepository.save(refreshToken);

        return refreshToken;
    }

    @Transactional
    public String rotateRefreshToken(
            RefreshToken oldToken,
            String userAgent,
            String ipAddress) {

        return createRefreshToken(
                oldToken.getUser(),
                userAgent,
                ipAddress
        );
    }

    @Transactional
    public void revokeToken(String rawToken) {

        String tokenHash = hashToken(rawToken);

        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeUserTokens(Long userId) {

        List<RefreshToken> tokens =
                refreshTokenRepository.findByUserId(userId);

        LocalDateTime now = LocalDateTime.now();

        tokens.forEach(token -> {
            token.setRevoked(true);
            token.setRevokedAt(now);
        });

        refreshTokenRepository.saveAll(tokens);
    }

    private void revokeFamily(String tokenFamily) {

        List<RefreshToken> familyTokens =
                refreshTokenRepository.findByTokenFamily(tokenFamily);

        LocalDateTime now = LocalDateTime.now();

        familyTokens.forEach(token -> {
            token.setRevoked(true);
            token.setRevokedAt(now);
        });

        refreshTokenRepository.saveAll(familyTokens);
    }

    private String generateToken() {

        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String hashToken(String token) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            token.getBytes(StandardCharsets.UTF_8)
                    );

            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                hexString.append(
                        String.format("%02x", b)
                );
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    e
            );
        }
    }
}