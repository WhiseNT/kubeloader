package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.definition.meta.Engine;
import com.whisent.kubeloader.impl.mixin.ScriptFileInfoInterface;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Mixin(value = ScriptFileInfo.class, remap = false)
public abstract class ScriptFileInfoMixin implements ScriptFileInfoInterface {
    @Unique
    private String targetPath = "";

    @Unique
    private final Set<String> sides = new HashSet<>();

    @Unique
    private Optional<Engine> engine = Optional.empty();

    @Override
    @Unique
    public String kubeLoader$getTargetPath() {
        return targetPath;
    }

    @Override
    @Unique
    public void kubeLoader$setTargetPath(String targetPath) {
        if (this.targetPath.isEmpty()) {
            this.targetPath = targetPath;
        }
    }

    @Override
    @Unique
    public Set<String> kubeLoader$getSides() {
        return sides;
    }

    @Override
    @Unique
    public Optional<Engine> kubeLoader$getEngine() {
        return engine;
    }

    @Override
    @Unique
    public void kubeLoader$setEngine(Engine engine) {
        this.engine = Optional.ofNullable(engine);
    }
}