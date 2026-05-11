package com.whisent.kubeloader.scripts.modernjs.plugin.impl;

public interface TextPlugin {
    String syntax();

    boolean matches(String input);

    String apply(String input);
}
