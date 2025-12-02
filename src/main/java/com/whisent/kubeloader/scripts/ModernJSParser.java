package com.whisent.kubeloader.scripts;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class ModernJSParser {

    // ===================== 缓存常用正则 =====================
    private static final Pattern CLASS_HEADER_PATTERN =
            Pattern.compile("^\\s*class\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?:extends\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*)?\\{?\\s*$");

    private static final Pattern SUPER_CALL_PATTERN =
            Pattern.compile("\\bsuper\\s*\\(([^)]*)\\)");

    private static final Pattern THIS_ASSIGN_PATTERN =
            Pattern.compile("\\bthis\\.([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=");

    private static final Pattern RETURN_SHORTHAND_PATTERN =
            Pattern.compile("\\breturn\\s*\\{([^{}]*)\\}\\s*(?=;|\\n|\\r|$)");

    private static final Pattern FUNCTION_WITH_DEFAULTS_PATTERN =
            Pattern.compile("(\\bfunction\\s+\\w+\\s*\\([^)]*\\))(\\s*\\{)");

    // ===================== 入口 =====================
    public static String parse(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder result = new StringBuilder();
        String[] lines = input.split("\\r?\\n");
        int i = 0;

        while (i < lines.length) {
            String line = lines[i].trim();

            if (line.startsWith("class ")) {
                ClassDef def = parseClassFromLines(lines, i);
                result.append(convertClass(def.className, def.parentClass, def.body));
                i = def.endLineIndex + 1;
            } else {
                result.append(lines[i]).append("\n");
                i++;
            }
        }

        return postProcessing(result.toString());
    }

    /* ===================== 内部数据结构 ===================== */
    private static class ClassDef {
        final String className;
        final String parentClass;
        final String body;
        final int endLineIndex;

        ClassDef(String className, String parentClass, String body, int endLineIndex) {
            this.className = className;
            this.parentClass = parentClass;
            this.body = body;
            this.endLineIndex = endLineIndex;
        }
    }

    private static class ConstructorInfo {
        final String params;
        final String bodyContent;
        final String bodyWithoutConstructor;

        ConstructorInfo(String params, String bodyContent, String bodyWithoutConstructor) {
            this.params = params;
            this.bodyContent = bodyContent;
            this.bodyWithoutConstructor = bodyWithoutConstructor;
        }
    }

    /* ===================== 解析 class ===================== */
    private static ClassDef parseClassFromLines(String[] lines, int start) {
        String header = lines[start];
        Matcher m = CLASS_HEADER_PATTERN.matcher(header);
        if (!m.find()) {
            throw new RuntimeException("Invalid class declaration at line " + (start + 1) + ": " + header);
        }

        String className = m.group(1);
        String parentClass = m.group(2);

        StringBuilder body = new StringBuilder();
        int braceCount = 0;
        int i = start;

        for (; i < lines.length; i++) {
            String line = lines[i];

            if (i == start) {
                braceCount = updateBraceCount(line, 0);
                int openBrace = line.indexOf('{');
                if (openBrace != -1) {
                    String after = line.substring(openBrace + 1);
                    if (!after.trim().isEmpty()) {
                        body.append(after).append("\n");
                    }
                }
            } else {
                body.append(line).append("\n");
                braceCount = updateBraceCount(line, braceCount);
            }

            if (braceCount == 0) {
                String bodyStr = body.toString();
                if (bodyStr.endsWith("}\n")) {
                    bodyStr = bodyStr.substring(0, bodyStr.length() - 2);
                } else if (bodyStr.endsWith("}")) {
                    bodyStr = bodyStr.substring(0, bodyStr.length() - 1);
                }
                return new ClassDef(className, parentClass, bodyStr.trim(), i);
            }
        }

        throw new RuntimeException("Unclosed class starting at line " + (start + 1));
    }

    /* ===================== 转换 class ===================== */
    private static String convertClass(String className, String parentClass, String classBody) {
        ConstructorInfo ctorInfo = extractConstructor(classBody);
        String bodyWithoutCtor = ctorInfo.bodyWithoutConstructor;

        List<String> instanceFields = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> staticMembers = new ArrayList<>();

        parseMembers(className, bodyWithoutCtor, instanceFields, methods, staticMembers);

        Set<String> assignedInCtor = new HashSet<>();
        if (!ctorInfo.bodyContent.isEmpty()) {
            Matcher m = THIS_ASSIGN_PATTERN.matcher(ctorInfo.bodyContent);
            while (m.find()) assignedInCtor.add(m.group(1));
        }

        StringBuilder output = new StringBuilder();

        // 构造函数
        output.append("function ").append(className).append("(")
                .append(ctorInfo.params).append(") {\n");

        String userCtorBody = ctorInfo.bodyContent;

        // super()
        if (parentClass != null && !parentClass.trim().isEmpty()) {
            Matcher superMatcher = SUPER_CALL_PATTERN.matcher(userCtorBody);
            if (superMatcher.find()) {
                String args = superMatcher.group(1);
                output.append("  ")
                        .append(parentClass)
                        .append(".call(this")
                        .append(args.isEmpty() ? "" : ", " + args)
                        .append(");\n");
                userCtorBody = superMatcher.replaceFirst("").trim();
            }
        }

        // 实例字段初始化
        for (String field : instanceFields) {
            int eq = field.indexOf('=');
            if (eq == -1) continue;
            String fieldName = field.substring(0, eq).trim();
            if (assignedInCtor.contains(fieldName)) continue;
            if (!field.endsWith(";")) field += ";";
            output.append("  this.").append(field).append("\n");
        }

        // 剩余构造逻辑
        if (!userCtorBody.isEmpty()) {
            output.append(userCtorBody.trim()).append('\n');
        }

        output.append("}\n\n");

        // 原型链
        if (parentClass != null && !parentClass.trim().isEmpty()) {
            output.append("Object.setPrototypeOf(").append(className).append(".prototype, ")
                    .append(parentClass).append(".prototype);\n");
            output.append("Object.setPrototypeOf(").append(className).append(", ")
                    .append(parentClass).append(");\n\n");
        }

        // 静态成员
        for (String stat : staticMembers) {
            stat = stat.trim();
            if (stat.startsWith("get ") || stat.startsWith("set ")) {
                continue;
            } else if (stat.contains("=")) {
                output.append(className).append(".").append(stat).append("\n");
            } else if (stat.contains("(")) {
                String methodName = extractMethodName(stat);
                String paramsAndBody = stat.substring(stat.indexOf("("));
                output.append(className).append(".").append(methodName)
                        .append(" = function").append(paramsAndBody).append(";\n");
            }
        }
        if (!staticMembers.isEmpty()) {
            output.append("\n");
        }

        // 原型方法 & getter/setter
        for (String method : methods) {
            output.append(method).append("\n");
        }

        return output.toString()
                .replaceAll("\\n\\s*;\\s*\\n", "\n")
                .replaceAll("\\n\\s*}\\s*$", "\n}");
    }

    /* ===================== 提取 constructor ===================== */
    private static ConstructorInfo extractConstructor(String body) {
        String originalBody = body;
        int ctorIndex = -1;
        int searchFrom = 0;

        while (true) {
            ctorIndex = body.indexOf("constructor", searchFrom);
            if (ctorIndex == -1) break;

            if (ctorIndex > 0 && Character.isJavaIdentifierPart(body.charAt(ctorIndex - 1))) {
                searchFrom = ctorIndex + 1;
                continue;
            }

            int pos = ctorIndex + "constructor".length();
            while (pos < body.length() && Character.isWhitespace(body.charAt(pos))) pos++;
            if (pos >= body.length() || body.charAt(pos) != '(') {
                searchFrom = ctorIndex + 1;
                continue;
            }

            int parenDepth = 1;
            int paramStart = pos + 1;
            int i = paramStart;
            while (i < body.length() && parenDepth > 0) {
                char c = body.charAt(i);
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                i++;
            }
            if (parenDepth != 0) {
                searchFrom = ctorIndex + 1;
                continue;
            }
            int paramEnd = i - 1;

            while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
            if (i >= body.length() || body.charAt(i) != '{') {
                searchFrom = ctorIndex + 1;
                continue;
            }

            int braceCount = 1;
            int bodyStart = i + 1;
            int j = bodyStart;
            while (j < body.length() && braceCount > 0) {
                char c = body.charAt(j);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                j++;
            }
            if (braceCount != 0) {
                searchFrom = ctorIndex + 1;
                continue;
            }
            int bodyEnd = j - 1;

            String params = body.substring(paramStart, paramEnd).trim();
            String ctorBodyInner = body.substring(bodyStart, bodyEnd).trim();
            String fullCtor = body.substring(ctorIndex, j);
            String bodyWithoutCtor = originalBody.replaceFirst(Pattern.quote(fullCtor), "").trim();

            return new ConstructorInfo(params, ctorBodyInner, bodyWithoutCtor);
        }

        return new ConstructorInfo("", "", originalBody);
    }

    /* ===================== 解析成员 ===================== */
    private static void parseMembers(String className, String body,
                                     List<String> instanceFields,
                                     List<String> methods,
                                     List<String> staticMembers) {
        String clean = body.replaceAll("(?m)^\\s*//.*$", "")
                .replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");

        List<String> statements = splitIntoStatements(clean);

        for (String stmt : statements) {
            stmt = stmt.trim();
            if (stmt.isEmpty()) continue;

            if (stmt.startsWith("static ")) {
                staticMembers.add(stmt.substring(7).trim());
            } else if (stmt.startsWith("get ") || stmt.startsWith("set ")) {
                methods.add(convertGetterSetter(className, stmt));
            } else if (isValidMethodDecl(stmt)) {
                methods.add(convertMethod(className, stmt));
            } else if (stmt.contains("=") && isValidFieldName(stmt)) {
                instanceFields.add(stmt);
            } else if (isValidPlainField(stmt)) {
                instanceFields.add(stmt);
            } else {
                // unmatched, ignore
            }
        }
    }

    private static boolean isValidMethodDecl(String stmt) {
        int paren = stmt.indexOf('(');
        if (paren <= 0) return false;
        String name = stmt.substring(0, paren).trim();
        return isValidIdentifier(name) && stmt.endsWith("}");
    }

    private static boolean isValidPlainField(String stmt) {
        if (!stmt.endsWith(";")) return false;
        String name = stmt.substring(0, stmt.length() - 1).trim();
        return isValidIdentifier(name);
    }

    private static boolean isValidFieldName(String stmt) {
        int eq = stmt.indexOf('=');
        if (eq <= 0) return false;
        String name = stmt.substring(0, eq).trim();
        return isValidIdentifier(name);
    }

    private static boolean isValidIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        char first = s.charAt(0);
        if (first != '_' && first != '$' && !Character.isLetter(first)) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '_' && c != '$' && !Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    /* ===================== 语句分割 ===================== */
    private static List<String> splitIntoStatements(String code) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;

        for (String line : code.split("\\r?\\n")) {
            current.append(line).append("\n");

            for (char c : line.toCharArray()) {
                if (c == '{') braceDepth++;
                else if (c == '}') braceDepth--;
            }

            if (braceDepth == 0) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    result.add(stmt);
                    current.setLength(0);
                }
            }
        }

        if (current.length() > 0) {
            String stmt = current.toString().trim();
            if (!stmt.isEmpty()) result.add(stmt);
        }
        return result;
    }

    /* ===================== 辅助方法 ===================== */
    private static String convertMethod(String className, String methodDecl) {
        int paren = methodDecl.indexOf('(');
        if (paren == -1) return "// invalid method: " + methodDecl;
        String name = methodDecl.substring(0, paren).trim();
        String rest = methodDecl.substring(paren);
        return className + ".prototype." + name + " = function" + rest + ";";
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

    private static String extractMethodName(String stat) {
        int paren = stat.indexOf('(');
        return paren == -1 ? stat : stat.substring(0, paren).trim();
    }

    private static String indentLines(String code, String indent) {
        if (code.isEmpty()) return "";
        return Arrays.stream(code.split("\\r?\\n"))
                .map(line -> indent + line)
                .collect(Collectors.joining("\n"));
    }

    private static int updateBraceCount(String s, int current) {
        for (char c : s.toCharArray()) {
            if (c == '{') current++;
            else if (c == '}') current--;
        }
        return current;
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

    /* ===================== 后处理 ===================== */
    private static String postProcessing(String result) {
        result = expandShorthandReturnObjects(result);

        Matcher m2 = FUNCTION_WITH_DEFAULTS_PATTERN.matcher(result);
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

    private static String expandShorthandReturnObjects(String result) {
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
                if (!id.isEmpty() && !isValidIdentifier(id)) {
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

    /* ===================== 测试 ===================== */
    public static void main(String[] args) {
        String input = """
                ServerEvents.recipes((event) => {
                   let {kubejs} = event.recipes;
                   function add(i) {
                       return kubejs.shaped("minecraft:stone", [
                           "AAA",
                           "ABA",
                           "AAA"
                       ], {
                           A: "minecraft:stone",
                           B
                       })
                   }
                   add("minecraft:arrow");
                })
                """;
        System.out.println("转换前：");
        System.out.println(input);
        System.out.println("转换后：");
        System.out.println(parse(input));
    }
}