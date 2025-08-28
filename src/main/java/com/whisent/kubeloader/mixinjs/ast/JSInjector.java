package com.whisent.kubeloader.mixinjs.ast;

import com.whisent.kubeloader.mixinjs.dsl.MixinDSL;
import dev.latvian.mods.rhino.Node;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ast.*;

import java.util.ArrayList;
import java.util.List;

public class JSInjector {

    /**
     * 根据MixinDSL规则注入代码到AST中
     * @param root AST根节点
     * @param dsl MixinDSL规则
     */
    public static void injectFromDSL(AstRoot root, MixinDSL dsl) {
        // 根据DSL定义的位置进行注入
        String position = dsl.getAt();
        if (position == null) {
            System.out.println("注入位置未定义");
            return;
        }

        switch (position) {
            case "head":
                injectAtFunctionHead(root, dsl.getTarget(), "const InjectCode = KubeLoader");
                break;
            case "tail":
                injectAtFunctionTail(root, dsl.getTarget(), "const InjectCode = KubeLoader");
                break;
            default:
                System.out.println("未知的注入位置: " + position);
        }
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
                        // 标记为新注入的节点（负位置）
                        injectNodes.get(i).setBounds(-1, -1);
                        block.addChildToFront(injectNodes.get(i));
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
                        // 标记为新注入的节点（负位置）
                        node.setBounds(-1, -1);
                        ((Block) functionBody).addChildToBack(node);
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
            // 进入Rhino上下文
            Context cx = Context.enter();
            try {
                // 遍历所有节点生成源代码
                for (Node node : root) {
                    if (node instanceof AstNode) {
                        String nodeSource = ((AstNode) node).getString();
                        code.append(nodeSource);
                        // 如果节点不是以分号结尾，添加分号
                        if (!nodeSource.trim().endsWith(";")) {
                            code.append(";");
                        }
                        code.append("\n");
                    }
                }
            } finally {
                // 不调用Context.exit()，因为在Rhino中可能不存在此方法
            }
        } catch (Exception e) {
            System.err.println("转换源代码时出错: " + e.getMessage());
            e.printStackTrace();
        }
        return code.toString();
    }

    /**
     * 执行AST注入并返回修改后的源代码
     * @param root AST根节点
     * @param dsl MixinDSL规则
     * @return 注入后的源代码
     */
    public static String injectAndGetSource(AstRoot root, MixinDSL dsl) {
        // 执行注入
        injectFromDSL(root, dsl);
        // 返回转换后的源代码
        return toSource(root);
    }
}
