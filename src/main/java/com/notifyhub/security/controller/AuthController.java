package com.notifyhub.security.controller;

import com.notifyhub.security.dto.LoginRequest;
import com.notifyhub.security.dto.RegisterRequest;
import com.notifyhub.security.entity.AppUser;
import com.notifyhub.security.entity.RefreshToken;
import com.notifyhub.security.repository.UserRepository;
import com.notifyhub.security.service.RefreshTokenService;
import com.notifyhub.security.service.RefreshRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsService userDetailsService;
    private final RefreshRateLimiter refreshRateLimiter;

    @Value("${security.jwt.expiration}")
    private long accessTokenExpiration;

    @Value("${security.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request) {

        if (userRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username already exists"));
        }

        AppUser user = new AppUser(
                request.username(),
                passwordEncoder.encode(request.password()),
                "USER"
        );

        userRepository.save(user);

        return ResponseEntity.ok(
                Map.of("message", "User registered successfully")
        );
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.username(),
                                request.password()
                        )
                );

        AppUser user = userRepository.findByUsername(
                authentication.getName()
        ).orElseThrow();

        String accessToken = generateAccessToken(authentication);

        String refreshToken =
                refreshTokenService.createRefreshToken(
                        user,
                        httpRequest.getHeader("User-Agent"),
                        getClientIp(httpRequest)
                );

        addRefreshCookie(httpResponse, refreshToken);

        return ResponseEntity.ok(
                Map.of("accessToken", accessToken)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @CookieValue(
                    name = "refresh_token",
                    required = false
            )
            String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String clientIp = getClientIp(httpRequest);

        if (!refreshRateLimiter.allow(clientIp)) {
            return ResponseEntity.status(429)
                    .body(Map.of(
                            "message",
                            "Too many refresh requests"
                    ));
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401)
                    .body(Map.of(
                            "message",
                            "Refresh token required"
                    ));
        }

        RefreshToken oldToken =
                refreshTokenService.validateAndRevoke(
                        refreshToken,
                        httpRequest.getHeader("User-Agent"),
                        clientIp
                );

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(
                        oldToken.getUser().getUsername()
                );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        String newAccessToken =
                generateAccessToken(authentication);

        String newRefreshToken =
                refreshTokenService.rotateRefreshToken(
                        oldToken,
                        httpRequest.getHeader("User-Agent"),
                        clientIp
                );

        addRefreshCookie(httpResponse, newRefreshToken);

        return ResponseEntity.ok(
                Map.of("accessToken", newAccessToken)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(
                    name = "refresh_token",
                    required = false
            )
            String refreshToken,
            HttpServletResponse httpResponse) {

        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeToken(refreshToken);
        }

        clearRefreshCookie(httpResponse);

        return ResponseEntity.ok(
                Map.of("message", "Logged out successfully")
        );
    }

    private String generateAccessToken(
            Authentication authentication) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(
                        now.plusSeconds(accessTokenExpiration)
                )
                .claim(
                        "roles",
                        authentication.getAuthorities()
                                .stream()
                                .map(authority ->
                                        authority.getAuthority())
                                .toList()
                )
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(jwsHeader, claims)
        ).getTokenValue();
    }

    private void addRefreshCookie(
            HttpServletResponse response,
            String refreshToken) {

        ResponseCookie cookie = ResponseCookie
                .from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(refreshTokenExpiration)
                .build();

        response.addHeader(
                "Set-Cookie",
                cookie.toString()
        );
    }

    private void clearRefreshCookie(
            HttpServletResponse response) {

        ResponseCookie cookie = ResponseCookie
                .from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(0)
                .build();

        response.addHeader(
                "Set-Cookie",
                cookie.toString()
        );
    }

    private String getClientIp(
            HttpServletRequest request) {

        String forwarded =
                request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}