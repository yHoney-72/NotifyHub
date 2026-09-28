package com.notifyhub.template.renderer;

import java.util.Map;

public interface TemplateRenderer {

    String render(String template, Map<String, Object> data);
}