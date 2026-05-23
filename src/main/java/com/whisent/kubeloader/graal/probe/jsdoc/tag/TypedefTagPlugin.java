package com.whisent.kubeloader.graal.probe.jsdoc.tag;

import com.whisent.kubeloader.graal.probe.jsdoc.JsDocMetadata;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocParseContext;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocTagPlugin;

import java.util.List;

public final class TypedefTagPlugin implements JsDocTagPlugin {
    @Override
    public List<String> tags() {
        return List.of("@typedef");
    }

    @Override
    public void apply(JsDocMetadata metadata, String tagLine, JsDocParseContext context) {
        JsDocParseContext.NamedTagPayload payload = context.readNamedPayload(tagLine, "@typedef");
        if (payload == null) {
            return;
        }

        metadata.setTypedefName(payload.name());
        metadata.setTypedefType(payload.type());
        if (payload.description() != null && (metadata.description() == null || metadata.description().isBlank())) {
            metadata.setDescription(payload.description());
        }
        if (payload.readonly()) {
            metadata.setReadonly(true);
        }
    }
}