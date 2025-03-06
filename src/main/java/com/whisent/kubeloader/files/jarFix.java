package com.whisent.kubeloader.files;

import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class jarFix {
    private final JarFile jarFile;
    private final ScriptType scriptType;

    public jarFix(JarFile jarFile, ScriptType scriptType) {
        this.jarFile = jarFile;
        this.scriptType = scriptType;
    }
    public JarFile getJarFile() {
        return jarFile;
    }
    public ScriptType getScriptType() {
        return scriptType;
    }
}
