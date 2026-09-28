package com.notifyhub.template.renderer;

import com.notifyhub.exception.MissingTemplateDataException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateRendererImpl implements TemplateRenderer {
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    @Override
    public String render(String template, Map<String, Object> data) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("Template cannot be empty");
        }
        if (data == null) {
            throw new MissingTemplateDataException(
                    "Template data cannot be null"
            );
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer renderedTemplate = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            if (!data.containsKey(key)) {
                throw new MissingTemplateDataException(
                        "Missing template data: " + key
                );
            }
            String value = String.valueOf(data.get(key));
            matcher.appendReplacement(renderedTemplate,Matcher.quoteReplacement(value));
        }
        matcher.appendTail(renderedTemplate);
        return renderedTemplate.toString();
    }
}