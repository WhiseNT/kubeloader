package com.whisent.kubeloader.scripts.modernjs;

final class SourceTransformResult {
    final String output;
    final int endLineIndex;

    SourceTransformResult(String output, int endLineIndex) {
        this.output = output;
        this.endLineIndex = endLineIndex;
    }
}
