package com.whisent.kubeloader.impl.mixin;

import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptSource;

import java.io.IOException;
import java.util.Set;

public interface ScriptFileInfoInterface {

    String kubeLoader$getTargetPath();

    void kubeLoader$setTargetPath(String targetPath);
    
    /**
     * 检查脚本是否应该在当前环境加载
     * @return 如果应该加载返回true，否则返回false
     */
    boolean shouldLoad(ScriptManager scriptManager, ScriptSource source) throws IOException;

    Set<String> kubeLoader$getSides();
    String mixin = "";
}