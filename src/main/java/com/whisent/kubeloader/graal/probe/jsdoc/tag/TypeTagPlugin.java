package com.whisent.kubeloader.graal.probe.jsdoc.tag;

import com.whisent.kubeloader.graal.probe.jsdoc.JsDocMetadata;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocParseContext;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocTagPlugin;

import java.util.List;

public final class TypeTagPlugin implements JsDocTagPlugin {
    @Override
    public List<String> tags() {
        return List.of("@type");
    }

    @Override
    public void apply(JsDocMetadata metadata, String tagLine, JsDocParseContext context) {
        JsDocParseContext.TypedTagPayload payload = context.readTypedPayload(tagLine, "@type");
        if (payload == null) {
            return;
        }

        metadata.setType(payload.type());
        if (payload.readonly()) {
            metadata.setReadonly(true);
        }
    }
}