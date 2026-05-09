package com.whisent.kubeloader.scripts.modernjs;

interface SourcePlugin {
    String syntax();

    boolean matches(String trimmedLine);

    SourceTransformResult transform(String[] lines, int startIndex);
}
