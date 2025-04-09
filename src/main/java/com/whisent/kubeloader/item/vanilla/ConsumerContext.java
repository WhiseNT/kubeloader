package com.whisent.kubeloader.item.vanilla;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ConsumerContext {
    public static class releaseUsingContext {
        public final ItemStack item;
        public final Level level;
        public final LivingEntity owner;
        public final int time;
        public releaseUsingContext(ItemStack stack, Level level, LivingEntity owner, int time) {
            this.item = stack;
            this.level = level;
            this.owner = owner;
            this.time = time;
        }
    }
    @FunctionalInterface
    public interface UseCallback {
        InteractionResultHolder<ItemStack> handle(ConsumerContext.UseContext context);
    }
    public static class UseContext {
        public final Level level;
        public final Player player;
        public final InteractionHand hand;
        public final ItemStack stack;

        public UseContext(Level level, Player player, InteractionHand hand, ItemStack stack) {
            this.level = level;
            this.player = player;
            this.hand = hand;
            this.stack = stack;
        }
    }
}
