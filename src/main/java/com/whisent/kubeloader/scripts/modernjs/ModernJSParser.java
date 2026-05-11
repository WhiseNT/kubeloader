package com.whisent.kubeloader.scripts.modernjs;

import com.whisent.kubeloader.scripts.modernjs.plugin.impl.SourcePlugin;

public class ModernJSParser {

    private static final ModernJSPluginManager PLUGINS = ModernJSPluginManager.INSTANCE;
    public static String parse(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder result = new StringBuilder();
        String[] lines = input.split("\\r?\\n");
        int i = 0;

        while (i < lines.length) {
            SourcePlugin plugin = PLUGINS.findSourcePlugin(lines[i].trim());
            if (plugin != null) {
                SourceTransformResult transformed = plugin.transform(lines, i);
                result.append(transformed.output);
                i = transformed.endLineIndex + 1;
            } else {
                result.append(lines[i]).append("\n");
                i++;
            }
        }

        return PLUGINS.applyPostProcessing(result.toString());
    }

    public static boolean isValidFieldName(String stmt) {
        int eq = stmt.indexOf('=');
        if (eq <= 0) return false;
        String name = stmt.substring(0, eq).trim();
        return isValidIdentifier(name);
    }

    public static boolean isValidIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        char first = s.charAt(0);
        if (first != '_' && first != '$' && !Character.isLetter(first)) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '_' && c != '$' && !Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }
    /* ===================== 测试 ===================== */
    public static void main(String[] args) {
        String input = "class Box {\n" +
                "  value = 1;\n" +
                "\n" +
                "  constructor(name) {\n" +
                "    this.name = name;\n" +
                "  }\n" +
                "\n" +
                "  greet() {\n" +
                "    return { name };\n" +
                "  }\n" +
                "\n" +
                "  get valueText() {\n" +
                "    return this._value;\n" +
                "  }\n" +
                "\n" +
                "  set valueText(value) {\n" +
                "    this._value = value;\n" +
                "  }\n" +
                "\n" +
                "  static version = \"1.0\";\n" +
                "  static make(label) {\n" +
                "    return label;\n" +
                "  }\n" +
                "}\n";
        System.out.println("转换前：");
        System.out.println(input);
        System.out.println("转换后：");
        System.out.println(parse(input));
    }
}
