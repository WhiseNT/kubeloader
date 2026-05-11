package com.whisent.kubeloader.scripts.modernjs.plugin;

import com.whisent.kubeloader.scripts.modernjs.plugin.impl.ClassMemberPlugin;

import java.util.List;

public class AccessorPlugin implements ClassMemberPlugin {
    @Override
    public String syntax() {
        return "get name() {...} | set name(v) {...}";
    }

    @Override
    public boolean matches(String statement) {
        return statement.startsWith("get ") || statement.startsWith("set ");
    }

    @Override
    public void apply(String className, String statement, List<String> instanceFields, List<String> methods, List<String> staticMembers) {
        methods.add(convertGetterSetter(className, statement));
    }

    private static String convertGetterSetter(String className, String decl) {
        boolean isGet = decl.startsWith("get ");
        String prefix = isGet ? "get" : "set";
        String propName = decl.substring(prefix.length()).trim();
        int paren = propName.indexOf('(');
        if (paren != -1) propName = propName.substring(0, paren).trim();

        int startBrace = decl.indexOf('{');
        String body = "";
        if (startBrace != -1) {
            int endBrace = findMatchingBrace(decl, startBrace);
            if (endBrace != -1) {
                body = decl.substring(startBrace + 1, endBrace).trim();
            }
        }

        return "Object.defineProperty(" + className + ".prototype, '" + propName + "', {\n" +
                "  " + prefix + ": function() {\n" +
                indentLines(body, "    ") +
                "  },\n" +
                "  enumerable: true,\n" +
                "  configurable: true\n" +
                "});";
    }

    private static int findMatchingBrace(String s, int start) {
        if (s.charAt(start) != '{') return -1;
        int depth = 1;
        for (int i = start + 1; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String indentLines(String code, String indent) {
        if (code.isEmpty()) return "";
        return java.util.Arrays.stream(code.split("\\r?\\n"))
                .map(line -> indent + line)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
