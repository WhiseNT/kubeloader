package com.whisent.kubeloader.scripts.modernjs;

import java.util.List;

interface ClassMemberPlugin {
    String syntax();

    boolean matches(String statement);

    void apply(String className, String statement,
               List<String> instanceFields,
               List<String> methods,
               List<String> staticMembers);
}
