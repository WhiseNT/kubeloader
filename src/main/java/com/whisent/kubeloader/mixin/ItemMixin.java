package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin.NbtBuilder;
import dev.latvian.mods.kubejs.core.ItemKJS;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import dev.latvian.mods.rhino.util.RemapForJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Item.class,priority = 1000)
public abstract class ItemMixin implements ItemKJS {
    private ItemBuilder kjs$itemBuilder;

    
    @Unique
    public ItemBuilder.ReleaseUsingCallback kubeLoader$releaseUsing;
    @Unique
    @RemapForJS("setReleaseUsing")
    public void kubeLoader$setReleaseUsing(ItemBuilder.ReleaseUsingCallback callback) {
        this.kubeLoader$releaseUsing = callback;
    }
    @Unique
    public ItemBuilder.ReleaseUsingCallback kubeLoader$getReleaseUsing() {
        return this.kubeLoader$releaseUsing;
    }
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    public void releaseUsingMixin(ItemStack stack, Level level, LivingEntity entity, int timeLeft, CallbackInfo ci) {
        System.out.print("releaseUsingMixin");
        if (this.kubeLoader$releaseUsing != null) {
            this.kubeLoader$releaseUsing.releaseUsing(stack, level, entity, timeLeft);
            ci.cancel();
        }
    }


    @Unique
    public CompoundTag defaultNbt;


    @Override
    public void kjs$setItemBuilder(ItemBuilder b) {
        kjs$itemBuilder = b;
    }



}
