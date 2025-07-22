package com.whisent.kubeloader.ast;

import dev.latvian.mods.rhino.ast.*;

import java.util.ArrayList;
import java.util.List;


public class AstToSourceConverter {
    private final StringBuilder sb = new StringBuilder();
    private int lastPosition = 0;
    private final String source;

    public AstToSourceConverter(String source) {
        this.source = source;
    }

    public String convert(AstNode root) {
        traverse(root);
        // 添加文件末尾可能存在的空白
        if (lastPosition < source.length()) {
            sb.append(source.substring(lastPosition));
        }
        return sb.toString();
    }

    private void traverse(AstNode node) {
        if (node == null) return;

        int start = node.getAbsolutePosition();
        int end = start + node.getLength();

        // 处理节点前的空白
        if (start > lastPosition) {
            sb.append(source.substring(lastPosition, start));
        }
        lastPosition = start;

        // 特殊节点处理
        if (node instanceof AstRoot) {
            handleAstRoot((AstRoot) node);
        } else if (node instanceof FunctionNode) {
            handleFunctionNode((FunctionNode) node);
        } else if (node instanceof Block) {
            handleBlock((Block) node);
        } else if (node instanceof VariableDeclaration) {
            handleVariableDeclaration((VariableDeclaration) node);
        } else if (node instanceof ExpressionStatement) {
            handleExpressionStatement((ExpressionStatement) node);
        }  else {
            // 默认处理：递归处理子节点
            AstNode child = (AstNode) node.getFirstChild();
            while (child != null) {
                traverse(child);
                child = (AstNode) child.getNext();
            }
            // 处理节点末尾空白
            if (lastPosition < end) {
                sb.append(source.substring(lastPosition, end));
                lastPosition = end;
            }
        }
    }

    // 处理根节点
    private void handleAstRoot(AstRoot root) {
        // 直接处理所有子节点
        AstNode child = (AstNode) root.getFirstChild();
        while (child != null) {
            traverse(child);
            child = (AstNode) child.getNext();
        }
    }

    // 处理作用域节点
    private void handleScope(Scope scope) {
        int start = scope.getAbsolutePosition();
        int end = start + scope.getLength();

        // 添加左花括号
        if (start > lastPosition) {
            sb.append(source.substring(lastPosition, start));
        }
        sb.append('{');
        lastPosition = start + 1; // 跳过'{'

        // 处理作用域内的子节点
        AstNode child = (AstNode) scope.getFirstChild();
        while (child != null) {
            traverse(child);
            child = (AstNode) child.getNext();
        }

        // 添加右花括号
        if (lastPosition < end) {
            String remaining = source.substring(lastPosition, end);
            // 确保有右花括号
            if (remaining.contains("}")) {
                sb.append(remaining);
                lastPosition = end;
            } else {
                sb.append('}');
                lastPosition = end;
            }
        }
    }

    // 处理代码块节点
    private void handleBlock(Block block) {
        int start = block.getAbsolutePosition();
        int end = start + block.getLength();

        // 添加左花括号
        if (start > lastPosition) {
            sb.append(source.substring(lastPosition, start));
        }
        sb.append('{');
        lastPosition = start + 1; // 跳过'{'

        // 处理块内的子节点
        AstNode child = (AstNode) block.getFirstChild();
        while (child != null) {
            traverse(child);
            child = (AstNode) child.getNext();
        }

        // 添加右花括号
        if (lastPosition < end) {
            String remaining = source.substring(lastPosition, end);
            if (remaining.contains("}")) {
                sb.append(remaining);
                lastPosition = end;
            } else {
                sb.append('}');
                lastPosition = end;
            }
        }
    }
    private void handleFunctionNode(FunctionNode func) {
        int start = func.getAbsolutePosition();
        int end = start + func.getLength();

        // 处理函数前的空白
        if (start > lastPosition) {
            sb.append(source.substring(lastPosition, start));
        }

        // 提取函数签名（function关键字、函数名、参数列表）
        int bodyStart = findFunctionBodyStart(func);
        if (bodyStart > start) {
            sb.append(source.substring(start, bodyStart));
            lastPosition = bodyStart;
        } else {
            // 生成默认函数签名
            sb.append("function ").append(func.getFunctionName()).append("()");
        }

        // 递归处理函数体
        AstNode body = findFunctionBodyNode(func);
        if (body != null) {
            traverse(body);
        }

        // 处理函数末尾内容
        if (lastPosition < end) {
            sb.append(source.substring(lastPosition, end));
            lastPosition = end;
        }
        System.out.println("截获Mixin函数：");
    }

    /**
     * 查找函数体的开始位置
     */
    private int findFunctionBodyStart(FunctionNode func) {
        // 在源码中查找函数体开始的 '{'
        int pos = func.getAbsolutePosition();
        while (pos < source.length() && source.charAt(pos) != '{') {
            pos++;
        }
        return (pos < source.length()) ? pos : func.getAbsolutePosition();
    }

    /**
     * 查找函数体节点
     */
    private AstNode findFunctionBodyNode(FunctionNode func) {
        // 遍历子节点查找函数体
        AstNode child = (AstNode) func.getBody();
        while (child != null) {
            if (child instanceof Block) {
                return child;
            }
            child = (AstNode) child.getNext();
        }
        return null;
    }
    // 处理变量声明
    private void handleVariableDeclaration(VariableDeclaration decl) {
        int declStart = decl.getAbsolutePosition();
        int declEnd = declStart + decl.getLength();

        // 提取声明关键字（let/var/const）
        int keywordEnd = declStart;
        AstNode firstChild = (AstNode) decl.getFirstChild();
        if (firstChild != null) {
            keywordEnd = firstChild.getAbsolutePosition();
        }

        // 添加声明关键字
        if (declStart < keywordEnd) {
            sb.append(source.substring(declStart, keywordEnd));
            lastPosition = keywordEnd;
        }

        // 处理变量初始化器
        boolean first = true;
        AstNode child = (AstNode) decl.getFirstChild();
        while (child != null) {
            if (child instanceof VariableInitializer) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                traverse(child);
            }
            child = (AstNode) child.getNext();
        }

        // 添加节点末尾内容
        if (lastPosition < declEnd) {
            sb.append(source.substring(lastPosition, declEnd));
            lastPosition = declEnd;
        }
    }

    // 处理表达式语句
    private void handleExpressionStatement(ExpressionStatement stmt) {
        // 处理表达式
        traverse(stmt.getExpression());

        // 检查是否需要添加分号
        int stmtEnd = stmt.getAbsolutePosition() + stmt.getLength();
        if (lastPosition < stmtEnd) {
            String trailing = source.substring(lastPosition, stmtEnd);
            if (!trailing.trim().endsWith(";")) {
                sb.append(';');
                lastPosition = stmtEnd;
            } else {
                sb.append(trailing);
                lastPosition = stmtEnd;
            }
        }
        System.out.println("截获Mixin函数：");
        System.out.println(stmt.getExpression());
    }

    public void printAst(AstNode root) {
        printAstRecursive(root, 0);
    }

    /**
     * 递归打印AST节点
     * @param node 当前节点
     * @param depth 当前深度（用于缩进）
     */
    private void printAstRecursive(AstNode node, int depth) {
        if (node == null) return;

        // 创建缩进字符串
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");  // 每层缩进2个空格
        }

        // 打印节点基本信息
        System.out.print(indent + node.getClass().getSimpleName());
        System.out.print(" [pos=" + node.getAbsolutePosition());
        System.out.print(", len=" + node.getLength());

        // 特殊节点附加信息
        if (node instanceof FunctionNode) {
            FunctionNode func = (FunctionNode) node;
            System.out.print(", name=" + func.getFunctionName());
            System.out.print(", args=" + func.getParams());

        } else if (node instanceof VariableDeclaration) {
            VariableDeclaration var = (VariableDeclaration) node;
            System.out.print(", vars=" + var.getVariables());
        }

        System.out.println("]");

        // 递归打印子节点
        AstNode child = (AstNode) node.getFirstChild();
        while (child != null) {
            printAstRecursive(child, depth + 1);
            child = (AstNode) child.getNext();
        }
    }

    public List<AstNode> findFunctionNodesByName(AstNode root, String functionName) {
        List<AstNode> result = new ArrayList<>();
        findFunctionsRecursive(root, functionName, result);
        return result;
    }

    private void findFunctionsRecursive(AstNode node, String targetName, List<AstNode> result) {
        if (node == null) return;

        // 检查是否是目标函数节点
        if (node instanceof FunctionNode) {
            FunctionNode func = (FunctionNode) node;
            if (targetName.equals(func.getFunctionName())) {
                result.add(node);
            }
        }

        // 递归搜索子节点
        AstNode child = (AstNode) node.getFirstChild();
        while (child != null) {
            findFunctionsRecursive(child, targetName, result);
            child = (AstNode) child.getNext();
        }
    }
}

