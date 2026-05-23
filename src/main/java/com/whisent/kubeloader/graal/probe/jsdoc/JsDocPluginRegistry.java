package com.whisent.kubeloader.graal.probe.jsdoc;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class JsDocPluginRegistry {
    private final Map<String, JsDocTagPlugin> plugins = new LinkedHashMap<>();

    public void register(JsDocTagPlugin plugin) {
        for (String tag : plugin.tags()) {
            plugins.put(tag.toLowerCase(Locale.ROOT), plugin);
        }
    }

    public void apply(JsDocMetadata metadata, String tagLine, JsDocParseContext context) {
        String trimmed = tagLine == null ? "" : tagLine.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("@")) {
            return;
        }

        int delimiterIndex = trimmed.indexOf(' ');
        String tag = delimiterIndex < 0 ? trimmed : trimmed.substring(0, delimiterIndex);
        JsDocTagPlugin plugin = plugins.get(tag.toLowerCase(Locale.ROOT));
        if (plugin != null) {
            plugin.apply(metadata, trimmed, context);
        }
    }
}