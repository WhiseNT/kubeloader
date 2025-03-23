package com.whisent.kubeloader.impl.depends;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * @author ZZZank
 */
public class DependencyReport {
    private final List<Component> infos = new ArrayList<>();
    private final List<Component> warnings = new ArrayList<>();
    private final List<Component> errors = new ArrayList<>();

    public void addInfo(Component info) {
        this.infos.add(Objects.requireNonNull(info));
    }

    public void addWarning(Component warning) {
        this.warnings.add(Objects.requireNonNull(warning));
    }

    public void addError(Component error) {
        this.errors.add(Objects.requireNonNull(error));
    }

    public Collection<Component> infos() {
        return infos;
    }

    public Collection<Component> warnings() {
        return warnings;
    }

    public Collection<Component> errors() {
        return errors;
    }
}
