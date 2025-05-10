package com.whisent.kubeloader.item.dynamic.modular;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.Hash;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

import java.util.HashMap;

public class ModuleItem extends Item {
    public HashMap<Attribute, AttributeValue> attributeHashmap = new HashMap<>();
    public Tier tierModifier;
    public String toolTypeModifier;

    public ModuleItem(Properties itemProperties) {
        super(itemProperties);
        tierModifier = Tiers.WOOD;
        toolTypeModifier = "pickaxe";
    }
    public void setAttribute(Attribute attribute, double value, AttributeModifier.Operation operationType) {
        attributeHashmap.put(attribute, new AttributeValue(value, operationType));
    }
    public AttributeValue getAttribute(Attribute attribute) {
        return attributeHashmap.get(attribute);
    }
    public HashMap<Attribute, AttributeValue> getAllAttributes() {
        return attributeHashmap;
    }

    public Tier getTierModifier() {
        return tierModifier;
    }
    public void setTierModifier(Tier tier) {
        this.tierModifier = tier;
    }




}
