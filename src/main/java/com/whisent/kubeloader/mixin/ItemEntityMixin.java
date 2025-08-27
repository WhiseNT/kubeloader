package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.event.kjs.ItemEntityEventJS;
import com.whisent.kubeloader.event.kjs.ItemEntityEvents;
import com.whisent.kubeloader.event.kjs.ItemEntityHurtEventJS;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemEntity.class)
public class ItemEntityMixin {
    @Shadow
    private int health;


    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void onItemEntityTickAfterPhysics(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity)(Object)this;
        Vec3 pos = itemEntity.position();
        Level level = itemEntity.level();
        if (level.isClientSide()) {
            ItemEntityEvents.ITEM_ENTITY_TICK.post(ScriptType.CLIENT,itemEntity.getItem().getItem(), new ItemEntityEventJS(itemEntity, level, pos));
        } else {
            ItemEntityEvents.ITEM_ENTITY_TICK.post(ScriptType.SERVER,itemEntity.getItem().getItem(), new ItemEntityEventJS(itemEntity, level, pos));
        }


    }
    @Inject(
            method = "hurt",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        Level level = itemEntity.level();
        Vec3 pos = itemEntity.position();
        if (itemEntity.isRemoved() || itemEntity.isInvulnerableTo(source)) {
            cir.setReturnValue(false);
            return;
        }
        if (level.isClientSide) {
            cir.setReturnValue(true);
            return;
        }
        ItemEntityHurtEventJS event = new ItemEntityHurtEventJS(itemEntity, level, pos, source, amount);
        EventResult result = ItemEntityEvents.ITEM_ENTITY_HURT.post(event, itemEntity.getItem().getItem());
        if (result.interruptTrue()) {
            cir.setReturnValue(false);
            return;
        }
        float finalAmount = event.amount;
        itemEntity.hurtMarked = true;
        this.health = (int)(this.health - finalAmount);
        itemEntity.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
        if (this.health <= 0) {
            itemEntity.getItem().onDestroyed(itemEntity, source);
            itemEntity.discard();
        }
        cir.setReturnValue(true);
    }
}
