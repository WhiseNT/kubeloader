package com.whisent.kubeloader.scripts.modernjs;

public class SourceTransformResult {
    final String output;
    final int endLineIndex;

    public SourceTransformResult(String output, int endLineIndex) {
        this.output = output;
        this.endLineIndex = endLineIndex;
    }
}
