package com.whisent.kubeloader.scripts.modernjs;

final class StaticMemberPlugin implements ClassMemberPlugin {
    @Override
    public String syntax() {
        return "static x = ... | static f(...) {...}";
    }

    @Override
    public boolean matches(String statement) {
        return statement.startsWith("static ");
    }

    @Override
    public void apply(String className, String statement, java.util.List<String> instanceFields, java.util.List<String> methods, java.util.List<String> staticMembers) {
        staticMembers.add(statement.substring(7).trim());
    }
}
