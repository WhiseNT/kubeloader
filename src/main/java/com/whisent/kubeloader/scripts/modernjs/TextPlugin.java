package com.whisent.kubeloader.scripts.modernjs;

interface TextPlugin {
    String syntax();

    boolean matches(String input);

    String apply(String input);
}
