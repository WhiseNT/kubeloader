package com.whisent.kubeloader.scripts.modernjs;

final class MethodPlugin implements ClassMemberPlugin {
    @Override
    public String syntax() {
        return "name(...) { ... }";
    }

    @Override
    public boolean matches(String statement) {
        return statement.contains("(") && statement.endsWith("}");
    }

    @Override
    public void apply(String className, String statement, java.util.List<String> instanceFields, java.util.List<String> methods, java.util.List<String> staticMembers) {
        methods.add(convertMethod(className, statement));
    }

    private static String convertMethod(String className, String decl) {
        int paren = decl.indexOf('(');
        if (paren == -1) return "// invalid method: " + decl;
        String name = decl.substring(0, paren).trim();
        String rest = decl.substring(paren);
        return className + ".prototype." + name + " = function" + rest + ";";
    }
}
