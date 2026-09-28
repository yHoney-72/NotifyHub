package com.notifyhub.template.renderer;

import com.notifyhub.exception.MissingTemplateDataException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateRendererImplTest {

    private final TemplateRenderer renderer =
            new TemplateRendererImpl();

    @Test
    void shouldRenderTemplateSuccessfully() {

        String template =
                "Hello {{name}}, welcome to {{company}}!";

        Map<String, Object> data = Map.of(
                "name", "Honey",
                "company", "NotifyHub"
        );

        String result = renderer.render(template, data);

        assertEquals(
                "Hello Honey, welcome to NotifyHub!",
                result
        );
    }

    @Test
    void shouldThrowExceptionWhenTemplateDataIsMissing() {

        String template =
                "Hello {{name}}, your order is {{orderId}}";

        Map<String, Object> data = Map.of(
                "name", "Honey"
        );

        assertThrows(
                MissingTemplateDataException.class,
                () -> renderer.render(template, data)
        );
    }

    @Test
    void shouldRenderMultiplePlaceholders() {

        String template =
                "Hi {{name}}, order {{orderId}} from {{company}} is ready.";

        Map<String, Object> data = Map.of(
                "name", "Honey",
                "orderId", "ORD-101",
                "company", "NotifyHub"
        );

        String result = renderer.render(template, data);

        assertEquals(
                "Hi Honey, order ORD-101 from NotifyHub is ready.",
                result
        );
    }
}