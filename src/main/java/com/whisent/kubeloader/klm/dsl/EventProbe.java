package com.whisent.kubeloader.klm.dsl;

import java.util.List;

public class EventProbe {
    private String eventName;
    private String position;
    private String functionBody;

    private EventProbe() {
    }

    public static EventProbe on(String eventName) {
        EventProbe probe = new EventProbe();
        probe.eventName = eventName;
        return probe;
    }

    public EventProbe at(String position) {
        this.position = position;
        return this;
    }

    public EventProbe inject(String functionBody) {
        // 从函数声明中提取函数体
        this.functionBody = EventProbeTextProcessor.extractFunctionBody(functionBody);
        return this;
    }
    
    public EventProbe injectFunctionBody(String functionBody) {
        this.functionBody = functionBody;
        return this;
    }

    public EventProbeDSL build() {
        EventProbeDSL dsl = new EventProbeDSL();
        dsl.setEventName(this.eventName);
        dsl.setPosition(this.position);
        dsl.setFunctionBody(this.functionBody);
        return dsl;
    }
    public static EventProbeDSL buildFromDSL(MixinDSL mixinDSL) {
        EventProbeDSL dsl = new EventProbeDSL();
        dsl.setEventName(mixinDSL.getTarget());
        dsl.setPosition(mixinDSL.getAt());
        dsl.setFunctionBody(mixinDSL.getAction());
        dsl.setTargetLocation(mixinDSL.getTargetLocation());
        return dsl;

    }

    public static List<EventProbeDSL> parse(String source) {
        return EventProbeDSLParser.parse(source);
    }
    
    /**
     * 应用DSL到源代码
     * 
     * @param sourceCode 源代码
     * @return 应用DSL后的源代码
     */
    public String applyTo(String sourceCode) {
        EventProbeDSL dsl = build();
        return EventProbeTextProcessor.applyDSL(sourceCode, dsl);
    }

    public static String applyTo(String sourceCode,MixinDSL dsl) {
        return EventProbeTextProcessor.applyDSL(sourceCode,buildFromDSL(dsl));
    }
}