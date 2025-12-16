package com.whisent.kubeloader.graal;

import com.whisent.kubeloader.graal.context.IdentifiedContext;
import com.whisent.kubeloader.graal.context.TestEvent;
import com.whisent.kubeloader.graal.context.TestObject;
import com.whisent.kubeloader.utils.KLUtil;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.graalvm.polyglot.Context;

public class TestClass {
    public static void main(String[] args) {
        var ctx = new IdentifiedContext("testContext", null);
        GraalApi.registerBinding(ctx.getContext(), "KLUtils", KLUtil.class);
        GraalApi.registerBinding(ctx.getContext(), "TestEvent", TestEvent.class);
        GraalApi.registerBinding(ctx.getContext(), "TestObject", new TestObject());
        String filePath = "testpack:test.js";

    }
}
