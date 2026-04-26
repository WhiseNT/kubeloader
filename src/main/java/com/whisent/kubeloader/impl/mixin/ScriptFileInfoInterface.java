package com.whisent.kubeloader.impl.mixin;

import com.whisent.kubeloader.definition.meta.Engine;

import java.util.Optional;
import java.util.Set;

public interface ScriptFileInfoInterface {

    String kubeLoader$getTargetPath();

    void kubeLoader$setTargetPath(String targetPath);

    Set<String> kubeLoader$getSides();
    
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