package com.whisent.kubeloader.utils;

import java.util.ArrayList;
import java.util.List;

public class Debugger {
    public static boolean enable = true;

    public static List<DebuggerLogTypes> logTypes = new ArrayList<>();

    public static void setEnable(boolean enable) {
        Debugger.enable = enable;
    }
    public static void out(String msg) {
        if (enable) {
            System.out.println(msg);
        }
    }
}
