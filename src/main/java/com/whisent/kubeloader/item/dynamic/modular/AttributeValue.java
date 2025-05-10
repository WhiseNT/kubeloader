package com.whisent.kubeloader.item.dynamic.modular;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class AttributeValue {
    private final double value;
    private final AttributeModifier.Operation operation;
    public AttributeValue(double value, AttributeModifier.Operation operation) {
        this.value = value;
        this.operation = operation;
    }

    public AttributeModifier.Operation getOperation() {
        return operation;
    }

    public double getValue() {
        return value;
    }
}
