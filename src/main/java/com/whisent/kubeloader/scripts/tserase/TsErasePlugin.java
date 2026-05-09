package com.whisent.kubeloader.scripts.tserase;

public interface TsErasePlugin {
    String syntax();

    boolean matches(TsEraseContext ctx);
    boolean apply(TsEraseContext ctx);
}
