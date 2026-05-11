package com.whisent.kubeloader.scripts.modernjs.plugin.impl;

import java.util.List;

public interface ClassMemberPlugin {
    String syntax();

    boolean matches(String statement);

    void apply(String className, String statement,
               List<String> instanceFields,
               List<String> methods,
               List<String> staticMembers);
}
