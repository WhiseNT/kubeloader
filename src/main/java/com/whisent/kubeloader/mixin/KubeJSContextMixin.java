package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.ConfigManager;
import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.compat.RhinoBuiltinPolyfills;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在每个 KubeJS 的 Rhino 上下文建好之后，往它的全局作用域里补上 Rhino 缺失的内建
 * （{@code globalThis}、{@code [].at(-1)}、{@code Object.hasOwn} 等，见
 * {@link RhinoBuiltinPolyfills}）。
 *
 * <p>挂在构造函数尾部而不是求值脚本之前：那时 {@code topLevelScope} 已经建好，
 * 且还没有任何脚本被求值，所以用户脚本一上来就能用这些写法，也不会每个脚本都重装一遍。
 * 同一作用域重复安装本身也是安全的（补丁里每项都先判断是否已存在）。</p>
 */
@Mixin(value = KubeJSContext.class, remap = false)
public class KubeJSContextMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void kubeLoader$installBuiltinPolyfills(CallbackInfo ci) {
        if (!ConfigManager.shouldPolyfillBuiltins()) {
            return;
        }
        KubeJSContext self = (KubeJSContext) (Object) this;
        try {
            RhinoBuiltinPolyfills.install(self, self.topLevelScope);
        } catch (Throwable t) {
            // 兼容层自己坏掉不该拦住脚本加载；补丁本身是否完好由
            // polyfillRegressionCheck 这组回归用例把关
            Kubeloader.LOGGER.error("[KubeLoader] 安装 Rhino 内建补丁失败，脚本仍会继续加载", t);
        }
    }
}
