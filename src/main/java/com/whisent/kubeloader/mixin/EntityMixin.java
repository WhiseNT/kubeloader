package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.event.kjs.ItemEntityEventJS;
import com.whisent.kubeloader.event.kjs.ItemEntityEvents;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "discard",at = @At(value = "HEAD"))
    public void onRemove(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ItemEntity itemEntity) {
            if (self.level().isClientSide) {
                ItemEntityEvents.ITEM_ENTITY_REMOVED.post(
                        ScriptType.CLIENT
                        ,itemEntity.getItem().getItem(),
                        new ItemEntityEventJS(itemEntity, self.level(), self.position()));
            } else {
                ItemEntityEvents.ITEM_ENTITY_REMOVED.post(
                        ScriptType.SERVER
                        ,itemEntity.getItem().getItem(),
                        new ItemEntityEventJS(itemEntity, self.level(), self.position()));
            }
        }
    }

}
