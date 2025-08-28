package com.whisent.kubeloader.impl.mixin_interface;

import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptSource;

import java.io.IOException;

public interface ScriptFileInfoInterface {

    String getTargetPath();

    void setTargetPath(String targetPath);
    
    /**
     * 检查脚本是否应该在当前环境加载
     * @return 如果应该加载返回true，否则返回false
     */
    boolean shouldLoad(ScriptManager scriptManager, ScriptSource source) throws IOException;

    String mixin = "";
}