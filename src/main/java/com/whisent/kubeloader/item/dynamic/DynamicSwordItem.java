package com.whisent.kubeloader.item.dynamic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.whisent.kubeloader.item.dynamic.attributes.DynamicAttributes;
import com.whisent.kubeloader.item.dynamic.definition.DynamicItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public class DynamicSwordItem extends SwordItem implements DynamicItem {
    public Map<String, Tier> enableTierMap;
    public int baseAttack;
    public float baseAttackSpeed;
    public String baseTier;

    public DynamicSwordItem(int baseAttack, float baseAttackSpeed, String baseTier, Map<String,Tier> enableTierMap) {
        super(Tiers.WOOD,0,0,new Properties());
        this.baseAttack = baseAttack;
        this.baseAttackSpeed = baseAttackSpeed;
        this.baseTier = baseTier;
        this.enableTierMap = enableTierMap;
    }

    @Override
    public Tier getTier(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        String tierName = tag.getString(TIER_TAG);
        enableTierMap.get(tierName);
        if (enableTierMap.get(tierName) != null) {
            return enableTierMap.get(tierName);
        } else return Tiers.WOOD;
    }
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {


        Multimap<Attribute, AttributeModifier> modifiers = HashMultimap.create();
        if (slot == EquipmentSlot.MAINHAND) {

            float attackBonus = stack.getTag().getInt(DynamicAttributes.ATTACK_BONUS_TAG);
            float attackSpeed = stack.getTag().getFloat(ATTACK_SPEED_TAG);

            modifiers.put(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                            BASE_ATTACK_DAMAGE_UUID,
                            "Weapon modifier",
                            attackBonus,
                            AttributeModifier.Operation.ADDITION
                    )
            );
            modifiers.put(Attributes.ATTACK_SPEED,
                    new AttributeModifier(
                            BASE_ATTACK_SPEED_UUID,
                            "Weapon modifier",
                            attackSpeed,
                            AttributeModifier.Operation.ADDITION
                    ));
            modifiers.put(Attributes.LUCK,new AttributeModifier(
                    UUID.randomUUID(),"Weapon modifier",0,AttributeModifier.Operation.ADDITION));
        }
        return modifiers;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = new ItemStack(this);
        CompoundTag tag = stack.getOrCreateTag();
        tag.put("baseAttributes",new CompoundTag());
        CompoundTag baseTag = tag.getCompound("baseAttributes");
        baseTag.putString(TIER_TAG, baseTier);
        baseTag.putInt(ATTACK_BONUS_TAG,baseAttack);
        baseTag.putFloat(ATTACK_SPEED_TAG,baseAttackSpeed);
        return stack;
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return getTier(stack).getUses();
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return getTier(stack).getEnchantmentValue();
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairItem) {
        return getTier(stack).getRepairIngredient().test(repairItem);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return getTier(stack).getSpeed();
    }
}
