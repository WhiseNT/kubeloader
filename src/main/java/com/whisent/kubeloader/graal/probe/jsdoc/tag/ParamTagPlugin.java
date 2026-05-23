package com.whisent.kubeloader.graal.probe.jsdoc.tag;

import com.whisent.kubeloader.graal.probe.jsdoc.JsDocMetadata;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocParseContext;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocTagPlugin;

import java.util.List;

public final class ParamTagPlugin implements JsDocTagPlugin {
    @Override
    public List<String> tags() {
        return List.of("@param");
    }

    @Override
    public void apply(JsDocMetadata metadata, String tagLine, JsDocParseContext context) {
        JsDocParseContext.NamedTagPayload payload = context.readNamedPayload(tagLine, "@param");
        if (payload == null) {
            return;
        }

        metadata.addParam(new JsDocMetadata.JsDocParam(
                payload.name(),
                payload.type(),
                payload.description(),
                payload.optional(),
                payload.rest(),
                payload.defaultValue()
        ));
    }
}