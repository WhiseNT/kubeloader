package com.whisent.kubeloader.item.dynamic.definition;

import dev.latvian.mods.kubejs.item.ItemBuilder;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

import java.util.HashMap;

public interface DynamicBuilder {

    @Info("向物品添加允许的Tier.")
    ItemBuilder putTier(String tierName, Tier tier);

    @Info("删除物品允许的Tier.")
    ItemBuilder removeTier(String tierName);

    ItemBuilder setDefaultTier(String tierName);

    ItemBuilder setBaseAttack(int baseAttack);

    ItemBuilder setBassAttackSpeed(float bassAttackSpeed);


    default HashMap<String, Tier> initTierMap(HashMap<String, Tier> enableTierMap) {
        enableTierMap.put("wood", Tiers.WOOD);
        enableTierMap.put("stone", Tiers.STONE);
        enableTierMap.put("iron", Tiers.IRON);
        enableTierMap.put("gold", Tiers.GOLD);
        enableTierMap.put("diamond", Tiers.DIAMOND);
        enableTierMap.put("netherite",Tiers.NETHERITE);
        return enableTierMap;
    };
}
