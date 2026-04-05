package com.whisent.kubeloader.klm.dsl;

import com.whisent.kubeloader.klm.dsl.wrapper.MixinAtEnum;
import com.whisent.kubeloader.klm.dsl.wrapper.MixinTypeEnum;
import dev.latvian.mods.kubejs.typings.Info;

/**
 * This class is only used to run through
 * JavaScript and has no actual functionality.
 *
 * <h3>定位语法</h3>
 * <pre>
 * .type() 已指定类型，.in() 只需写名称
 * 示例: 'myFunc', 'console.log'
 *
 * .offset(行号) - 在函数体内第几行注入（从0开始）
 * </pre>
 */
public class ShadowMixinObject {
    public ShadowMixinObject() {
    }

    public static ShadowMixinObject type(MixinTypeEnum type) {
        return new ShadowMixinObject();
    }
    @Info("like @At in mixin,inject into 'head' or 'tail' of function")
    public ShadowMixinObject at(MixinAtEnum at) {
        return this;
    }
    @Info("""
        the mixin target.
        Simple: 'functionName'
        Locator: 'function:myFunc', 'call:console.log', 'for', 'if'
        """)
    public ShadowMixinObject in(String target) {
        return this;
    }
    @Info("if the target file has multiple same events, use this to locate the right one,\n start from 0")
    public ShadowMixinObject locate(int location) {
        return this;
    }
    @Info("the priority of this mixin, higher number means higher priority,\ndefault is 0")
    public ShadowMixinObject priority(int priority) {
        return this;
    }
    @Info("put your code here,create a function to wrap your code block")
    public ShadowMixinObject inject(Object action) {
        return this;
    }
    @Info("inject at specific line offset inside the function body (0-based)")
    public ShadowMixinObject offset(int offset) {
        return this;
    }
}
