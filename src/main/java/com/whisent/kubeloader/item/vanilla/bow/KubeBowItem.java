package com.whisent.kubeloader.item.vanilla.bow;

import com.whisent.kubeloader.item.vanilla.ConsumerContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class KubeBowItem extends BowItem {
    private final Map<String, Boolean> superFlags;
    private final Map<String , Object> callbacks;
    private final Map<String , Object> suppliers;
    private final int customUseDuration;
    public CompoundTag nbt;
    public KubeBowItem(Properties properties,
                       int useDuration,
                       Map<String, Boolean> superFlags,
                       Map<String, Object> callbacks,
                       Map<String, Object> suppliers,
                       CompoundTag nbt) {
        super(properties);
        this.customUseDuration = useDuration;
        this.superFlags = new HashMap<>(superFlags);
        this.callbacks = new HashMap<>(callbacks);
        this.suppliers = new HashMap<>(suppliers);
        this.nbt = nbt;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = new ItemStack(this);
        stack.getOrCreateTag().merge(nbt);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack bow, Level level, LivingEntity owner, int time) {
        if (shouldCallSuper("onRelease")) {
            super.releaseUsing(bow, level, owner, time);
        }
        ConsumerContext.releaseUsingContext context = new ConsumerContext.releaseUsingContext(bow,level,owner,time);
        Consumer<ConsumerContext.releaseUsingContext> callback = getCallback("onRelease");
        if (callback != null) {
            callback.accept(context);
        }
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        if (shouldCallSuper("allSupportedProjectiles")) {
            super.getAllSupportedProjectiles();
        }
        Predicate<ItemStack> supplier = getPredicate("allSupportedProjectiles");
        if (supplier != null) {
            return supplier;
        }
        return super.getAllSupportedProjectiles();
    }
    @Override
    public int getUseDuration(ItemStack stack) {
        if (shouldCallSuper("getUseDuration")) {
            return super.getUseDuration(stack);
        }
        Supplier<Integer> supplier = getSupplier("getUseDuration");
        return supplier != null ? supplier.get() : customUseDuration;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        if (shouldCallSuper("getUseAnimation")) {
            return super.getUseAnimation(stack);
        }
        Supplier<UseAnim> supplier = getSupplier("getUseAnimation");
        return supplier != null ? supplier.get() : UseAnim.BOW;
    }


    @Override
    public AbstractArrow customArrow(AbstractArrow arrow) {
        Function<AbstractArrow, AbstractArrow> callback = getFunction("customArrow");
        return callback != null ? callback.apply(arrow) : super.customArrow(arrow);
    }

    @Override
    public int getDefaultProjectileRange() {
        if (shouldCallSuper("getDefaultProjectileRange")) {
            return super.getDefaultProjectileRange();
        }
        Supplier<Integer> supplier = getSupplier("getDefaultProjectileRange");
        return supplier != null ? supplier.get() : super.getDefaultProjectileRange();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 判断是否调用父类逻辑
        if (shouldCallSuper("use")) {
            return super.use(level, player, hand);
        }

        ConsumerContext.UseCallback callback = (ConsumerContext.UseCallback) getCallback("use");
        if (callback != null) {
            ConsumerContext.UseContext context = new ConsumerContext.UseContext(level, player, hand, player.getItemInHand(hand));
            return callback.handle(context);
        }
        return super.use(level, player, hand);
    }

    @SuppressWarnings("unchecked")
    private Consumer getCallback(String type) {
        return (Consumer) callbacks.get(type);
    }
    @SuppressWarnings("unchecked")
    private Function getFunction(String type) {
        return (Function) callbacks.get(type);
    }
    @SuppressWarnings("unchecked")
    private Supplier getSupplier(String type) {
        return (Supplier) suppliers.get(type);
    }
    @SuppressWarnings("unchecked")
    private Predicate<ItemStack> getPredicate(String type) {
        return (Predicate<ItemStack>) suppliers.get(type);
    }

    private boolean shouldCallSuper(String method) {
        return superFlags.getOrDefault(method, true);
    }
}