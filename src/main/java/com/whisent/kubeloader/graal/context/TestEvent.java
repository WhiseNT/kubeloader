package com.whisent.kubeloader.graal.context;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class TestEvent {
    public static Set<Consumer<String>> listeners = new HashSet<>();
    public static void onTest(Consumer<String> consumer) {
        listeners.add(consumer);
    }
}
