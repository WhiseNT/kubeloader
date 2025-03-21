package com.whisent.kubeloader.item.dynamic.definition;

import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;

public interface DynamicItem {
    String TIER_TAG = "tool_tier";
    String ATTACK_BONUS_TAG = "attack_bonus";
    String ATTACK_SPEED_TAG = "attack_speed";


    Tier getTier(ItemStack stack);

    ItemStack getDefaultInstance();



}
