package com.whisent.kubeloader.scripts;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class ModernJSParser {

    // ===================== 缓存常用正则 =====================
    private static final Pattern CLASS_HEADER_PATTERN =
            Pattern.compile("^\\s*class\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?:extends\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*)?\\{?\\s*$");

    private static final Pattern THIS_ASSIGN_PATTERN =
            Pattern.compile("\\bthis\\.([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=");

    private static final Pattern RETURN_SHORTHAND_PATTERN =
            Pattern.compile("\\breturn\\s*\\{([^{}]*)\\}\\s*(?=;|\\n|\\r|$)");

    // 具名与匿名函数都要处理：类方法会被转换成 "Class.prototype.m = function(...) {"
    private static final Pattern FUNCTION_WITH_DEFAULTS_PATTERN =
            Pattern.compile("(\\bfunction\\s*(?:[a-zA-Z_$][a-zA-Z0-9_$]*)?\\s*\\([^)]*\\))(\\s*\\{)");

    // ===================== 入口 =====================
    public static String parse(String input) {
        return parse(input, true);
    }

    /**
     * 转换入口。
     *
     * @param rhinoTarget 目标引擎是否包含 Rhino。Rhino 会「静默算错」{@code ??} 这类语法，
     *                    所以要对它做硬失败；而 GraalJS 原生支持这些写法，同样拦下来属于误伤。
     */
    public static String parse(String input, boolean rhinoTarget) {
        if (input == null || input.isEmpty()) return input;
        try {
            return doParse(input, rhinoTarget);
        } catch (ModernJSParseException e) {
            throw e; // 已经是给用户看的错误（带原始行号与建议），原样抛
        } catch (RuntimeException | Error e) {
            // 转换器自己没预料到的失败：绝不能把原始异常放出去。
            // KLScriptLoader 只认 ModernJSParseException，否则这个异常会冒到 KubeJS 的
            // 脚本加载里，连累同一个包里的其它脚本。
            throw new ModernJSParseException(
                    "ModernJS 转换器在这个写法上失败了：" + e,
                    1, "",
                    "这多半是转换器的问题，请把这一行连同上下文反馈给作者；"
                            + "该脚本已被跳过，同包其它脚本不受影响");
        }
    }

    private static String doParse(String input, boolean rhinoTarget) {
        // 先过安全网：Rhino 会“静默算错”的语法（如 ??）在这里直接报错，
        // 而不是让它跑出错误结果
        if (rhinoTarget) {
            ModernJSSyntaxGuard.check(input);
        }
        StringBuilder result = new StringBuilder();
        String[] lines = input.split("\\r?\\n");
        int i = 0;

        while (i < lines.length) {
            String line = lines[i].trim();

            if (line.startsWith("class ")) {
                ClassDef def = parseClassFromLines(lines, i);
                // 必须补一个换行：convertClass 会去掉尾部空白，若直接拼接，类体最后的 "}"
                // 会和下一行粘成 "}let x = 1;"——既有 ASI 隐患，也会打乱后续行号对应
                result.append(convertClass(def.className, def.parentClass, def.body)).append("\n");
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
            throw new ModernJSParseException(
                    "这个 class 写法暂不支持：" + header.trim(),
                    start + 1, header.trim(),
                    "class 目前必须让「class 名字 {」独占一行再换行展开类体。"
                            + "与其它代码挤在同一行（例如函数体内联写的 class）请拆成多行；嵌套 class 同样暂不支持");
        }

        String className = m.group(1);
        String parentClass = m.group(2);

        StringBuilder body = new StringBuilder();
        int braceCount = 0;
        int i = start;
        // 数花括号必须按词法来（跳过字符串/注释/模板文本），否则类体里一个 "}" 字符串
        // 就会被当成类结束，把后面的代码粘成非法语句
        LexState lexState = new LexState();

        for (; i < lines.length; i++) {
            String line = lines[i];

            if (i == start) {
                braceCount = updateBraceCount(line, 0, lexState);
                int openBrace = line.indexOf('{');
                if (openBrace != -1) {
                    String after = line.substring(openBrace + 1);
                    if (!after.trim().isEmpty()) {
                        body.append(after).append("\n");
                    }
                }
            } else {
                body.append(line).append("\n");
                braceCount = updateBraceCount(line, braceCount, lexState);
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

        throw new ModernJSParseException(
                "从这个 class 开始找不到配对的 '}'",
                start + 1, lines[start].trim(),
                "检查类体的花括号是否配对；若类体里有内容含 '{' 或 '}'（长字符串、正则等），"
                        + "也欢迎把这一处反馈给作者");
    }

    /* ===================== 转换 class ===================== */
    private static String convertClass(String className, String parentClass, String classBody) {
        ConstructorInfo ctorInfo = extractConstructor(classBody);
        String bodyWithoutCtor = ctorInfo.bodyWithoutConstructor;

        List<String> instanceFields = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> staticMembers = new ArrayList<>();

        parseMembers(className, parentClass, bodyWithoutCtor, instanceFields, methods, staticMembers);

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

        // super()（括号配对扫描，正确处理 super(a(b())) 这类嵌套调用）
        if (parentClass != null && !parentClass.trim().isEmpty()) {
            SuperCall superCall = findSuperCall(userCtorBody);
            if (superCall != null) {
                output.append("  ")
                        .append(parentClass)
                        .append(".call(this")
                        .append(superCall.args.isEmpty() ? "" : ", " + superCall.args)
                        .append(");\n");
                userCtorBody = (userCtorBody.substring(0, superCall.start)
                        + userCtorBody.substring(superCall.end)).trim();
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

        // 剩余构造逻辑（super.member(...) 重写为父类原型调用）
        userCtorBody = rewriteSuperMemberAccess(userCtorBody, parentPrefix(parentClass, false));
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

        // 静态成员（静态上下文中 super.member 指向父类本身）
        String staticPrefix = parentPrefix(parentClass, true);
        for (String stat : staticMembers) {
            stat = rewriteSuperMemberAccess(stat.trim(), staticPrefix);
            if (stat.startsWith("get ") || stat.startsWith("set ")) {
                continue;
            } else if (isStaticMethodDecl(stat)) {
                // 方法要先判：参数默认值/方法体里都可能出现 '='，不能当作静态字段
                String methodName = extractMethodName(stat);
                String paramsAndBody = stat.substring(stat.indexOf("("));
                output.append(className).append(".").append(methodName)
                        .append(" = function").append(paramsAndBody).append(";\n");
            } else if (stat.contains("=")) {
                output.append(className).append(".").append(stat).append("\n");
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

            // 构造函数体的收尾花括号要按词法找（体里可能有 "}" 字符串）
            int closeBrace = findMatchingBrace(body, i);
            if (closeBrace == -1) {
                searchFrom = ctorIndex + 1;
                continue;
            }
            int bodyStart = i + 1;
            int bodyEnd = closeBrace;
            int j = closeBrace + 1;

            String params = body.substring(paramStart, paramEnd).trim();
            String ctorBodyInner = body.substring(bodyStart, bodyEnd).trim();
            String fullCtor = body.substring(ctorIndex, j);
            String bodyWithoutCtor = originalBody.replaceFirst(Pattern.quote(fullCtor), "").trim();

            return new ConstructorInfo(params, ctorBodyInner, bodyWithoutCtor);
        }

        return new ConstructorInfo("", "", originalBody);
    }

    /* ===================== 解析成员 ===================== */
    private static void parseMembers(String className, String parentClass, String body,
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
                methods.add(convertGetterSetter(className, parentClass, stmt));
            } else if (isValidMethodDecl(stmt)) {
                methods.add(convertMethod(className, parentClass, stmt));
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

    /**
     * 静态方法：形如 {@code name(...) {...}}，左括号前必须是合法的名字。
     * 用于与静态字段（{@code name = value}）区分——后者即使值里带括号
     * （如 {@code BASE = Math.max(1, 2)}）也不会被误判成方法。
     */
    private static boolean isStaticMethodDecl(String stat) {
        int paren = stat.indexOf('(');
        if (paren <= 0) return false;
        return isValidIdentifier(stat.substring(0, paren).trim());
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
        // 必须按词法数：成员体里出现 "}" 字符串（如 return "}";）时，
        // 天真的数法会把那一行当成成员结束，后面的代码就变成游离语句
        LexState lexState = new LexState();

        for (String line : code.split("\\r?\\n")) {
            current.append(line).append("\n");

            braceDepth = updateBraceCount(line, braceDepth, lexState);

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
    private static String convertMethod(String className, String parentClass, String methodDecl) {
        int paren = methodDecl.indexOf('(');
        if (paren == -1) return "// invalid method: " + methodDecl;
        String name = methodDecl.substring(0, paren).trim();
        String rest = rewriteSuperMemberAccess(methodDecl.substring(paren), parentPrefix(parentClass, false));
        return className + ".prototype." + name + " = function" + rest + ";";
    }

    private static String convertGetterSetter(String className, String parentClass, String decl) {
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
        body = rewriteSuperMemberAccess(body, parentPrefix(parentClass, false));

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

    /**
     * 词法扫描状态。字符串、模板串、块注释都可能跨行，所以状态要跨行带下去。
     *
     * <p>已知局限：模板串 {@code ${}} 里再嵌模板串（{@code `a${ `b${c}` }d`}）时，
     * 层数只记一份，内层的 {@code }} 会让状态提前回到文本模式。这种写法很少见，
     * 暂时接受。</p>
     */
    private static final class LexState {
        /** '\0' 表示不在普通字符串里，否则是 ' 或 " */
        char stringQuote;
        /** 在模板串的纯文本部分 */
        boolean inTemplateText;
        boolean inBlockComment;
        /** 模板串 ${} 的嵌套层数；>0 时按代码处理 */
        int templateExprDepth;
    }

    /**
     * 按词法把这一段里的花括号净增量累加进 {@code st.depth}，跳过字符串、注释、
     * 模板串文本（模板串 {@code ${}} 里面是代码，照常算）。
     *
     * <p>为什么必须这么做：{@code class A { m() { return "}"; } }} 里，
     * 字符串中那个 {@code }} 会让天真的数法提前认定类体结束，把后面的代码粘成非法语句。</p>
     */
    private static int updateBraceCount(String line, int current, LexState st) {
        int n = line.length();
        for (int i = 0; i < n; i++) {
            char c = line.charAt(i);
            char next = i + 1 < n ? line.charAt(i + 1) : '\0';

            if (st.inBlockComment) {
                if (c == '*' && next == '/') {
                    st.inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (st.stringQuote != '\0') {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == st.stringQuote) st.stringQuote = '\0';
                continue;
            }
            if (st.inTemplateText) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == '`') {
                    st.inTemplateText = false;
                    continue;
                }
                if (c == '$' && next == '{') {
                    st.inTemplateText = false;
                    st.templateExprDepth = 1;
                    i++;
                    continue;
                }
                continue; // 模板文本里的 {} 不算
            }

            // 以下都是「代码」部分
            if (c == '/' && next == '/') return current; // 行注释，本行后面都不是代码
            if (c == '/' && next == '*') {
                st.inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                st.stringQuote = c;
                continue;
            }
            if (c == '`') {
                st.inTemplateText = true;
                continue;
            }
            if (c == '{') {
                if (st.templateExprDepth > 0) st.templateExprDepth++;
                else current++;
            } else if (c == '}') {
                if (st.templateExprDepth > 0) {
                    if (--st.templateExprDepth == 0) st.inTemplateText = true; // 回到模板文本
                } else {
                    current--;
                }
            }
        }
        return current;
    }

    /** 找出与 start 处 '{' 配对的那个 '}' 下标（按词法跳过字符串/注释/模板文本），找不到返回 -1。 */
    private static int findMatchingBrace(String s, int start) {
        if (start >= s.length() || s.charAt(start) != '{') return -1;
        LexState st = new LexState();
        boolean started = false;
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : '\0';

            if (st.inBlockComment) {
                if (c == '*' && next == '/') {
                    st.inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (st.stringQuote != '\0') {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == st.stringQuote) st.stringQuote = '\0';
                continue;
            }
            if (st.inTemplateText) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == '`') {
                    st.inTemplateText = false;
                    continue;
                }
                if (c == '$' && next == '{') {
                    st.inTemplateText = false;
                    st.templateExprDepth = 1;
                    i++;
                    continue;
                }
                continue;
            }
            if (c == '/' && next == '/') return -1; // 单行情况，后面没代码了
            if (c == '/' && next == '*') {
                st.inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                st.stringQuote = c;
                continue;
            }
            if (c == '`') {
                st.inTemplateText = true;
                continue;
            }
            if (c == '{') {
                if (st.templateExprDepth > 0) st.templateExprDepth++;
                else {
                    depth++;
                    started = true;
                }
            } else if (c == '}') {
                if (st.templateExprDepth > 0) {
                    if (--st.templateExprDepth == 0) st.inTemplateText = true;
                } else if (started && --depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /* ===================== super(...) 扫描 ===================== */

    /** super(...) 调用的扫描结果 */
    private static final class SuperCall {
        final int start;   // "super" 起始下标
        final int end;     // 右括号之后的下标（不含）
        final String args; // 括号内参数原文（已 trim）

        SuperCall(int start, int end, String args) {
            this.start = start;
            this.end = end;
            this.args = args;
        }
    }

    /**
     * 查找 super(...) 调用位置。使用括号配对扫描而非正则，
     * 以支持嵌套调用（如 super(a(b()))）以及字符串/注释中的括号。
     *
     * @return 扫描结果；未找到或括号不配对时返回 null
     */
    private static SuperCall findSuperCall(String code) {
        for (int i = 0; i + 5 <= code.length(); i++) {
            if (!code.startsWith("super", i)) continue;
            // 避免匹配到标识符内部（如 xsuper、superX）
            if (i > 0 && Character.isJavaIdentifierPart(code.charAt(i - 1))) continue;

            int p = i + 5;
            while (p < code.length() && Character.isWhitespace(code.charAt(p))) p++;
            if (p >= code.length() || code.charAt(p) != '(') continue;

            int depth = 0;
            int argsStart = p + 1;
            for (int j = p; j < code.length(); j++) {
                char c = code.charAt(j);

                if (c == '"' || c == '\'' || c == '`') {
                    j = skipStringLiteral(code, j) - 1;
                    continue;
                }
                if (c == '/' && j + 1 < code.length()) {
                    char next = code.charAt(j + 1);
                    if (next == '/') {
                        int nl = code.indexOf('\n', j);
                        j = (nl == -1) ? code.length() : nl - 1;
                        continue;
                    }
                    if (next == '*') {
                        int close = code.indexOf("*/", j + 2);
                        j = (close == -1) ? code.length() : close + 1;
                        continue;
                    }
                }

                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return new SuperCall(i, j + 1, code.substring(argsStart, j).trim());
                    }
                }
            }
            return null; // 括号不配对
        }
        return null;
    }

    /** 跳过字符串/模板字符串字面量，返回结束引号之后的下标 */
    private static int skipStringLiteral(String code, int start) {
        char quote = code.charAt(start);
        int i = start + 1;
        while (i < code.length()) {
            char c = code.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) return i + 1;
            i++;
        }
        return code.length();
    }

    /**
     * 计算 super 成员的父类访问前缀。
     * 实例成员用 "Parent.prototype"，静态成员用 "Parent"（静态上下文中 super 指向父类本身）。
     * 无父类时返回空串。
     */
    private static String parentPrefix(String parentClass, boolean isStatic) {
        if (parentClass == null || parentClass.trim().isEmpty()) return "";
        String p = parentClass.trim();
        return isStatic ? p : p + ".prototype";
    }

    /**
     * 将方法体中的 super.member 访问重写为对父类的显式访问（Rhino 不支持 super 关键字）：
     *   super.foo(args)  ->  {parentPrefix}.foo.call(this, args)
     *   super.foo        ->  {parentPrefix}.foo
     * 跳过字符串/模板字符串与注释中的内容。
     *
     * @param parentPrefix 由 {@link #parentPrefix(String, boolean)} 计算；为空时原样返回
     */
    private static String rewriteSuperMemberAccess(String body, String parentPrefix) {
        if (body == null || body.isEmpty() || parentPrefix == null || parentPrefix.isEmpty()) {
            return body;
        }

        StringBuilder sb = new StringBuilder(body.length() + 32);
        int i = 0;
        int n = body.length();

        while (i < n) {
            char c = body.charAt(i);

            if (c == '"' || c == '\'' || c == '`') {
                int end = skipStringLiteral(body, i);
                sb.append(body, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n) {
                char next = body.charAt(i + 1);
                if (next == '/') {
                    int nl = body.indexOf('\n', i);
                    int end = (nl == -1) ? n : nl;
                    sb.append(body, i, end);
                    i = end;
                    continue;
                }
                if (next == '*') {
                    int close = body.indexOf("*/", i + 2);
                    int end = (close == -1) ? n : close + 2;
                    sb.append(body, i, end);
                    i = end;
                    continue;
                }
            }

            if (c == 's' && body.startsWith("super", i)
                    && (i == 0 || !Character.isJavaIdentifierPart(body.charAt(i - 1)))) {
                int p = i + 5;
                while (p < n && Character.isWhitespace(body.charAt(p))) p++;

                if (p < n && body.charAt(p) == '.') {
                    int nameStart = p + 1;
                    while (nameStart < n && Character.isWhitespace(body.charAt(nameStart))) nameStart++;

                    int nameEnd = nameStart;
                    while (nameEnd < n && (Character.isLetterOrDigit(body.charAt(nameEnd))
                            || body.charAt(nameEnd) == '_' || body.charAt(nameEnd) == '$')) {
                        nameEnd++;
                    }

                    if (nameEnd > nameStart) {
                        String name = body.substring(nameStart, nameEnd);

                        int after = nameEnd;
                        while (after < n && Character.isWhitespace(body.charAt(after))) after++;

                        if (after < n && body.charAt(after) == '(') {
                            // super.foo(...) -> Parent.prototype.foo.call(this, ...)：消费 '('
                            int argStart = after + 1;
                            int k = argStart;
                            while (k < n && Character.isWhitespace(body.charAt(k))) k++;
                            boolean noArgs = k < n && body.charAt(k) == ')';

                            sb.append(parentPrefix).append('.').append(name).append(".call(this");
                            if (!noArgs) sb.append(", ");
                            i = argStart;
                        } else {
                            // super.foo -> Parent.prototype.foo
                            sb.append(parentPrefix).append('.').append(name);
                            i = nameEnd;
                        }
                        continue;
                    }
                }
            }

            sb.append(c);
            i++;
        }

        return sb.toString();
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
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
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
                matcher.appendReplacement(sb, Matcher.quoteReplacement(expanded.toString()));
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