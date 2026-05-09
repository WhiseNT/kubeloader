package com.whisent.kubeloader.scripts.modernjs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DefaultParameterPlugin implements TextPlugin {
    private static final Pattern FUNCTION_WITH_DEFAULTS_PATTERN =
            Pattern.compile("(\\bfunction\\s+\\w+\\s*\\([^)]*\\))(\\s*\\{)");

    @Override
    public String syntax() {
        return "function f(a=1) { } -> function f(a) { a = a===undefined?1:a; }";
    }

    @Override
    public boolean matches(String input) {
        return FUNCTION_WITH_DEFAULTS_PATTERN.matcher(input).find();
    }

    @Override
    public String apply(String input) {
        Matcher m2 = FUNCTION_WITH_DEFAULTS_PATTERN.matcher(input);
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            String head = m2.group(1);
            String brace = m2.group(2);

            int pStart = head.indexOf('(') + 1;
            int pEnd = head.lastIndexOf(')');
            String paramsPart = head.substring(pStart, pEnd);
            String[] params = paramsPart.isEmpty() ? new String[0] : paramsPart.split(",");

            StringBuilder dftStmts = new StringBuilder();
            StringBuilder cleanParams = new StringBuilder();

            for (int i = 0; i < params.length; i++) {
                String p = params[i].trim();
                if (i > 0) cleanParams.append(", ");
                int eq = p.indexOf('=');
                if (eq > 0) {
                    String name = p.substring(0, eq).trim();
                    String val = p.substring(eq + 1).trim();
                    dftStmts.append("  ")
                            .append(name).append(" = ")
                            .append(name).append(" === undefined ? ")
                            .append(val).append(" : ").append(name).append(";\n");
                    cleanParams.append(name);
                } else {
                    cleanParams.append(p);
                }
            }

            String cleanSig = head.substring(0, pStart) + cleanParams + ")";
            m2.appendReplacement(sb2,
                    Matcher.quoteReplacement(cleanSig + brace + "\n" + dftStmts.toString()));
        }
        m2.appendTail(sb2);
        return sb2.toString();
    }
}
