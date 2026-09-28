package com.notifyhub.security.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

    @Test
    void shouldRejectShortJwtSecret() throws Exception {
        JwtConfig config = new JwtConfig();
        Field field = JwtConfig.class.getDeclaredField("jwtSecret");
        field.setAccessible(true);
        field.set(config, "short-secret");

        assertThrows(IllegalStateException.class, config::validateSecret);
    }
}
