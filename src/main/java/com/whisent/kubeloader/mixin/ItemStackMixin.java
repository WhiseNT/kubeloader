package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.event.kjs.ItemHurtEventJS;
import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import dev.latvian.mods.kubejs.event.EventResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;

@Mixin(value = ItemStack.class, priority = 1000)
public abstract class ItemStackMixin {
    @Shadow public abstract Item getItem();

    /**
     * 在耐久度消耗逻辑中注入，用于触发自定义事件。
     * 使用 @Inject 和 @LocalCapture 获取局部变量。
     */
    @Inject(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"
            ),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD)
    private <T> void onHurt(int originalDamage, RandomSource random, @Nullable ServerPlayer player,
                            CallbackInfoReturnable<Boolean> cir,
                            int i
                            ) {
        EventResult result = KubeLoaderEvents.ITEM_HURT.post(
                new ItemHurtEventJS((ItemStack) (Object) this, player, originalDamage),this.getItem().asItem());
        if (result.interruptFalse()) {
            cir.setReturnValue(false);
        }
    }
}
