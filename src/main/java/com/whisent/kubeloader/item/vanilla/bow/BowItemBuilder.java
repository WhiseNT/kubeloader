package com.whisent.kubeloader.item.vanilla.bow;

import com.whisent.kubeloader.item.vanilla.ConsumerContext;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import java.util.HashMap;
import java.util.Map;
import java.util.function.*;
@Deprecated
public class BowItemBuilder extends ItemBuilder {
    private int useDuration = 72000;
    private final Map<String, Boolean> superFlags = new HashMap<>();
    private final Map<String, Object> callbacks = new HashMap<>();
    private final Map<String, Object> suppliers = new HashMap<>();
    public CompoundTag nbt;

    public BowItemBuilder(ResourceLocation id) {
        super(id);
    }
    public ItemBuilder nbt(CompoundTag v) {
        this.nbt = v;
        return this;
    }

    // 基础配置 --------------------------------
    public BowItemBuilder useDuration(int duration) {
        this.useDuration = duration;
        return this;
    }

    // 方法控制 --------------------------------
    public BowItemBuilder callSuper(String method, boolean call) {
        superFlags.put(method, call);
        return this;
    }

    // 通用回调注册 -----------------------------
    public BowItemBuilder registerCallback(String type, Consumer callback) {
        callbacks.put(type, callback);
        return this;
    }
    public BowItemBuilder registerSupplier(String type, Supplier supplier) {
        callbacks.put(type, supplier);
        return this;
    }
    public BowItemBuilder registerFunction(String type, Function callback) {
        callbacks.put(type, callback);
        return this;
    }
    public BowItemBuilder registerPredicate(String type, Object object) {
        suppliers.put(type, object);
        return this;
    }

    // 注入函数的方法 --------------------------------
    public BowItemBuilder onRelease(Consumer<ConsumerContext.releaseUsingContext> callback) {
        return registerCallback("onRelease", callback);
    }
    public BowItemBuilder setAllSupportedProjectiles(Predicate<ItemStack> supplier) {
        return registerPredicate("allSupportedProjectiles", supplier);
    }

    @Override
    public BowItemBuilder useAnimation(UseAnim animation) {
        return (BowItemBuilder) super.useAnimation(animation);
    }

    public BowItemBuilder onCustomArrow(Function<AbstractArrow,AbstractArrow> callback) {
        return registerFunction("customArrow", callback);
    }

    public BowItemBuilder defaultProjectileRange(int range) {
        return registerSupplier("getDefaultProjectileRange", () -> range);
    }



    @Override
    public Item createObject() {
        return new KubeBowItem(
                new Item.Properties(),
                useDuration,
                new HashMap<>(superFlags),
                new HashMap<>(callbacks),
                new HashMap<>(suppliers),
                nbt
        );
    }
}
