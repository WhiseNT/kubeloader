package com.whisent.kubeloader.graal.context;

import com.whisent.kubeloader.graal.GraalApi;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.graalvm.polyglot.Context;

public class IdentifiedContext implements AutoCloseable {
    public String id;
    public ScriptType type;
    public Context context;
    public IdentifiedContext(String id, ScriptType type) {
        this.id = id;
        this.type = type;
        this.context = GraalApi.createContext();;
        System.out.println("创建Graal上下文: " + this.id + " 类型: " + this.type);
    }
    public String getId() {
        return id;
    }
    public ScriptType getType() {
        return type;
    }
    public Context getContext() {
        return context;
    }
    public void eval(String script) {
        GraalApi.eval(this.context, script);
    }

    @Override
    public void close() {

    }
    @Override
    public String toString() {
        return "IdentifiedContext{id='" + id + "', type=" + type + "}";
    }
}
