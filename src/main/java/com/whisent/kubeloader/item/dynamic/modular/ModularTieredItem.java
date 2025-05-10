package com.whisent.kubeloader.item.dynamic.modular;

import com.google.common.collect.Multimap;
import com.whisent.kubeloader.item.dynamic.DynamicTieredItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModularTieredItem extends DynamicTieredItem {
    public ModularTieredItem(int baseAttack, float baseAttackSpeed, String baseTier, Map<String, Tier> enableTierMap, HashMap<String, HashMap<String, Object>> extendAttributesMap) {
        super(baseAttack, baseAttackSpeed, baseTier, enableTierMap, extendAttributesMap);
    }


    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> modifiers= super.getAttributeModifiers(slot, stack);
        List<ItemStack> modules = ModuleHelper.getModules(stack);
        modules.forEach(itemStack -> {
            ModuleItem moduleItem = (ModuleItem) itemStack.getItem();
            moduleItem.getAllAttributes().forEach((attribute, attributeValue) -> {
                modifiers.put(attribute,new AttributeModifier(
                        ModuleHelper.generateUUIDForAttribute(attribute,itemStack,attributeValue.getOperation()),
                        "KLModule",
                        attributeValue.getValue(),
                        attributeValue.getOperation()
                ));
            });
        });
        return modifiers;
    }
}
