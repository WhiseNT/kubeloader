package com.whisent.kubeloader.graal.probe.jsdoc.tag;

import com.whisent.kubeloader.graal.probe.jsdoc.JsDocMetadata;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocParseContext;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocTagPlugin;

import java.util.List;

public final class PropertyTagPlugin implements JsDocTagPlugin {
    @Override
    public List<String> tags() {
        return List.of("@property");
    }

    @Override
    public void apply(JsDocMetadata metadata, String tagLine, JsDocParseContext context) {
        JsDocParseContext.NamedTagPayload payload = context.readNamedPayload(tagLine, "@property");
        if (payload == null) {
            return;
        }

        metadata.addProperty(new JsDocMetadata.JsDocProperty(
                payload.name(),
                payload.type(),
                payload.description(),
                payload.optional(),
                payload.readonly()
        ));
    }
}