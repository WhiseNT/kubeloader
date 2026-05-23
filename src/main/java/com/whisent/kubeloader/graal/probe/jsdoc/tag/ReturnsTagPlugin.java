package com.whisent.kubeloader.graal.probe.jsdoc.tag;

import com.whisent.kubeloader.graal.probe.jsdoc.JsDocMetadata;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocParseContext;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocTagPlugin;

import java.util.List;

public final class ReturnsTagPlugin implements JsDocTagPlugin {
    @Override
    public List<String> tags() {
        return List.of("@returns", "@return");
    }

    @Override
    public void apply(JsDocMetadata metadata, String tagLine, JsDocParseContext context) {
        String tag = tagLine.startsWith("@returns") ? "@returns" : "@return";
        JsDocParseContext.TypedTagPayload payload = context.readTypedPayload(tagLine, tag);
        if (payload == null) {
            return;
        }

        metadata.setReturnsType(payload.type());
        metadata.setReturnsDescription(payload.description());
    }
}