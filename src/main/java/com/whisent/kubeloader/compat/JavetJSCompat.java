package com.whisent.kubeloader.compat;

import com.whisent.javetjs.babel.BabelWrapper;
import com.whisent.javetjs.core.JavetJSApi;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraftforge.fml.ModList;

public class JavetJSCompat {
    public static boolean isLoaded = false;
    public static void init() {
        if (ModList.get().isLoaded("javetjs")) {
            isLoaded = false;
        }

        //JavetJSApi.eval(BabelWrapper.runtime, ScriptType.STARTUP,"console.log","test.js");
    }
}
