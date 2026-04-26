package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.event.kjs.KubeLoaderEvents;
import com.whisent.kubeloader.event.kjs.TridentReleased;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TridentItem.class)
public abstract class TridentItemMixin {
    @Shadow public abstract int getUseDuration(ItemStack stack, LivingEntity entity);

    @Inject(
            method = "releaseUsing",
            at = @At("HEAD"),
            cancellable = true
    )
        private void kubeLoader$releaseUsing(ItemStack stack, Level level,
                         LivingEntity entity, int timeLeft,
                                         CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }

        int duration = this.getUseDuration(stack, entity) - timeLeft;
        if (duration < 10) {
            return;
        }

        int riptideLevel = EnchantmentHelper.getTagEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.RIPTIDE),
                stack
        );

        EventResult result;
        if (!level.isClientSide) {
            result = KubeLoaderEvents.TRIDENT_RELEASE_USING.post(ScriptType.SERVER,
                    new TridentReleased(stack, entity, level, timeLeft, riptideLevel));
        } else {
            result = KubeLoaderEvents.TRIDENT_RELEASE_USING.post(ScriptType.CLIENT,
                    new TridentReleased(stack, entity, level, timeLeft, riptideLevel));
        }
        if (result.interruptFalse()) {
            ci.cancel();
        }

    }

}
