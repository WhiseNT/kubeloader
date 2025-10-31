package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import com.whisent.kubeloader.event.kjs.TridentReleased;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = TridentItem.class)
public class TridentItemMixin {
    @Inject(
            method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V",
            at = @At(
                    value = "INVOKE_ASSIGN", // 使用 INVOKE_ASSIGN 捕获方法调用的返回值
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getRiptide(Lnet/minecraft/world/item/ItemStack;)I"
            ),
            locals = LocalCapture.CAPTURE_FAILSOFT,
            cancellable = true
    )
    public void releaseUsing(ItemStack stack, Level level,
                             LivingEntity entity, int timeLeft,
                             CallbackInfo ci,Player player, int i,int j) {
        if (player == null) return;
        EventResult result;
        if (!level.isClientSide) {
            result = KubeLoaderEvents.TRIDENT_RELEASE_USING.post(ScriptType.SERVER,
                    new TridentReleased(stack, entity, level, timeLeft, j));
        } else {
            result = KubeLoaderEvents.TRIDENT_RELEASE_USING.post(ScriptType.CLIENT,
                    new TridentReleased(stack, entity, level, timeLeft, j));
        }
        if (result.interruptFalse()) {
            ci.cancel();
        }

    }

}
