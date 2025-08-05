package com.whisent.kubeloader.utils.mod_gen;

public class ModDependency {
    public String id;
    public boolean mandatory = true;
    public String versionRange = null;
    public String ordering = null;
    public String side = null;
    public ModDependency(String id, boolean mandatory, String versionRange, String ordering, String side) {
        this.id = id;
        this.mandatory = mandatory;
        this.versionRange = versionRange;
        this.ordering = ordering;
        this.side = side;
    }

    public String toTomlString(String modId) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[[dependencies.%s]]\n", modId));
        sb.append(String.format("    modId=\"%s\"\n", this.id));
        sb.append(String.format("    mandatory=%s\n", this.mandatory));
        if (this.versionRange != null) {
            sb.append(String.format("    versionRange=\"%s\"\n", this.versionRange));
        }
        if (this.ordering != null) {
            sb.append(String.format("    ordering=\"%s\"\n", this.ordering));
        }
        if (this.side != null) {
            sb.append(String.format("    side=\"%s\"\n", this.side));
        }
        return sb.toString();
    }
}
