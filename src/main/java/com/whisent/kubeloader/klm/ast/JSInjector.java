package com.whisent.kubeloader.klm.ast;

import com.whisent.kubeloader.klm.dsl.MixinDSL;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Node;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JSInjector {
    private static final String PLACEHOLDER_PREFIX = "var __KubeLoaderInject_";
    private static final String PLACEHOLDER_SUFFIX = " = KubeLoader;";

    // 存储每个 DSL 的唯一占位符
    private static final Map<MixinDSL, String> placeholderMap = new HashMap<>();
    private static int placeholderCounter = 0;

    // ==================== 定位表达式 ====================
    // .type() 已指定类型，.in() 只需写名称
    // 示例: myFunc, console.log

    static class Locator {
        final String target;
        Locator child;

        Locator(String target) { this.target = target; }

        @Override
        public String toString() {
            return child != null ? target + " > " + child : target;
        }
    }

    /**
     * 解析定位表达式
     * 语法: 名称 > 名称
     */
    static Locator parseLocator(String expression) {
        if (expression == null || expression.trim().isEmpty()) return null;
        String trimmed = expression.trim();

        if (trimmed.contains(">")) {
            String[] parts = trimmed.split("\\s*>\\s*");
            Locator root = null, current = null;
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;
                Locator expr = new Locator(part);
                if (root == null) { root = expr; current = expr; }
                else { current.child = expr; current = expr; }
            }
            return root;
        }
        return new Locator(trimmed);
    }

    /**
     * 在 AST 中查找匹配定位表达式的节点
     */
    static AstNode findNode(AstRoot root, Locator expr, String type) {
        if (root == null || expr == null) return null;

        if (expr.child != null) {
            List<AstNode> parents = collectByType(root, type);
            parents = filterByName(parents, type, expr.target);
            if (!parents.isEmpty()) {
                return findInSubtree(parents.get(0), expr.child, type);
            }
            return null;
        }

        List<AstNode> candidates = collectByType(root, type);
        candidates = filterByName(candidates, type, expr.target);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static AstNode findInSubtree(AstNode subtreeRoot, Locator expr, String type) {
        List<AstNode> candidates = collectByType(subtreeRoot, type);
        candidates = filterByName(candidates, type, expr.target);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * 收集指定类型的所有节点
     */
    private static List<AstNode> collectByType(AstNode node, String nodeType) {
        List<AstNode> results = new ArrayList<>();
        collectRecursive(node, nodeType, results);
        return results;
    }

    private static void collectRecursive(AstNode node, String nodeType, List<AstNode> results) {
        if (node == null) return;
        if (matchesType(node, nodeType)) results.add(node);
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof AstNode) collectRecursive((AstNode) child, nodeType, results);
        }
    }

    /**
     * 按名称过滤节点
     */
    private static List<AstNode> filterByName(List<AstNode> nodes, String nodeType, String target) {
        if (target == null) return nodes;
        List<AstNode> filtered = new ArrayList<>();
        for (AstNode node : nodes) {
            if (matchesName(node, nodeType, target)) filtered.add(node);
        }
        return filtered;
    }

    /**
     * 判断节点是否匹配名称
     */
    private static boolean matchesName(AstNode node, String nodeType, String name) {
        return switch (nodeType.toLowerCase()) {
            case "function", "functiondeclaration", "functiondecleration" -> {
                if (node instanceof FunctionNode func) {
                    Name funcName = func.getFunctionName();
                    yield funcName != null && name.equals(funcName.getIdentifier());
                }
                yield false;
            }
            case "call", "functioncall", "function_call" -> {
                if (node instanceof FunctionCall call) {
                    yield name.equals(extractCallTarget(call.getTarget()));
                }
                yield false;
            }
            case "variable", "variabledeclaration", "var" -> {
                if (node instanceof VariableDeclaration varDecl) {
                    boolean match = false;
                    for (var init : varDecl.getVariables()) {
                        if (init.getTarget() instanceof Name varName && name.equals(varName.getIdentifier())) {
                            match = true; break;
                        }
                    }
                    yield match;
                }
                yield false;
            }
            case "event", "eventsubscription" -> {
                if (node instanceof ExpressionStatement stmt && stmt.getExpression() instanceof FunctionCall call) {
                    yield name.equals(extractCallTarget(call.getTarget()));
                }
                yield false;
            }
            default -> false;
        };
    }

    private static boolean matchesType(AstNode node, String nodeType) {
        return switch (nodeType.toLowerCase()) {
            case "function", "functiondeclaration", "functiondecleration" -> node instanceof FunctionNode;
            case "variable", "variabledeclaration", "var" -> node instanceof VariableDeclaration;
            case "expression", "expressionstatement" -> node instanceof ExpressionStatement;
            case "call", "functioncall", "function_call" -> node instanceof FunctionCall;
            case "block" -> node instanceof Block;
            case "if", "ifstatement" -> node instanceof IfStatement;
            case "return", "returnstatement" -> node instanceof ReturnStatement;
            case "switch", "switchstatement" -> node instanceof SwitchStatement;
            case "try", "trystatement" -> node instanceof TryStatement;
            case "throw", "throwstatement" -> node instanceof ThrowStatement;
            case "break", "breakstatement" -> node instanceof BreakStatement;
            case "continue", "continuestatement" -> node instanceof ContinueStatement;
            case "do", "dostatement" -> node instanceof DoLoop;
            case "loop", "loopstatement" -> node instanceof Loop;
            case "scope" -> node instanceof Scope;
            case "name" -> node instanceof Name;
            case "array", "arrayliteral" -> node instanceof ArrayLiteral;
            case "object", "objectliteral" -> node instanceof ObjectLiteral;
            case "string", "stringliteral" -> node instanceof StringLiteral;
            case "number", "numberliteral" -> node instanceof NumberLiteral;
            case "assignment" -> node instanceof Assignment;
            case "infixexpression", "infix" -> node instanceof InfixExpression;
            case "conditional", "conditionalexpression" -> node instanceof ConditionalExpression;
            case "propertyget", "property" -> node instanceof PropertyGet;
            case "elementget", "element" -> node instanceof ElementGet;
            case "new", "newexpression" -> node instanceof NewExpression;
            case "empty" -> node instanceof EmptyExpression;
            case "label" -> node instanceof LabeledStatement;
            case "comment" -> node instanceof Comment;
            case "event", "eventsubscription" -> node instanceof ExpressionStatement;
            default -> false;
        };
    }

    private static String extractCallTarget(AstNode target) {
        if (target instanceof Name name) return name.getIdentifier();
        if (target instanceof PropertyGet prop) return extractCallTarget(prop.getLeft()) + "." + prop.getRight().getString();
        return target.getString();
    }

    // ==================== 注入逻辑 ====================

    public static void injectFromDSL(AstRoot root, MixinDSL dsl) {
        String type = dsl.getType();
        if (Objects.equals(type, "FunctionDeclaration")) {
            injectFunction(root, dsl);
        } else if (Objects.equals(type, "EventSubscription")) {
            injectEvent(root, dsl);
        }
    }

    public static void injectFunction(AstRoot root, MixinDSL dsl) {
        String position = dsl.getAt();
        String type = dsl.getType();
        if (position == null) {
            dsl.getFile().pack.manager.scriptType.console.error("Unknown injection position for DSL: " + dsl);
            return;
        }

        Locator locator = parseLocator(dsl.getLocatorExpression());
        if (locator == null) {
            dsl.getFile().pack.manager.scriptType.console.error("Invalid locator expression: " + dsl.getLocatorExpression());
            return;
        }

        AstNode targetNode = findNode(root, locator, type);
        if (targetNode == null) {
            System.out.println("未找到匹配的节点: " + locator);
            return;
        }

        if (!(targetNode instanceof FunctionNode targetFunction)) {
            System.out.println("定位到的节点不是 FunctionNode: " + targetNode.getClass().getSimpleName());
            return;
        }

        String placeholder = registerPlaceholder(dsl);
        int offset = dsl.getOffset();
        if (offset >= 0) {
            injectAtOffset(targetFunction, offset, placeholder);
        } else {
            switch (position) {
                case "head" -> injectAtFunctionHead(targetFunction, placeholder);
                case "tail" -> injectAtFunctionTail(targetFunction, placeholder);
                default -> dsl.getFile().pack.manager.scriptType.console.error("Unknown injection position: " + position);
            }
        }
    }

    public static void injectEvent(AstRoot root, MixinDSL dsl) {
        String eventName = dsl.getTarget();
        if (eventName == null || eventName.isEmpty()) {
            dsl.getFile().pack.manager.scriptType.console.error("Event name is required for EventSubscription type");
            return;
        }
        System.out.println("EventSubscription injection via EventProbe: " + eventName);
    }

    /**
     * 在函数体内指定行偏移量插入占位符
     */
    private static void injectAtOffset(FunctionNode targetFunction, int offset, String placeholder) {
        System.out.println("尝试在函数 " + getFunctionName(targetFunction) + " 偏移量 " + offset + " 处插入占位符");
        List<AstNode> injectNodes = parsePlaceholderNodes(placeholder);
        if (injectNodes.isEmpty()) return;

        AstNode functionBody = targetFunction.getBody();
        if (!(functionBody instanceof Block block)) return;

        if (offset == 0) {
            for (int i = injectNodes.size() - 1; i >= 0; i--) {
                AstNode nodeCopy = copyNode(injectNodes.get(i));
                nodeCopy.setPosition(-1);
                nodeCopy.setLength(0);
                block.addChildToFront(nodeCopy);
            }
        } else {
            for (AstNode injectNode : injectNodes) {
                AstNode nodeCopy = copyNode(injectNode);
                nodeCopy.setPosition(-1);
                nodeCopy.setLength(0);
                block.addChildToBack(nodeCopy);
            }
        }
        System.out.println("成功在函数 " + getFunctionName(targetFunction) + " 偏移量 " + offset + " 处插入占位符");
    }

    public static void injectAtFunctionHead(FunctionNode targetFunction, String placeholder) {
        System.out.println("尝试在函数 " + getFunctionName(targetFunction) + " 开头插入占位符");
        List<AstNode> injectNodes = parsePlaceholderNodes(placeholder);
        if (!injectNodes.isEmpty() && targetFunction.getBody() instanceof Block block) {
            for (int i = injectNodes.size() - 1; i >= 0; i--) {
                AstNode nodeCopy = copyNode(injectNodes.get(i));
                nodeCopy.setPosition(-1);
                nodeCopy.setLength(0);
                block.addChildToFront(nodeCopy);
            }
            System.out.println("成功在函数 " + getFunctionName(targetFunction) + " 开头插入占位符");
        }
    }

    public static void injectAtFunctionTail(FunctionNode targetFunction, String placeholder) {
        List<AstNode> injectNodes = parsePlaceholderNodes(placeholder);
        if (!injectNodes.isEmpty() && targetFunction.getBody() instanceof Block block) {
            for (AstNode node : injectNodes) {
                AstNode nodeCopy = copyNode(node);
                nodeCopy.setPosition(-1);
                nodeCopy.setLength(0);
                block.addChildToBack(nodeCopy);
            }
            System.out.println("成功在函数 " + getFunctionName(targetFunction) + " 末尾插入占位符");
        }
    }

    @Deprecated
    public static void injectAtFunctionHead(AstRoot root, String functionName, String placeholder) {
        FunctionNode targetFunction = findFunctionByName(root, functionName);
        if (targetFunction != null) injectAtFunctionHead(targetFunction, placeholder);
    }

    @Deprecated
    public static void injectAtFunctionTail(AstRoot root, String functionName, String placeholder) {
        FunctionNode targetFunction = findFunctionByName(root, functionName);
        if (targetFunction != null) injectAtFunctionTail(targetFunction, placeholder);
    }

    private static AstNode copyNode(AstNode node) {
        if (node instanceof ExpressionStatement) {
            ExpressionStatement original = (ExpressionStatement) node;
            ExpressionStatement copy = new ExpressionStatement();
            copy.setExpression(original.getExpression());
            copy.setLength(original.getLength());
            copy.setPosition(original.getPosition());
            return copy;
        } else if (node instanceof VariableDeclaration) {
            VariableDeclaration original = (VariableDeclaration) node;
            VariableDeclaration copy = new VariableDeclaration();
            copy.setVariables(original.getVariables());
            copy.setLength(original.getLength());
            copy.setPosition(original.getPosition());
            return copy;
        }
        return node;
    }

    private static String getFunctionName(FunctionNode func) {
        Name name = func.getFunctionName();
        return name != null ? name.getIdentifier() : "<anonymous>";
    }

    /**
     * 解析占位符代码为AST节点，返回占位符文本
     */
    static String registerPlaceholder(MixinDSL dsl) {
        if (placeholderMap.containsKey(dsl)) {
            return placeholderMap.get(dsl);
        }
        int id = placeholderCounter++;
        String placeholder = PLACEHOLDER_PREFIX + id + PLACEHOLDER_SUFFIX;
        placeholderMap.put(dsl, placeholder);
        return placeholder;
    }

    public static Map<MixinDSL, String> getPlaceholderMap() {
        return placeholderMap;
    }

    private static List<AstNode> parsePlaceholderNodes(String placeholder) {
        return parseCodeToNodes(placeholder);
    }

    private static FunctionNode findFunctionByName(AstNode node, String functionName) {
        if (node instanceof FunctionNode) {
            FunctionNode function = (FunctionNode) node;
            Name functionNameNode = function.getFunctionName();
            if (functionNameNode != null && functionName.equals(functionNameNode.getIdentifier())) return function;
        }
        for (Node child : node) {
            if (child instanceof AstNode) {
                FunctionNode result = findFunctionByName((AstNode) child, functionName);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static List<AstNode> parseCodeToNodes(String code) {
        List<AstNode> nodes = new ArrayList<>();
        if (code == null || code.trim().isEmpty()) {
            System.out.println("注入代码为空");
            return nodes;
        }
        if (code.trim().startsWith("{")) {
            try {
                Context context = Context.enter();
                Parser parser = new Parser(context);
                AstRoot root = parser.parse(code, "<inject>", 1);
                for (Node node : root) {
                    if (node instanceof AstNode) nodes.add((AstNode) node);
                }
                System.out.println("成功解析函数体代码，提取出 " + nodes.size() + " 个节点");
                return nodes;
            } catch (Exception e) {
                System.err.println("解析函数体代码失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if ("const InjectCode = KubeLoader".equals(code.trim())) {
            try {
                Context context = Context.enter();
                Parser parser = new Parser(context);
                AstRoot root = parser.parse("const InjectCode = KubeLoader;", "<inject>", 1);
                for (Node node : root) {
                    if (node instanceof AstNode) nodes.add((AstNode) node);
                }
                System.out.println("成功解析占位符代码，提取出 " + nodes.size() + " 个节点");
                return nodes;
            } catch (Exception e) {
                System.err.println("解析占位符代码失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
        Context context = null;
        try {
            context = Context.enter();
            Parser parser = new Parser(context);
            AstRoot root = parser.parse(code, "<inject>", 1);
            for (Node node : root) {
                if (node instanceof AstNode) nodes.add((AstNode) node);
            }
            System.out.println("成功解析代码，提取出 " + nodes.size() + " 个节点");
        } catch (Exception e) {
            System.err.println("直接解析代码失败: " + e.getMessage());
            try {
                if (context == null) context = Context.enter();
                Parser parser = new Parser(context);
                String wrappedCode = "(function() { " + code + " })";
                AstRoot root = parser.parse(wrappedCode, "<inject>", 1);
                FunctionNode wrapperFunction = findFirstFunction(root);
                if (wrapperFunction != null) {
                    AstNode body = wrapperFunction.getBody();
                    if (body instanceof Block) {
                        for (Node stmtNode : body) {
                            if (stmtNode instanceof AstNode) nodes.add((AstNode) stmtNode);
                        }
                    }
                }
                System.out.println("成功使用包装函数解析代码，提取出 " + nodes.size() + " 个节点");
            } catch (Exception ex) {
                System.err.println("包装函数解析也失败: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        return nodes;
    }

    private static FunctionNode findFirstFunction(AstNode root) {
        if (root instanceof FunctionNode) return (FunctionNode) root;
        for (Node child : root) {
            if (child instanceof AstNode) {
                if (child instanceof FunctionNode) return (FunctionNode) child;
                FunctionNode result = findFirstFunction((AstNode) child);
                if (result != null) return result;
            }
        }
        return null;
    }

    public static String toSource(AstRoot root) {
        StringBuilder code = new StringBuilder();
        try {
            for (Node node : root) {
                if (node instanceof AstNode) {
                    AstNode astNode = (AstNode) node;
                    try {
                        String nodeSource = astNode.getString();
                        code.append(nodeSource);
                        if (!nodeSource.trim().endsWith(";") && !nodeSource.trim().endsWith("}") && !(astNode instanceof FunctionNode) && !(astNode instanceof EmptyExpression)) {
                            code.append(";");
                        }
                        code.append("\n");
                    } catch (Exception e) {
                        code.append("/* 无法转换节点: ").append(astNode.getClass().getSimpleName()).append(" */\n");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("转换源代码时出错: " + e.getMessage());
            e.printStackTrace();
            return root.getString();
        }
        return code.toString();
    }
}
