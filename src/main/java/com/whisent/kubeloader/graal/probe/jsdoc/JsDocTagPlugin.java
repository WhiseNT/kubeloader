package com.whisent.kubeloader.graal.probe.jsdoc;

import java.util.List;

public interface JsDocTagPlugin {
    List<String> tags();

    void apply(JsDocMetadata metadata, String tagLine, JsDocParseContext context);
}