package com.whisent.kubeloader.utils;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import net.minecraftforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceUtil {
    private final ConsoleJS console;
    private final Map<String, Long> timers = new ConcurrentHashMap<>();
    private final Map<String, Integer> callCounts = new ConcurrentHashMap<>();
    public PerformanceUtil(ScriptType type) {
        this.console = switch (type) {
            case CLIENT -> ConsoleJS.CLIENT;
            case SERVER -> ConsoleJS.SERVER;
            case STARTUP -> ConsoleJS.STARTUP;
        };
    }

    public long getNanoTime() {
        return System.nanoTime();
    }
    public void start(String label) {
        timers.put(label, System.nanoTime());
        callCounts.merge(label, 1, Integer::sum);
    }

    public double end(String label) {
        Long start = timers.get(label);
        if (start == null) {
            return -1;
        }
        long end = System.nanoTime();
        double ms = (end - start) / 1_000_000.0;
        timers.remove(label);
        return ms;
    }

    public int getCallCount(String label) {
        return callCounts.getOrDefault(label, 0);
    }

    public void reset(String label) {
        timers.remove(label);
        callCounts.remove(label);
    }

    public void resetAll() {
        timers.clear();
        callCounts.clear();
    }
    public void log(String label) {
        double duration = end(label);
        int count = getCallCount(label);
        if (duration >= 0) {
            console.log(String.format("Timer: %s | Time: %.4f ms | Calls: %d", label, duration, count));
        } else {
            console.log("No timer started for: " + label);
        }
    }

}
