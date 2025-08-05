package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin_interface.NbtBuilder;
import dev.latvian.mods.kubejs.core.ItemKJS;
import dev.latvian.mods.kubejs.item.ItemBuilder;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Item.class,priority = 1000)
public abstract class ItemMixin implements ItemKJS {
    private ItemBuilder kjs$itemBuilder;

    @Inject(method = "getDefaultInstance", at = @At("RETURN"), cancellable = true)
    private void getDefaultInstanceMixin(CallbackInfoReturnable<ItemStack> cir) {
        if (kjs$itemBuilder != null && ((NbtBuilder)kjs$itemBuilder).getDefaultNbt() != null) {
            ItemStack itemStack = cir.getReturnValue();
            itemStack.setTag(((NbtBuilder)kjs$itemBuilder).getDefaultNbt());
            cir.setReturnValue(itemStack);
        } else if (defaultNbt != null) {
            ItemStack itemStack = cir.getReturnValue();
            itemStack.setTag(defaultNbt);
            cir.setReturnValue(itemStack);
        }
    }
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

    @Unique
    private void setDefaultNbt(CompoundTag nbt) {
        if (kjs$itemBuilder != null) {
            ((NbtBuilder)kjs$itemBuilder).setDefaultNbt(nbt);
        }
    }
    @Override
    public void kjs$setItemBuilder(ItemBuilder b) {
        kjs$itemBuilder = b;
    }



}
