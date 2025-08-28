package com.whisent.kubeloader.mixinjs.dsl;

import com.whisent.kubeloader.mixinjs.dsl.wrapper.MixinAtEnum;
import com.whisent.kubeloader.mixinjs.dsl.wrapper.MixinTypeEnum;

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
    public ShadowMixinObject at(MixinAtEnum at) {
        return this;
    }
    public ShadowMixinObject in(String target) {
        return this;
    }
    public ShadowMixinObject inject(Object action) {
        return this;
    }
}
