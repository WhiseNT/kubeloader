package com.whisent.kubeloader.item.dynamic;

import com.whisent.kubeloader.item.dynamic.definition.DynamicBuilder;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import dev.latvian.mods.kubejs.item.custom.SwordItemBuilder;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

import java.util.HashMap;
import java.util.Map;

public class DynamicTieredItemBuilder extends ItemBuilder implements DynamicBuilder {
    public HashMap<String, Tier> enableTierMap = new HashMap<String, Tier>();
    public int baseAttack;
    public float baseAttackSpeed;
    public String baseTier;
    public HashMap<String,HashMap<String,Object>> extendAttributesMap = new HashMap<>();

    public DynamicTieredItemBuilder(ResourceLocation i) {
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
    public ItemBuilder putAttribute(String key, HashMap<String,Object> valueMap) {
        extendAttributesMap.put(key,valueMap);
        return this;
    }

    @Override
    public Item createObject() {
        return new DynamicTieredItem(baseAttack,baseAttackSpeed,baseTier,enableTierMap,extendAttributesMap);
    }
}
