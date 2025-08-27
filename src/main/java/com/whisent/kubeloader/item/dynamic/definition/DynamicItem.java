package com.whisent.kubeloader.item.dynamic.definition;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;

public interface DynamicItem {
    String TIER_TAG = "tool_tier";
    String ATTACK_BONUS_TAG = "attack_bonus";
    String ATTACK_SPEED_TAG = "attack_speed";


    Tier getTier(ItemStack stack);

    ItemStack getDefaultInstance();



}
