package com.whisent.kubeloader.impl.mixin;

import com.whisent.kubeloader.definition.meta.Engine;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptSource;

import java.io.IOException;
import java.util.Optional;
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
    
    /**
     * 获取脚本指定的引擎
     * @return 引擎类型，如果未指定则返回Optional.empty()
     */
    Optional<Engine> kubeLoader$getEngine();
    
    /**
     * 设置脚本使用的引擎
     * @param engine 引擎类型
     */
    void kubeLoader$setEngine(Engine engine);
}