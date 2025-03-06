package com.whisent.kubeloader.files;

import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class jarFix {
    private final JarFile jarFile;
    private final JarEntry jarEntry;
    private final ScriptType scriptType;

    public jarFix(JarFile jarFile, JarEntry jarEntry, ScriptType scriptType) {
        this.jarFile = jarFile;
        this.jarEntry = jarEntry;
        this.scriptType = scriptType;
    }
    public JarFile getJarFile() {
        return jarFile;
    }
    public JarEntry getJarEntry() {
        return jarEntry;
    }
    public ScriptType getScriptType() {
        return scriptType;
    }
}
