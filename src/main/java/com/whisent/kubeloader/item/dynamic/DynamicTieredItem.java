package com.whisent.kubeloader.item.dynamic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.whisent.kubeloader.item.dynamic.attributes.DynamicAttributes;
import com.whisent.kubeloader.item.dynamic.definition.DynamicItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public class DynamicTieredItem extends DiggerItem implements DynamicAttributes {
    public int baseAttack;
    public float baseAttackSpeed;
    public Map<String, Tier> enableTierMap;
    public Tier baseTier;
    public String baseTierName;
    private TagKey<Block> blocks;
    private Tier tier;
    public DynamicTieredItem(int baseAttack, float baseAttackSpeed, String baseTier, Map<String,Tier> enableTierMap) {
        super(baseAttack,baseAttackSpeed,Tiers.WOOD,BlockTags.MINEABLE_WITH_PICKAXE, new Properties());
        this.enableTierMap = enableTierMap;
        this.baseAttack = baseAttack;
        this.baseAttackSpeed = baseAttackSpeed;
        this.baseTier = enableTierMap.get(baseTier);
        this.baseTierName = baseTier;
    }
    public TagKey<Block> getBlocks (ItemStack stack) {
        String toolType = stack.getTag().getCompound("dynamic").getString(TOOL_TAG);
        return switch (toolType) {
            case "shovel" -> BlockTags.MINEABLE_WITH_SHOVEL;
            case "pickaxe" -> BlockTags.MINEABLE_WITH_PICKAXE;
            case "axe" -> BlockTags.MINEABLE_WITH_AXE;
            case "hoe" -> BlockTags.MINEABLE_WITH_HOE;
            default -> BlockTags.MINEABLE_WITH_PICKAXE;
        };
    }

    public Tier getTier(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag().getCompound("dynamic");
        String tierName = tag.getString(TIER_TAG);
        enableTierMap.get(tierName);
        if (enableTierMap.get(tierName) != null) {
            return enableTierMap.get(tierName);
        } else return baseTier;
    }

    public Float getDigSpeed(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag().getCompound("dynamic");
        return tag.getFloat(DIG_SPEED_TAG);
    }

    @Override
    public @NotNull ItemStack getDefaultInstance() {
        ItemStack stack = new ItemStack(this);
        stack.getOrCreateTag().put("dynamic", new CompoundTag());
        CompoundTag tag = stack.getOrCreateTag().getCompound("dynamic");
        tag.putString(TIER_TAG, baseTierName);
        tag.putInt(ATTACK_BONUS_TAG,baseAttack);
        tag.putFloat(ATTACK_SPEED_TAG,baseAttackSpeed);
        tag.putFloat(DIG_SPEED_TAG,3F);
        tag.putString(TOOL_TAG,"shovel");
        tag.putInt(ARMOR_TAG,0);
        tag.putInt(ARMOR_TOUGHNESS_TAG,0);
        tag.putFloat(MOVEMENT_SPEED_TAG,0);
        tag.putFloat(LUCK_TAG,0);
        tag.putFloat(MAX_HEALTH_TAG,0);
        return stack;
    }



    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> modifiers = HashMultimap.create();
        if (slot == EquipmentSlot.MAINHAND) {

            CompoundTag tag = stack.getOrCreateTag().getCompound("dynamic");
            float attackBonus = baseAttack + tag.getInt(ATTACK_BONUS_TAG);
            float attackSpeed = baseAttackSpeed + tag.getFloat(ATTACK_SPEED_TAG);
            float armor = tag.getInt(ARMOR_TAG);
            float armorToughness = tag.getInt(ARMOR_TOUGHNESS_TAG);
            float movementSpeed = tag.getFloat(MOVEMENT_SPEED_TAG);
            float luck = tag.getFloat(LUCK_TAG);
            float flyingSpeed = tag.getFloat(FLYING_SPEED_TAG);
            float knockbackResistance = tag.getFloat(KNOCKBACK_RESISTANCE_TAG);
            float jumpStrength = tag.getFloat(JUMP_STRENGTH_TAG);
            float maxHealth = tag.getFloat(MAX_HEALTH_TAG);
            modifiers.put(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                            BASE_ATTACK_DAMAGE_UUID,
                            "modifier",
                            attackBonus,
                            AttributeModifier.Operation.ADDITION
                    )
            );
            modifiers.put(Attributes.ATTACK_SPEED,
                    new AttributeModifier(
                            BASE_ATTACK_SPEED_UUID,
                            "modifier",
                            attackSpeed,
                            AttributeModifier.Operation.ADDITION
                    ));

            this.putAttributes(modifiers,Attributes.ARMOR,armor);
            this.putAttributes(modifiers,Attributes.ARMOR_TOUGHNESS,armorToughness);
            this.putAttributes(modifiers,Attributes.MOVEMENT_SPEED,movementSpeed);
            this.putAttributes(modifiers,Attributes.LUCK,luck);
            this.putAttributes(modifiers,Attributes.FLYING_SPEED,flyingSpeed);
            this.putAttributes(modifiers,Attributes.KNOCKBACK_RESISTANCE,knockbackResistance);
            this.putAttributes(modifiers,Attributes.JUMP_STRENGTH,jumpStrength);
            this.putAttributes(modifiers,Attributes.MAX_HEALTH,maxHealth);
        }
        return super.getAttributeModifiers(slot, stack);
    }

    public void putAttributes(Multimap<Attribute, AttributeModifier> modifiers,Attribute attribute,Float value) {
        modifiers.put(attribute,new AttributeModifier(
                UUID.randomUUID(),
                "modifier",
                value,
                AttributeModifier.Operation.ADDITION
        ));
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

    public float getDestroySpeed(ItemStack pStack, BlockState pState) {
        return pState.is(this.getBlocks(pStack)) ? this.getDigSpeed(pStack): 1.0F;
    }

    public boolean hurtEnemy(ItemStack pStack, LivingEntity pTarget, LivingEntity pAttacker) {
        pStack.hurtAndBreak(2, pAttacker, (p_41007_) -> {
            p_41007_.broadcastBreakEvent(EquipmentSlot.MAINHAND);
        });
        return true;
    }

    public boolean mineBlock(ItemStack pStack, Level pLevel, BlockState pState, BlockPos pPos, LivingEntity pEntityLiving) {
        if (!pLevel.isClientSide && pState.getDestroySpeed(pLevel, pPos) != 0.0F) {
            pStack.hurtAndBreak(1, pEntityLiving, (p_40992_) -> {
                p_40992_.broadcastBreakEvent(EquipmentSlot.MAINHAND);
            });
        }

        return true;
    }
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(this.getBlocks(stack)) && TierSortingRegistry.isCorrectTierForDrops(this.getTier(stack), state);
    }
}
