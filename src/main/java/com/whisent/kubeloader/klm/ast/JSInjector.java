package com.whisent.kubeloader.klm.ast;

import com.whisent.kubeloader.klm.dsl.EventProbeTextProcessor;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Node;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JSInjector {
    private String te = "const InjectCode = KubeLoader";
    /**
     * 根据MixinDSL规则注入代码到AST中
     * @param root AST根节点
     * @param dsl MixinDSL规则
     */
    public static void injectFromDSL(AstRoot root, MixinDSL dsl) {
        // 根据DSL定义的类型进行注入
        String type = dsl.getType();
        
        if (Objects.equals(type, "FunctionDeclaration")) {
            injectFunction(root, dsl);
        }

    }
    public static void injectFunction(AstRoot root, MixinDSL dsl) {
        String position = dsl.getAt();
        if (position == null) {
            dsl.getFile().pack.manager.scriptType.console.error("Unknown injection position for DSL: " + dsl);
            return;
        }

        switch (position) {
            case "head":
                injectAtFunctionHead(root, dsl.getTarget(), dsl.getAction());
                break;
            case "tail":
                injectAtFunctionTail(root, dsl.getTarget(), dsl.getAction());
                break;
            default:
                dsl.getFile().pack.manager.scriptType.console.error("Unknown injection position for DSL: " + dsl);
        }
    }

    /**
     * 复制AST节点
     * @param node 原始节点
     * @return 节点副本
     */
    private static AstNode copyNode(AstNode node) {
        // 创建一个新的节点副本，而不是直接返回原始节点
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
        // 对于其他类型的节点，直接返回
        // 在实际应用中，可能需要更复杂的复制逻辑
        return node;
    }

    /**
     * 在指定函数的开头注入代码
     * @param root AST根节点
     * @param functionName 目标函数名
     * @param injectCode 要注入的代码
     */
    public static void injectAtFunctionHead(AstRoot root, String functionName, String injectCode) {
        System.out.println("尝试在函数 " + functionName + " 开头注入代码: " + injectCode);
        
        // 查找目标函数
        FunctionNode targetFunction = findFunctionByName(root, functionName);
        if (targetFunction != null) {
            System.out.println("找到目标函数: " + functionName);
            
            // 创建要注入的语句
            List<AstNode> injectNodes = parseCodeToNodes(injectCode);
            System.out.println("解析出 " + injectNodes.size() + " 个节点");
            
            if (!injectNodes.isEmpty()) {
                // 获取函数体
                AstNode functionBody = targetFunction.getBody();
                System.out.println("函数体类型: " + functionBody.getClass().getSimpleName());
                
                if (functionBody instanceof Block) {
                    Block block = (Block) functionBody;

                    // 在函数体开头插入注入的代码（逆序插入以保持语句顺序）
                    for (int i = injectNodes.size() - 1; i >= 0; i--) {
                        System.out.println("添加节点: " + injectNodes.get(i).getClass().getSimpleName());
                        // 创建节点副本并设置位置信息
                        AstNode nodeCopy = copyNode(injectNodes.get(i));
                        // 设置为新注入的节点（负位置）
                        nodeCopy.setPosition(-1);
                        nodeCopy.setLength(0);
                        block.addChildToFront(nodeCopy);
                    }

                    System.out.println("成功在函数 " + functionName + " 开头注入代码");
                } else {
                    System.out.println("函数体不是Block类型");
                }
            } else {
                System.out.println("没有解析出任何节点");
            }
        } else {
            System.out.println("未找到函数: " + functionName);
        }
    }

    /**
     * 在指定函数的末尾注入代码
     * @param root AST根节点
     * @param functionName 目标函数名
     * @param injectCode 要注入的代码
     */
    public static void injectAtFunctionTail(AstRoot root, String functionName, String injectCode) {
        // 查找目标函数
        FunctionNode targetFunction = findFunctionByName(root, functionName);
        if (targetFunction != null) {
            // 创建要注入的语句
            List<AstNode> injectNodes = parseCodeToNodes(injectCode);
            if (!injectNodes.isEmpty()) {
                // 获取函数体
                AstNode functionBody = targetFunction.getBody();
                if (functionBody instanceof Block) {
                    // 在函数体末尾插入注入的代码
                    for (AstNode node : injectNodes) {
                        // 创建节点副本并设置位置信息
                        AstNode nodeCopy = copyNode(node);
                        // 设置为新注入的节点（负位置）
                        nodeCopy.setPosition(-1);
                        nodeCopy.setLength(0);
                        ((Block) functionBody).addChildToBack(nodeCopy);
                    }

                    System.out.println("成功在函数 " + functionName + " 末尾注入代码: " + injectCode);
                }
            }
        } else {
            System.out.println("未找到函数: " + functionName);
        }
    }

    /**
     * 查找指定名称的函数
     * @param node AST节点
     * @param functionName 函数名
     * @return 找到的函数节点，未找到返回null
     */
    private static FunctionNode findFunctionByName(AstNode node, String functionName) {
        // 检查当前节点是否是函数节点
        if (node instanceof FunctionNode) {
            FunctionNode function = (FunctionNode) node;
            Name functionNameNode = function.getFunctionName();
            if (functionNameNode != null && functionName.equals(functionNameNode.getIdentifier())) {
                return function;
            }
        }

        // 递归查找子节点
        for (Node child : node) {
            if (child instanceof AstNode) {
                FunctionNode result = findFunctionByName((AstNode) child, functionName);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * 将代码字符串解析为AST节点列表
     * @param code 代码字符串
     * @return 解析后的AST节点列表
     */
    private static List<AstNode> parseCodeToNodes(String code) {
        List<AstNode> nodes = new ArrayList<>();
        
        if (code == null || code.trim().isEmpty()) {
            System.out.println("注入代码为空");
            return nodes;
        }
        
        // 如果是EventProbe提取的函数体（以{开头），特殊处理
        if (code.trim().startsWith("{")) {
            try {
                Context context = Context.enter();
                Parser parser = new Parser(context);
                // 直接解析代码块
                AstRoot root = parser.parse(code, "<inject>", 1);
                
                // 提取语句
                for (Node node : root) {
                    if (node instanceof AstNode) {
                        nodes.add((AstNode) node);
                    }
                }
                
                System.out.println("成功解析函数体代码，提取出 " + nodes.size() + " 个节点");
                return nodes;
            } catch (Exception e) {
                System.err.println("解析函数体代码失败: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 不调用Context.exit()，因为在Rhino中可能不存在此方法
            }
        }
        
        // 处理占位符代码的特殊情况
        if ("const InjectCode = KubeLoader".equals(code.trim())) {
            try {
                Context context = Context.enter();
                Parser parser = new Parser(context);
                AstRoot root = parser.parse("const InjectCode = KubeLoader;", "<inject>", 1);
                
                // 提取声明语句
                for (Node node : root) {
                    if (node instanceof AstNode) {
                        nodes.add((AstNode) node);
                    }
                }
                
                System.out.println("成功解析占位符代码，提取出 " + nodes.size() + " 个节点");
                return nodes;
            } catch (Exception e) {
                System.err.println("解析占位符代码失败: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 不调用Context.exit()，因为在Rhino中可能不存在此方法
            }
        }
        
        Context context = null;
        try {
            // 创建Rhino上下文和解析器
            context = Context.enter();
            Parser parser = new Parser(context);
            
            // 直接解析代码
            AstRoot root = parser.parse(code, "<inject>", 1);
            
            // 提取顶层语句
            for (Node node : root) {
                if (node instanceof AstNode) {
                    nodes.add((AstNode) node);
                }
            }
            
            System.out.println("成功解析代码，提取出 " + nodes.size() + " 个节点");
            
        } catch (Exception e) {
            System.err.println("直接解析代码失败: " + e.getMessage());
            
            // 尝试使用包装函数解析
            try {
                if (context == null) {
                    context = Context.enter();
                }
                Parser parser = new Parser(context);
                
                // 将代码包装在函数中以便正确解析
                String wrappedCode = "(function() { " + code + " })";
                AstRoot root = parser.parse(wrappedCode, "<inject>", 1);
                
                // 获取包装函数
                FunctionNode wrapperFunction = findFirstFunction(root);
                if (wrapperFunction != null) {
                    // 获取函数体
                    AstNode body = wrapperFunction.getBody();
                    if (body instanceof Block) {
                        // 提取函数体中的语句
                        for (Node stmtNode : body) {
                            if (stmtNode instanceof AstNode) {
                                nodes.add((AstNode) stmtNode);
                            }
                        }
                    }
                }
                
                System.out.println("成功使用包装函数解析代码，提取出 " + nodes.size() + " 个节点");
                
            } catch (Exception ex) {
                System.err.println("包装函数解析也失败: " + ex.getMessage());
                ex.printStackTrace();
            }
        } finally {
            // 不调用Context.exit()，因为在Rhino中可能不存在此方法
        }
        return nodes;
    }

    /**
     * 查找AST中的第一个函数节点
     */
    private static FunctionNode findFirstFunction(AstNode root) {
        // 如果根节点就是函数节点
        if (root instanceof FunctionNode) {
            return (FunctionNode) root;
        }
        
        // 查找子节点
        for (Node child : root) {
            if (child instanceof AstNode) {
                // 如果是函数节点，返回
                if (child instanceof FunctionNode) {
                    return (FunctionNode) child;
                }
                
                // 递归查找
                FunctionNode result = findFirstFunction((AstNode) child);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }

    /**
     * 将AST转换回源代码
     * @param root AST根节点
     * @return 转换后的源代码
     */
    public static String toSource(AstRoot root) {
        StringBuilder code = new StringBuilder();
        try {
            // 遍历所有节点生成源代码
            for (Node node : root) {
                if (node instanceof AstNode) {
                    AstNode astNode = (AstNode) node;
                    // 使用Rhino的getString方法获取节点源代码
                    try {
                        String nodeSource = astNode.getString();
                        code.append(nodeSource);
                        
                        // 如果节点不是以分号或大括号结尾，且不是函数声明等特殊情况，添加分号
                        if (!nodeSource.trim().endsWith(";") && 
                            !nodeSource.trim().endsWith("}") && 
                            !(astNode instanceof FunctionNode) &&
                            !(astNode instanceof EmptyExpression)) {
                            code.append(";");
                        }
                        code.append("\n");
                    } catch (Exception e) {
                        // 如果getString方法失败，添加注释表示这里有节点
                        code.append("/* 无法转换节点: ").append(astNode.getClass().getSimpleName()).append(" */\n");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("转换源代码时出错: " + e.getMessage());
            e.printStackTrace();
            // 返回原始源代码作为后备
            return root.getString();
        }
        return code.toString();
    }

}