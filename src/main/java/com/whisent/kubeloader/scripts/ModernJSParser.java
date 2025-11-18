package com.whisent.kubeloader.scripts;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class ModernJSParser {

    public static String parse(String input) {
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

    /* ===================== 解析入口 ===================== */
    private static ClassDef parseClassFromLines(String[] lines, int start) {
        String header = lines[start];
        Pattern p = Pattern.compile("^\\s*class\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?:extends\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*)?\\{?\\s*$");
        Matcher m = p.matcher(header);
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
                braceCount = countChar(line, '{') - countChar(line, '}');
                int openBrace = line.indexOf('{');
                if (openBrace != -1) {
                    String after = line.substring(openBrace + 1);
                    if (!after.trim().isEmpty()) {
                        body.append(after).append("\n");
                    }
                }
            } else {
                body.append(line).append("\n");
                braceCount += countChar(line, '{') - countChar(line, '}');
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

    /* ===================== 转换主函数 ===================== */
    private static String convertClass(String className, String parentClass, String classBody) {
        ConstructorInfo ctorInfo = extractConstructor(classBody);
        String bodyWithoutCtor = ctorInfo.bodyWithoutConstructor;

        List<String> instanceFields = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> staticMembers = new ArrayList<>();

        parseMembers(className, bodyWithoutCtor, instanceFields, methods, staticMembers);

        /* --- 新增：找出构造函数里已经赋值过的字段 --- */
        Set<String> assignedInCtor = new HashSet<>();
        if (!ctorInfo.bodyContent.isEmpty()) {
            Matcher m = Pattern.compile("\\bthis\\.([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=").matcher(ctorInfo.bodyContent);
            while (m.find()) assignedInCtor.add(m.group(1));
        }

        StringBuilder output = new StringBuilder();

        /* 构造函数 */
        output.append("function ").append(className).append("(")
                .append(ctorInfo.params).append(") {\n");
        /* 用户构造逻辑（含 super → Parent.call） */
        String userCtorBody = ctorInfo.bodyContent;


        /* 1. super() 必须绝对第一行（如果存在） */
        if (parentClass != null && !parentClass.trim().isEmpty()) {
            Matcher superMatcher = Pattern
                    .compile("\\bsuper\\s*\\(([^)]*)\\)")
                    .matcher(userCtorBody);
            if (superMatcher.find()) {
                String args = superMatcher.group(1);          // ← 1. 改名
                output.append("  ")
                        .append(parentClass)
                        .append(".call(this")
                        .append(args.isEmpty() ? "" : ", " + args) // ← 2. 复用原始文本
                        .append(");\n");
                userCtorBody = superMatcher.replaceFirst("").trim(); // 删掉原 super()
            }
        }

        /* 2. 实例字段初始化（在 super 之后、用户逻辑之前） */
        for (String field : instanceFields) {
            int eq = field.indexOf('=');
            if (eq == -1) continue;
            String fieldName = field.substring(0, eq).trim();
            if (assignedInCtor.contains(fieldName)) continue;
            if (!field.endsWith(";")) field += ";";
            output.append("  this.").append(field).append("\n");
        }

        /* 3. 剩余用户构造逻辑 */
        if (!userCtorBody.isEmpty()) {
            output.append(userCtorBody.trim()).append('\n');
        }

        output.append("}\n\n");

        /* 原型链 */
        if (parentClass != null && !parentClass.trim().isEmpty()) {
            output.append("Object.setPrototypeOf(").append(className).append(".prototype, ")
                    .append(parentClass).append(".prototype);\n");
            output.append("Object.setPrototypeOf(").append(className).append(", ")
                    .append(parentClass).append(");\n\n");
        }

        /* 静态成员 */
        for (String stat : staticMembers) {
            stat = stat.trim();
            if (stat.startsWith("get ") || stat.startsWith("set ")) {
                continue; // skip static getter/setter
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

        /* 原型方法 & getter/setter */
        for (String method : methods) {
            output.append(method).append("\n");
        }

        String raw = output.toString()
                .replaceAll("\\n\\s*;\\s*\\n", "\n")   // 删掉孤零零的 ;
                .replaceAll("\\n\\s*}\\s*$", "\n}");   // 让 } 单独一行
        return raw;
    }

    /* ===================== 安全提取 constructor ===================== */
    private static ConstructorInfo extractConstructor(String body) {
        String originalBody = body;
        int ctorIndex = -1;
        int searchFrom = 0;

        while (true) {
            ctorIndex = body.indexOf("constructor", searchFrom);
            if (ctorIndex == -1) break;

            /* 完整单词检查 */
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

            /* 匹配参数括号 */
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

            /* 找构造函数体 {} */
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
        /* 移除注释 */
        String clean = body.replaceAll("(?m)^\\s*//.*$", "")
                .replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");

        List<String> statements = splitIntoStatements(clean);

        for (String stmt : statements) {
            stmt = stmt.trim();
            if (stmt.isEmpty()) continue;

            if (stmt.startsWith("static ")) {
                System.err.println(">>> stmt: " + stmt + ", length: " + stmt.length());
                staticMembers.add(stmt.substring(7).trim());
            } else if (stmt.startsWith("get ") || stmt.startsWith("set ")) {
                methods.add(convertGetterSetter(className, stmt));
            } else if (stmt.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*\\([^)]*\\)\\s*\\{")) {
                methods.add(convertMethod(className, stmt));
            } else if (stmt.contains("=") && isValidFieldName(stmt)) {
                instanceFields.add(stmt);
            } else if (stmt.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*;")) { // 匹配纯字段声明
                instanceFields.add(stmt);
            } else if (stmt.matches("^\\s*[a-zA-Z_$][a-zA-Z0-9_$]*\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*\\}$")) {
                methods.add(convertMethod(className, stmt));
            } else {
                System.err.println(">>> unmatched: " + stmt);
            }
            /* 其余情况忽略（如纯表达式） */
        }
    }

    /* ===================== 语句分割（核心修复） ===================== */
    private static List<String> splitIntoStatements(String code) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        int braceDepth = 0;          // 只统计 {}
        boolean insideArrow = false; // 是否正位于 => 的函数体里

        for (String line : code.split("\\r?\\n")) {
            current.append(line).append("\n");

            /* 更新 {} 深度 */
            for (char c : line.toCharArray()) {
                if (c == '{') braceDepth++;
                else if (c == '}') braceDepth--;
            }

            /* 进入箭头函数体的标记：=> 后面紧跟着 { */
            if (!insideArrow && braceDepth == 0) {
                String tmp = line.replaceAll("\\s+", "");
                if (tmp.contains("=>{")) insideArrow = true;
            } else if (insideArrow && braceDepth == 0) {
                insideArrow = false;
            }

            /* 只有顶层才切分语句 */
            if (braceDepth == 0 && !insideArrow) {
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

    private static boolean isValidFieldName(String stmt) {
        int eq = stmt.indexOf('=');
        if (eq <= 0) return false;
        String name = stmt.substring(0, eq).trim();
        return name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*");
    }

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

    private static int countChar(String s, char c) {
        int count = 0;
        for (char ch : s.toCharArray()) if (ch == c) count++;
        return count;
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
    private static String postProcessing(String result) {
        /* 1. 先展开 {a,b} → {a:a,b:b}  */
        result = expandShorthandReturnObjects(result);

        /* 2. 再降级默认参数（继续用 result，链式替换） */
        Pattern p2 = Pattern.compile("(\\bfunction\\s+\\w+\\s*\\([^)]*\\))(\\s*\\{)");
        Matcher m2 = p2.matcher(result);
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            String head  = m2.group(1);
            String brace = m2.group(2);

            int pStart = head.indexOf('(') + 1;
            int pEnd   = head.lastIndexOf(')');
            String[] params = head.substring(pStart, pEnd).split(",");

            StringBuilder dftStmts = new StringBuilder();
            for (String p : params) {
                p = p.trim();
                int eq = p.indexOf('=');
                if (eq > 0) {
                    String name = p.substring(0, eq).trim();
                    String val  = p.substring(eq + 1).trim();
                    dftStmts.append("  ")
                            .append(name).append(" = ")
                            .append(name).append(" === undefined ? ")
                            .append(val).append(" : ").append(name).append(";\n");
                }
            }
            String cleanSig = head.replaceAll("\\s*=\\s*[^,)]+", "");
            m2.appendReplacement(sb2,
                    Matcher.quoteReplacement(cleanSig + brace + "\n" + dftStmts.toString()));
        }
        m2.appendTail(sb2);          // ← 这里已经把「全文」写进 sb2
        return sb2.toString();
    }
    // ========== 后处理：将 return {a, b} 转为 return {a: a, b: b} ==========

    private static String expandShorthandReturnObjects(String result) {
        Pattern pattern = Pattern.compile(

                "\\breturn\\s*\\{([^{}]*)\\}\\s*(?=;|\\n|\\r|$)"

        );

        Matcher matcher = pattern.matcher(result);

        StringBuffer sb = new StringBuffer();


        while (matcher.find()) {

            String inner = matcher.group(1).trim();

            if (inner.isEmpty()) {

                matcher.appendReplacement(sb, "return {}");

                continue;

            }


            String[] parts = inner.split(",");

            StringBuilder expanded = new StringBuilder("return {");

            boolean first = true;

            boolean shouldExpand = true;


            for (String part : parts) {

                String id = part.trim();

                if (id.isEmpty()) continue;


                // 必须是纯标识符（不能有 ...、[、"、=、: 等）

                if (!id.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {

                    shouldExpand = false;

                    break;

                }

            }


            if (!shouldExpand) {
                // 不转换，原样保留
                matcher.appendReplacement(sb, matcher.group(0));
            } else {
                for (String part : parts) {
                    String id = part.trim();
                    if (id.isEmpty()) continue;
                    if (!first) {
                        expanded.append(", ");
                    }
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

    /* ===================== 测试主方法 ===================== */
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
""";    System.out.println("转换前：");
        System.out.println(input);
        System.out.println("转换后：");
        System.out.println(parse(input));
    }
}