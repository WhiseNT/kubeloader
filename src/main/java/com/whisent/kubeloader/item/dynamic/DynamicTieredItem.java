package com.whisent.kubeloader.item.dynamic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.whisent.kubeloader.item.dynamic.attributes.DynamicAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.TierSortingRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DynamicTieredItem extends DiggerItem implements DynamicAttributes {
    public int baseAttack;
    public float baseAttackSpeed;
    public Map<String, Tier> enableTierMap;
    public Tier baseTier;
    public String baseTierName;
    private TagKey<Block> blocks;
    private ArrayList<Tier> tiers;

    public HashMap<String,HashMap<String,Object>> extendAttributesMap = new HashMap<>();

    private final UUID REACH_UUID = UUID.fromString("6E81DA22-D3A3-6861-425E-17A86AB675E2");
    private final UUID MAX_HEALTH_UUID = UUID.fromString("F0B38DBC-DE56-D4C1-C719-7C01DED7B028");
    private final UUID KNOCKBACK_RESISTANCE_UUID = UUID.fromString("8ECB9FE0-C171-BECB-6F18-BD417246D64C");
    private final UUID MOVEMENT_SPEED_UUID = UUID.fromString("9138D62D-343B-2095-2F02-753C71AE9244");
    private final UUID FLY_SPEED_UUID = UUID.fromString("B18E2AD3-589E-155E-C88D-B29977CD29A6");
    private final UUID LUCK_UUID = UUID.fromString("691594AC-16C4-FD27-8D31-BB15B3F8AE7B");
    private final UUID JUMP_STRENGTH_UUID = UUID.fromString("F47B240E-7A23-2A07-CB00-7BB881096511");
    private final UUID DIG_SPEED_UUID = UUID.fromString("43EAFB1E-6180-D729-DA84-9D6EE4588312");
    private final UUID ARMOR_TOUGHNESS_UUID = UUID.fromString("2EC897D4-113B-55D0-DFCE-7B51527BBA7C");
    private final UUID ARMOR_UUID = UUID.fromString("2de1b442-56aa-ba93-37d6-27216aad7c7e");
    private final UUID ENTITY_GRAVITY_UUID = UUID.fromString("F9984215-BFB6-E9BB-12E7-A3D313082F10");


    public DynamicTieredItem(int baseAttack, float baseAttackSpeed, String baseTier, Map<String,Tier> enableTierMap,
                             HashMap<String,HashMap<String,Object>> extendAttributesMap) {
        super(baseAttack,baseAttackSpeed,Tiers.WOOD,BlockTags.MINEABLE_WITH_PICKAXE, new Properties());
        this.enableTierMap = enableTierMap;
        this.baseAttack = baseAttack;
        this.baseAttackSpeed = baseAttackSpeed;
        this.baseTier = enableTierMap.get(baseTier);
        this.baseTierName = baseTier;
        this.extendAttributesMap = extendAttributesMap;
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
        if (tag.getFloat(DIG_SPEED_TAG) == 0) {
            return 1.0F;
        } else {
            return tag.getFloat(DIG_SPEED_TAG);
        }
    }

    @Override
    public @NotNull ItemStack getDefaultInstance() {
        ItemStack stack = new ItemStack(this);
        stack.getOrCreateTag().put("dynamic", new CompoundTag());
        CompoundTag tag = stack.getOrCreateTag().getCompound("dynamic");
        tag.putString(TIER_TAG, baseTierName);
        /*
        tag.putInt(ATTACK_BONUS_TAG,baseAttack);
        tag.putFloat(ATTACK_SPEED_TAG,baseAttackSpeed);
        tag.putFloat(DIG_SPEED_TAG,3F);
        tag.putString(TOOL_TAG,"shovel");

        tag.putInt(ARMOR_TOUGHNESS_TAG,0);
        tag.putFloat(MOVEMENT_SPEED_TAG,0);
        tag.putFloat(LUCK_TAG,0);
        tag.putFloat(MAX_HEALTH_TAG,0);
        */
        tag.putFloat(MAX_HEALTH_TAG,10);
        tag.putInt(ARMOR_TAG,4);
        tag.putFloat(ENTITY_GRAVITY_TAG,-0.01f);
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
            float luck = tag.getFloat(LUCK_TAG) + 5F;
            float flyingSpeed = tag.getFloat(FLYING_SPEED_TAG);
            float knockbackResistance = tag.getFloat(KNOCKBACK_RESISTANCE_TAG);
            float jumpStrength = tag.getFloat(JUMP_STRENGTH_TAG);
            float maxHealth = tag.getFloat(MAX_HEALTH_TAG);
            float entityGravity = tag.getFloat(ENTITY_GRAVITY_TAG);
            this.putAttributes(modifiers,BASE_ATTACK_DAMAGE_UUID,Attributes.ATTACK_DAMAGE,attackBonus);
            this.putAttributes(modifiers,BASE_ATTACK_SPEED_UUID,Attributes.ATTACK_SPEED,attackSpeed);
            this.putAttributes(modifiers,ARMOR_UUID,Attributes.ARMOR,armor);
            this.putAttributes(modifiers,ARMOR_TOUGHNESS_UUID,Attributes.ARMOR_TOUGHNESS,armorToughness);
            this.putAttributes(modifiers,MOVEMENT_SPEED_UUID,Attributes.MOVEMENT_SPEED,movementSpeed);
            this.putAttributes(modifiers,LUCK_UUID,Attributes.LUCK,luck);
            this.putAttributes(modifiers,FLY_SPEED_UUID,Attributes.FLYING_SPEED,flyingSpeed);
            this.putAttributes(modifiers,KNOCKBACK_RESISTANCE_UUID,Attributes.KNOCKBACK_RESISTANCE,knockbackResistance);
            this.putAttributes(modifiers,JUMP_STRENGTH_UUID,Attributes.JUMP_STRENGTH,jumpStrength);
            this.putAttributes(modifiers,MAX_HEALTH_UUID,Attributes.MAX_HEALTH,maxHealth);
            this.putAttributes(modifiers,ENTITY_GRAVITY_UUID,ForgeMod.ENTITY_GRAVITY.get(),entityGravity);

            this.extendAttributesMap.forEach((key,value)->{
                float addValue = baseAttack + tag.getFloat(key);
                this.putAttributes(modifiers,(UUID) value.get("uuid"),(Attribute) value.get("attribute"),addValue);
            });
            return modifiers;
        }
        return modifiers;


    }

    public void putAttributes(Multimap<Attribute, AttributeModifier> modifiers,UUID uuid,Attribute attribute,Float value) {
        modifiers.put(attribute,new AttributeModifier(
                uuid,
                "modifier",
                value,
                AttributeModifier.Operation.ADDITION
        ));
    }

    public void putExtendAttributesMap(String id, UUID uuid,Attribute attribute) {
        var map = new HashMap<String, Object>();
        map.put("uuid",uuid);
        map.put("attribute",attribute);
        extendAttributesMap.put(id,map);
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
