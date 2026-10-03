package com.notifyhub.security.OAuth;

import com.notifyhub.security.entity.AppUser;
import com.notifyhub.security.repository.UserRepository;
import com.notifyhub.security.service.RefreshTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "notifyhub.oauth2.google.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class GoogleOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenService refreshTokenService;

    @Value("${security.jwt.expiration}")
    private long accessTokenExpiration;

    @Value("${security.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute("email");

        if (email == null || email.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"message\":\"Google account email not available\"}"
            );
            return;
        }

        AppUser user = userRepository.findByUsername(email)
                .orElseGet(() -> createGoogleUser(email));

        String accessToken = generateAccessToken(user);

        String refreshToken = refreshTokenService.createRefreshToken(
                user,
                request.getHeader("User-Agent"),
                getClientIp(request)
        );

        addRefreshCookie(response, refreshToken);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"message\":\"Google login successful\",\"accessToken\":\""
                        + accessToken
                        + "\"}"
        );

        clearAuthenticationAttributes(request);
    }

    private AppUser createGoogleUser(String email) {
        String randomPassword = UUID.randomUUID().toString();

        AppUser user = new AppUser(
                email,
                passwordEncoder.encode(randomPassword),
                "USER"
        );

        return userRepository.save(user);
    }

    private String generateAccessToken(AppUser user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenExpiration))
                .claim("roles", "ROLE_" + user.getRole())
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(claims)
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

        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}