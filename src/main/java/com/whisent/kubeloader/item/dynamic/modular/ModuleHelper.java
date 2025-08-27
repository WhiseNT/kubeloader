package com.whisent.kubeloader.item.dynamic.modular;

import dev.latvian.mods.kubejs.registry.RegistryInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModuleHelper {
    public static List<ItemStack> getModules(ItemStack stack) {
        List<ItemStack> modules = new ArrayList<>();
        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag.contains("Modules", 9)) {
                ListTag moduleTags = tag.getList("Modules", 10);
                for (int i = 0; i < moduleTags.size(); i++) {
                    CompoundTag moduleTag = moduleTags.getCompound(i);
                    String moduleId = moduleTag.getString("id");
                    CompoundTag itemNBT = new CompoundTag();
                    itemNBT.putString("id", moduleId);
                    ItemStack moduleStack = new ItemStack((ItemLike) moduleTag, 1);
                    if (moduleStack.getItem() instanceof ModuleItem) {
                        modules.add(moduleStack);
                    }
                }
            }
        }
        return modules;
    }
    public static UUID generateUUIDForAttribute(Attribute attribute, ItemStack item, AttributeModifier.Operation operation) {
        String attributeString = RegistryInfo.ATTRIBUTE.getId(attribute).toString();
        String itemString = RegistryInfo.ITEM.getId(item.getItem()).toString();
        String operationString = operation.toString();
        return UUID.nameUUIDFromBytes((attributeString+itemString+operationString).getBytes());
    }

}
