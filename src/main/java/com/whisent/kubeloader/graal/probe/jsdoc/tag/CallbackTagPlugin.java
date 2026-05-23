package com.whisent.kubeloader.graal.probe.jsdoc.tag;

import com.whisent.kubeloader.graal.probe.jsdoc.JsDocMetadata;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocParseContext;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocTagPlugin;

import java.util.List;

public final class CallbackTagPlugin implements JsDocTagPlugin {
    @Override
    public List<String> tags() {
        return List.of("@callback");
    }

    @Override
    public void apply(JsDocMetadata metadata, String tagLine, JsDocParseContext context) {
        JsDocParseContext.NamedTagPayload payload = context.readNamedPayload(tagLine, "@callback");
        if (payload == null) {
            return;
        }

        metadata.setCallbackName(payload.name());
        if (payload.description() != null && (metadata.description() == null || metadata.description().isBlank())) {
            metadata.setDescription(payload.description());
        }
    }
}