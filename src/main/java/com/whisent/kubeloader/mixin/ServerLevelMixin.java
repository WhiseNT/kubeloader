package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.event.kjs.ItemEntityEventJS;
import com.whisent.kubeloader.event.kjs.ItemEntityEvents;
import dev.latvian.mods.kubejs.event.EventResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    public void addFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ItemEntity itemEntity) {
            EventResult result = ItemEntityEvents.ITEM_ENTITY_SPAWN.post(new ItemEntityEventJS(itemEntity, entity.level(), entity.position()),itemEntity.getItem().getItem());
            if (result.interruptFalse()) {
                cir.cancel();
            }
        }
    }
}
