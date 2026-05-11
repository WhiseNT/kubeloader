package com.whisent.kubeloader.scripts.modernjs.plugin;

import com.whisent.kubeloader.scripts.modernjs.ModernJSParser;
import com.whisent.kubeloader.scripts.modernjs.plugin.impl.TextPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShorthandReturnObjectPlugin implements TextPlugin {
    private static final Pattern RETURN_SHORTHAND_PATTERN =
            Pattern.compile("\\breturn\\s*\\{([^{}]*)\\}\\s*(?=;|\\n|\\r|$)");

    @Override
    public String syntax() {
        return "return { a, b } -> return {a: a, b: b}";
    }

    @Override
    public boolean matches(String input) {
        return RETURN_SHORTHAND_PATTERN.matcher(input).find();
    }

    @Override
    public String apply(String result) {
        Matcher matcher = RETURN_SHORTHAND_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String inner = matcher.group(1).trim();
            if (inner.isEmpty()) {
                matcher.appendReplacement(sb, "return {}");
                continue;
            }

            String[] parts = inner.split(",");
            boolean shouldExpand = true;
            for (String part : parts) {
                String id = part.trim();
                if (!id.isEmpty() && !ModernJSParser.isValidIdentifier(id)) {
                    shouldExpand = false;
                    break;
                }
            }

            if (!shouldExpand) {
                matcher.appendReplacement(sb, matcher.group(0));
            } else {
                StringBuilder expanded = new StringBuilder("return {");
                boolean first = true;
                for (String part : parts) {
                    String id = part.trim();
                    if (id.isEmpty()) continue;
                    if (!first) expanded.append(", ");
                    expanded.append(id).append(": ").append(id);
                    first = false;
                }
                expanded.append("}");
                matcher.appendReplacement(sb, expanded.toString());
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
