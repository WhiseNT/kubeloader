package com.whisent.kubeloader.graal.probe.jsdoc;

import com.whisent.kubeloader.graal.probe.jsdoc.tag.CallbackTagPlugin;
import com.whisent.kubeloader.graal.probe.jsdoc.tag.ParamTagPlugin;
import com.whisent.kubeloader.graal.probe.jsdoc.tag.PropertyTagPlugin;
import com.whisent.kubeloader.graal.probe.jsdoc.tag.ReadonlyTagPlugin;
import com.whisent.kubeloader.graal.probe.jsdoc.tag.ReturnsTagPlugin;
import com.whisent.kubeloader.graal.probe.jsdoc.tag.TypeTagPlugin;
import com.whisent.kubeloader.graal.probe.jsdoc.tag.TypedefTagPlugin;

public final class JsDocParser {
    private final JsDocPluginRegistry registry;
    private final JsDocParseContext context;

    public JsDocParser(JsDocPluginRegistry registry, JsDocParseContext context) {
        this.registry = registry;
        this.context = context;
    }

    public static JsDocParser createDefault() {
        JsDocPluginRegistry registry = new JsDocPluginRegistry();
        registry.register(new ParamTagPlugin());
        registry.register(new ReturnsTagPlugin());
        registry.register(new TypeTagPlugin());
        registry.register(new ReadonlyTagPlugin());
        registry.register(new TypedefTagPlugin());
        registry.register(new PropertyTagPlugin());
        registry.register(new CallbackTagPlugin());
        return new JsDocParser(registry, new JsDocParseContext());
    }

    public JsDocMetadata parse(String comment) {
        if (comment == null) {
            return null;
        }

        JsDocMetadata metadata = new JsDocMetadata();
        StringBuilder description = new StringBuilder();

        for (String line : comment.split("\\R")) {
            String trimmed = line.stripLeading();

            if (trimmed.startsWith("/**")) {
                trimmed = trimmed.substring(3).stripLeading();
            }

            if (trimmed.endsWith("*/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 2).stripTrailing();
            }

            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).stripLeading();
            }

            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("@")) {
                registry.apply(metadata, trimmed, context);
                continue;
            }

            if (description.length() > 0) {
                description.append("\n");
            }
            description.append(trimmed);
        }

        if (description.length() > 0) {
            if (metadata.description() == null || metadata.description().isBlank()) {
                metadata.setDescription(description.toString());
            } else {
                metadata.setDescription(description + "\n" + metadata.description());
            }
        }

        return metadata.isEmpty() ? null : metadata;
    }
}