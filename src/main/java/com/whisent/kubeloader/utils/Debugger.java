package com.whisent.kubeloader.utils;

public class Debugger {
    public static boolean enable = true;
    public static void out(String msg) {
        if (enable) {
            System.out.println(msg);
        }
    }
}
