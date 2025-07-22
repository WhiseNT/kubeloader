package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.impl.mixin_interface.NbtBuilder;
import dev.latvian.mods.kubejs.core.ItemKJS;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import dev.latvian.mods.rhino.util.RemapForJS;
import dev.latvian.mods.rhino.util.RemapPrefixForJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Item.class,priority = 1000)
@RemapPrefixForJS("kjs$")
public abstract class ItemMixin implements ItemKJS {
    private ItemBuilder kjs$itemBuilder;

    @Inject(method = "getDefaultInstance", at = @At("RETURN"), cancellable = true)
    private void getDefaultInstanceMixin(CallbackInfoReturnable<ItemStack> cir) {
        if (kjs$itemBuilder != null && ((NbtBuilder)kjs$itemBuilder).getDefaultNbt() != null) {
            ItemStack itemStack = cir.getReturnValue();
            itemStack.setTag(((NbtBuilder)kjs$itemBuilder).getDefaultNbt());
            cir.setReturnValue(itemStack);
        }
    }
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
