package com.whisent.kubeloader.scripts.modernjs.plugin;

import com.whisent.kubeloader.scripts.modernjs.ModernJSPluginManager;
import com.whisent.kubeloader.scripts.modernjs.SourceTransformResult;
import com.whisent.kubeloader.scripts.modernjs.plugin.impl.ClassMemberPlugin;
import com.whisent.kubeloader.scripts.modernjs.plugin.impl.SourcePlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassBlockPlugin implements SourcePlugin {
    private static final Pattern CLASS_HEADER_PATTERN =
            Pattern.compile("^\\s*class\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?:extends\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*)?\\{?\\s*$");

    private static final Pattern SUPER_CALL_PATTERN =
            Pattern.compile("\\bsuper\\s*\\(([^)]*)\\)");

    private static final Pattern THIS_ASSIGN_PATTERN =
            Pattern.compile("\\bthis\\.([a-zA-Z_$][a-zA-Z0-9_$]*)\\s=");

    @Override
    public String syntax() {
        return "class <name> [extends <parent>] { ... }";
    }

    @Override
    public boolean matches(String trimmedLine) {
        return trimmedLine.startsWith("class ");
    }

    @Override
    public SourceTransformResult transform(String[] lines, int startIndex) {
        ClassDef def = parseClassFromLines(lines, startIndex);
        return new SourceTransformResult(convertClass(def.className, def.parentClass, def.body), def.endLineIndex);
    }

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
        output.append("function ").append(className).append("(")
                .append(ctorInfo.params).append(") {\n");

        String userCtorBody = ctorInfo.bodyContent;

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

        for (String field : instanceFields) {
            int eq = field.indexOf('=');
            if (eq != -1) {
                String fieldName = field.substring(0, eq).trim();
                if (!assignedInCtor.contains(fieldName)) {
                    if (!field.endsWith(";")) field += ";";
                    output.append("  this.").append(field).append("\n");
                }
            }
        }

        if (!userCtorBody.isEmpty()) {
            output.append(userCtorBody.trim()).append('\n');
        }

        output.append("}\n\n");

        if (parentClass != null && !parentClass.trim().isEmpty()) {
            output.append("Object.setPrototypeOf(").append(className).append(".prototype, ")
                    .append(parentClass).append(".prototype);\n");
            output.append("Object.setPrototypeOf(").append(className).append(", ")
                    .append(parentClass).append(");\n\n");

        }

        for (String stat : staticMembers) {
            stat = stat.trim();
            if (!(stat.startsWith("get ") || stat.startsWith("set "))) {
                if (stat.contains("=")) {
                    output.append(className).append(".").append(stat).append("\n");
                } else if (stat.contains("(")) {
                    String methodName = extractMethodName(stat);
                    String paramsAndBody = stat.substring(stat.indexOf("("));
                    output.append(className).append(".").append(methodName)
                            .append(" = function").append(paramsAndBody).append(";\n");
                }
            }
        }
        if (!staticMembers.isEmpty()) {
            output.append("\n");
        }

        for (String method : methods) {
            output.append(method).append("\n");
        }

        return output.toString()
            .replaceAll("\\n\\s*;\\s*\\n", "\n")
            .replaceAll("\\n\\s*}\\s*$", "\n}");
    }

    private static ConstructorInfo extractConstructor(String body) {
        String originalBody = body;
        int searchFrom = 0;

        while (true) {
            int ctorIndex = body.indexOf("constructor", searchFrom);
            if (ctorIndex == -1) {
                break;
            }

            if (ctorIndex > 0 && Character.isJavaIdentifierPart(body.charAt(ctorIndex - 1))) {
                searchFrom = ctorIndex + 1;
            } else {
                int pos = ctorIndex + "constructor".length();
                while (pos < body.length() && Character.isWhitespace(body.charAt(pos))) pos++;
                if (pos >= body.length() || body.charAt(pos) != '(') {
                    searchFrom = ctorIndex + 1;
                } else {
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
                    } else {
                        int paramEnd = i - 1;

                        while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
                        if (i >= body.length() || body.charAt(i) != '{') {
                            searchFrom = ctorIndex + 1;
                        } else {
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
                            } else {
                                int bodyEnd = j - 1;

                                String params = body.substring(paramStart, paramEnd).trim();
                                String ctorBodyInner = body.substring(bodyStart, bodyEnd).trim();
                                String fullCtor = body.substring(ctorIndex, j);
                                String bodyWithoutCtor = originalBody.replaceFirst(java.util.regex.Pattern.quote(fullCtor), "").trim();

                                return new ConstructorInfo(params, ctorBodyInner, bodyWithoutCtor);
                            }
                        }
                    }
                }
            }
        }

        return new ConstructorInfo("", "", originalBody);
    }

    private static void parseMembers(String className, String body,
                                     List<String> instanceFields,
                                     List<String> methods,
                                     List<String> staticMembers) {
        String clean = body.replaceAll("(?m)^\\s*//.*$", "")
                .replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");

        List<String> statements = splitIntoStatements(clean);
        ModernJSPluginManager plugins = ModernJSPluginManager.INSTANCE;

        for (String stmt : statements) {
            stmt = stmt.trim();
            if (!stmt.isEmpty()) {
                ClassMemberPlugin plugin = plugins.findClassMemberPlugin(stmt);
                if (plugin != null) {
                    plugin.apply(className, stmt, instanceFields, methods, staticMembers);
                }
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

    static boolean isValidIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        char first = s.charAt(0);
        if (first != '_' && first != '$' && !Character.isLetter(first)) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '_' && c != '$' && !Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

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
        return java.util.Arrays.stream(code.split("\\r?\\n"))
                .map(line -> indent + line)
                .collect(java.util.stream.Collectors.joining("\n"));
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

    private record ClassDef(String className, String parentClass, String body, int endLineIndex) {
    }

    private record ConstructorInfo(String params, String bodyContent, String bodyWithoutConstructor) {
    }
}
