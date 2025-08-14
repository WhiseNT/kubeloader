package com.whisent.kubeloader.utils.mod_gen;

public class ModDependency {
    public final String id;
    public final boolean mandatory;
    public final String versionRange;
    public final String ordering;
    public final String side;

    // 私有构造，强制使用 Builder
    private ModDependency(Builder builder) {
        this.id = builder.id;
        this.mandatory = builder.mandatory;
        this.versionRange = builder.versionRange;
        this.ordering = builder.ordering;
        this.side = builder.side;
    }


    public static Builder create(String modId) {
        return new Builder().withId(modId);
    }

    // ========================
    // Builder 内部类
    // ========================
    public static class Builder {
        private String id;
        private boolean mandatory = true;
        private String versionRange;
        private String ordering;
        private String side;

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withMandatory(boolean mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public Builder withVersionRange(String versionRange) {
            this.versionRange = versionRange;
            return this;
        }

        public Builder withOrdering(String ordering) {
            this.ordering = ordering;
            return this;
        }

        public Builder withSide(String side) {
            this.side = side;
            return this;
        }

        public ModDependency build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalStateException("ModDependency ID is required.");
            }
            return new ModDependency(this);
        }
    }

    // ========================
    // 输出为 TOML 格式（保持原有逻辑，优化）
    // ========================
    public String toTomlString(String modId) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[[dependencies.%s]]%n", modId));
        sb.append(String.format("    modId=\"%s\"%n", this.id));
        sb.append(String.format("    mandatory=%s%n", this.mandatory));

        if (this.versionRange != null && !this.versionRange.isEmpty()) {
            sb.append(String.format("    versionRange=\"%s\"%n", this.versionRange));
        }
        if (this.ordering != null && !this.ordering.isEmpty()) {
            sb.append(String.format("    ordering=\"%s\"%n", this.ordering));
        }
        if (this.side != null && !this.side.isEmpty()) {
            sb.append(String.format("    side=\"%s\"%n", this.side));
        }
        return sb.toString();
    }

    // ========================
    // 可选：toString() 用于调试
    // ========================
    @Override
    public String toString() {
        return String.format("ModDependency{id='%s', mandatory=%s, versionRange='%s', ordering='%s', side='%s'}",
                id, mandatory, versionRange, ordering, side);
    }
}
