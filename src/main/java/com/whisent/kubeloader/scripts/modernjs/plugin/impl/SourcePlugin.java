package com.whisent.kubeloader.scripts.modernjs.plugin.impl;

import com.whisent.kubeloader.scripts.modernjs.SourceTransformResult;

public interface SourcePlugin {
    String syntax();

    boolean matches(String trimmedLine);

    SourceTransformResult transform(String[] lines, int startIndex);
}
