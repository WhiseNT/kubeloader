package com.whisent.kubeloader.item.dynamic;

import com.whisent.kubeloader.item.dynamic.definition.DynamicBuilder;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import dev.latvian.mods.kubejs.item.custom.PickaxeItemBuilder;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

import java.util.HashMap;

public class DynamicPickAxeBuilder extends PickaxeItemBuilder implements DynamicBuilder {
    public HashMap<String, Tier> enableTierMap = new HashMap<String, Tier>();
    public int baseAttack;
    public float baseAttackSpeed;
    public String baseTier;

    public DynamicPickAxeBuilder(ResourceLocation i) {
        super(i);
        enableTierMap = initTierMap(enableTierMap);
        baseTier = "wood";
    }
    @Info("向物品添加允许的Tier.")
    @Override
    public ItemBuilder putTier(String tierName, Tier tier) {
        this.enableTierMap.put(tierName, tier);
        return this;
    }
    @Info("删除物品允许的Tier.")
    @Override
    public ItemBuilder removeTier(String tierName) {
        this.enableTierMap.remove(tierName);
        return this;
    }
    @Override
    public ItemBuilder setDefaultTier(String tierName) {
        this.baseTier = tierName;
        return this;
    }
    @Override
    public ItemBuilder setBaseAttack(int baseAttack) {
        this.baseAttack = baseAttack;
        return this;
    }
    @Override
    public ItemBuilder setBassAttackSpeed(float bassAttackSpeed) {
        this.baseAttackSpeed = bassAttackSpeed;
        return this;
    }

    @Override
    public Item createObject() {
        return new DynamicPickAxeItem(baseAttack,baseAttackSpeed,baseTier,enableTierMap);
    }
}