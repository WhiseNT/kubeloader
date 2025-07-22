package com.whisent.kubeloader.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "move",at = @At(value = "HEAD"), cancellable = true)
    public void onMove(MoverType moverType, Vec3 movement, CallbackInfo ci) {
        /*
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (self.getPassengers().isEmpty()) {
            return;
        }
        for (Entity passenger : self.getPassengers()) {
            AABB passengerBox = passenger.getBoundingBox().move(movement);
            if (!level.noCollision(passenger,passengerBox)) {
                ci.cancel();
                return;
            }
        }

         */
    }

}
