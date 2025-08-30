package com.whisent.kubeloader.mixinjs.dsl;

import com.whisent.kubeloader.mixinjs.dsl.wrapper.MixinAtEnum;
import com.whisent.kubeloader.mixinjs.dsl.wrapper.MixinTypeEnum;
import dev.latvian.mods.kubejs.typings.Info;

/**
 * This class is only used to run through
 * JavaScript and has no actual functionality.
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
    @Info("the mixin target's name,like 'testFunc' or 'StartupEvents.init'")
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
}
