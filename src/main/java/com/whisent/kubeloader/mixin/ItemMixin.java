package com.whisent.kubeloader.mixin;

import dev.latvian.mods.kubejs.core.ItemKJS;
import dev.latvian.mods.kubejs.item.ItemBehavior;
import dev.latvian.mods.rhino.util.RemapForJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KubeJS 2101.7.2-build.374+ 将 {@code ItemBuilder.ReleaseUsingCallback} 重构进了
 * {@link ItemBehavior}（ItemBuilder 不再持有各回调字段，改为 ItemBehavior 聚合）。
 * 本 Mixin 通过 ItemKJS 的 kjs$getItemBehavior()/kjs$setItemBehavior() 读写 behavior，
 * 保持 item.setReleaseUsing(cb) 的 JS API 语义不变。
 */
@Mixin(value = Item.class, priority = 1000)
public abstract class ItemMixin implements ItemKJS {

    @Unique
    @RemapForJS("setReleaseUsing")
    public void kubeLoader$setReleaseUsing(ItemBehavior.ReleaseUsingCallback callback) {
        var behavior = kjs$getItemBehavior();
        if (behavior == null) {
            behavior = new ItemBehavior();
            kjs$setItemBehavior(behavior);
        }
        behavior.releaseUsing = callback;
    }

    @Unique
    public ItemBehavior.ReleaseUsingCallback kubeLoader$getReleaseUsing() {
        var behavior = kjs$getItemBehavior();
        return behavior == null ? null : behavior.releaseUsing;
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    public void releaseUsingMixin(ItemStack stack, Level level, LivingEntity entity, int timeLeft, CallbackInfo ci) {
        var behavior = kjs$getItemBehavior();
        if (behavior != null && behavior.releaseUsing != null) {
            System.out.print("releaseUsingMixin");
            behavior.releaseUsing.releaseUsing(stack, level, entity, timeLeft);
            ci.cancel();
        }
    }

    @Unique
    public CompoundTag defaultNbt;
}