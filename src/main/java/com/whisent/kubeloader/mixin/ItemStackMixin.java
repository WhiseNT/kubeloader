package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.event.kjs.ItemHurtEventJS;
import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import dev.latvian.mods.kubejs.event.EventResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.function.Consumer;

@Mixin(value = ItemStack.class, priority = 1000)
public abstract class ItemStackMixin {
    @Shadow public abstract Item getItem();

    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void onHurt(int originalDamage, ServerLevel level, @Nullable LivingEntity entity,
                        Consumer<Item> onBreak, CallbackInfo ci) {
        ServerPlayer player = entity instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        EventResult result = KubeLoaderEvents.ITEM_HURT.post(
                new ItemHurtEventJS((ItemStack) (Object) this, player, originalDamage),this.getItem().asItem());
        if (result.interruptFalse()) {
            ci.cancel();
        }
    }
}
