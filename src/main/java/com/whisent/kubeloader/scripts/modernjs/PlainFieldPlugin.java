package com.whisent.kubeloader.scripts.modernjs;

final class PlainFieldPlugin implements ClassMemberPlugin {
    @Override
    public String syntax() {
        return "name;";
    }

    @Override
    public boolean matches(String statement) {
        return statement.endsWith(";") && ModernJSParser.isValidFieldName(statement);
    }

    @Override
    public void apply(String className, String statement, java.util.List<String> instanceFields, java.util.List<String> methods, java.util.List<String> staticMembers) {
        instanceFields.add(statement);
    }
}
