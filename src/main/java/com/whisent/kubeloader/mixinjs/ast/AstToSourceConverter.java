package com.whisent.kubeloader.mixinjs.ast;

import dev.latvian.mods.rhino.Node;
import dev.latvian.mods.rhino.Token;
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

    public String convert(AstRoot root) {
        lastPosition = 0; // 重置位置
        sb.setLength(0); // 清空StringBuilder
        
        // 先检查是否有原始源码
        if (source == null || source.isEmpty()) {
            // 如果没有原始源码，直接生成新的代码
            generateNodeSource(root);
            return sb.toString();
        }

        traverse(root);
        // 添加文件末尾可能存在的空白
        if (lastPosition >= 0 && lastPosition < source.length()) {
            sb.append(source.substring(lastPosition));
        }
        return sb.toString();
    }

    private void traverse(AstNode node) {
        if (node == null) return;

        int start = node.getAbsolutePosition();
        int end = start + node.getLength();
        
        // 检查是否是新注入的节点
        if (start < 0 || end < 0) {
            generateNodeSource(node);
            return;
        }

        // 确保索引有效
        if (start > source.length() || end > source.length()) {
            return;
        }

        // 处理节点前的空白
        if (start > lastPosition && lastPosition >= 0) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(start, source.length());
            if (actualStart < actualEnd) {
                sb.append(source.substring(actualStart, actualEnd));
            }
        }
        lastPosition = Math.max(0, start);

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
        } else {
            // 默认处理：递归处理子节点
            AstNode child = (AstNode) node.getFirstChild();
            while (child != null) {
                traverse(child);
                child = (AstNode) child.getNext();
            }

            // 处理节点末尾空白
            if (lastPosition >= 0 && lastPosition < end && end <= source.length()) {
                sb.append(source.substring(lastPosition, end));
                lastPosition = end;
            }
        }
    }

    // 改进generateNodeSource方法
    private void generateNodeSource(AstNode node) {
        if (node instanceof ExpressionStatement) {
            ExpressionStatement exprStmt = (ExpressionStatement) node;
            generateNodeSource(exprStmt.getExpression());
            // 确保语句以分号结尾
            if (!sb.toString().endsWith(";")) {
                sb.append(";");
            }
            sb.append("\n"); // 添加换行以保持格式
        } else if (node instanceof FunctionCall) {
            FunctionCall call = (FunctionCall) node;
            generateNodeSource(call.getTarget());
            sb.append("(");
            List<AstNode> arguments = call.getArguments();
            if (arguments != null) {
                for (int i = 0; i < arguments.size(); i++) {
                    if (i > 0) sb.append(", ");
                    generateNodeSource(arguments.get(i));
                }
            }
            sb.append(")");
        } else if (node instanceof PropertyGet) {
            PropertyGet prop = (PropertyGet) node;
            generateNodeSource(prop.getLeft());
            sb.append(".");
            generateNodeSource(prop.getRight());
        } else if (node instanceof Name) {
            Name name = (Name) node;
            sb.append(name.getIdentifier());
        } else if (node instanceof StringLiteral) {
            StringLiteral strLit = (StringLiteral) node;
            sb.append("\"").append(escapeString(strLit.getValue())).append("\"");
        } else if (node instanceof NumberLiteral) {
            NumberLiteral numLit = (NumberLiteral) node;
            sb.append(numLit.getValue());
        } else if (node instanceof Block) {
            Block block = (Block) node;
            sb.append(" {\n");
            for (Node child : block) {
                if (child instanceof AstNode) {
                    sb.append("    "); // 添加缩进
                    generateNodeSource((AstNode) child);
                }
            }
            sb.append("}\n");
        } else if (node instanceof VariableDeclaration) {
            VariableDeclaration varDecl = (VariableDeclaration) node;
            switch (varDecl.getType()) {
                case Token.VAR:
                    sb.append("var ");
                    break;
                case Token.LET:
                    sb.append("let ");
                    break;
                case Token.CONST:
                    sb.append("const ");
                    break;
                default:
                    sb.append("var ");
                    break;
            }
            
            boolean first = true;
            for (VariableInitializer init : varDecl.getVariables()) {
                if (!first) sb.append(", ");
                first = false;
                generateNodeSource(init);
            }
            sb.append(";\n"); // 添加分号和换行
        } else if (node instanceof VariableInitializer) {
            VariableInitializer init = (VariableInitializer) node;
            generateNodeSource(init.getTarget());
            if (init.getInitializer() != null) {
                sb.append(" = ");
                generateNodeSource(init.getInitializer());
            }
        } else if (node instanceof AstRoot) {
            // 处理根节点的所有子节点
            for (Node child : node) {
                if (child instanceof AstNode) {
                    generateNodeSource((AstNode) child);
                }
            }
        }
    }

    // 添加辅助方法：转义字符串
    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
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
        
        // 确保索引有效
        if (start < 0 || end < 0 || start > source.length() || end > source.length()) {
            return;
        }

        // 添加左花括号
        if (start > lastPosition && lastPosition >= 0) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(start, source.length());
            if (actualStart < actualEnd) {
                sb.append(source.substring(actualStart, actualEnd));
            }
        }
        sb.append('{');
        lastPosition = Math.max(0, start + 1); // 跳过'{'

        // 处理作用域内的子节点
        AstNode child = (AstNode) scope.getFirstChild();
        while (child != null) {
            traverse(child);
            child = (AstNode) child.getNext();
        }

        // 添加右花括号
        if (lastPosition >= 0 && lastPosition < end) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(end, source.length());
            String remaining = source.substring(actualStart, actualEnd);
            // 确保有右花括号
            if (remaining.contains("}")) {
                sb.append(remaining);
                lastPosition = actualEnd;
            } else {
                sb.append('}');
                lastPosition = actualEnd;
            }
        }
    }

    // 处理代码块节点
    private void handleBlock(Block block) {
        int start = block.getAbsolutePosition();
        int end = start + block.getLength();
        
        // 确保索引有效
        if (start < 0 || end < 0 || start > source.length() || end > source.length()) {
            // 处理新添加的块节点
            sb.append(" {");
            // 处理块内的子节点
            AstNode child = (AstNode) block.getFirstChild();
            while (child != null) {
                traverse(child);
                child = (AstNode) child.getNext();
            }
            sb.append("} ");
            return;
        }

        // 添加左花括号
        if (start > lastPosition && lastPosition >= 0) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(start, source.length());
            if (actualStart < actualEnd) {
                sb.append(source.substring(actualStart, actualEnd));
            }
        }
        sb.append('{');
        lastPosition = Math.max(0, start + 1); // 跳过'{'

        // 处理块内的子节点
        AstNode child = (AstNode) block.getFirstChild();
        while (child != null) {
            traverse(child);
            child = (AstNode) child.getNext();
        }

        // 添加右花括号
        if (lastPosition >= 0 && lastPosition < end) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(end, source.length());
            String remaining = source.substring(actualStart, actualEnd);
            if (remaining.contains("}")) {
                sb.append(remaining);
                lastPosition = actualEnd;
            } else {
                sb.append('}');
                lastPosition = actualEnd;
            }
        }
    }
    
    private void handleFunctionNode(FunctionNode func) {
        int start = func.getAbsolutePosition();
        int end = start + func.getLength();
        
        // 确保索引有效
        if (start < 0 || end < 0 || start > source.length() || end > source.length()) {
            // 处理新添加的函数节点
            Name functionName = func.getFunctionName();
            if (functionName != null) {
                sb.append("function ").append(functionName.getIdentifier()).append("() ");
            } else {
                sb.append("function () ");
            }
            
            // 处理函数体
            AstNode body = func.getBody();
            if (body != null) {
                traverse(body);
            }
            return;
        }

        // 处理函数前的空白
        if (start > lastPosition && lastPosition >= 0) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(start, source.length());
            if (actualStart < actualEnd) {
                sb.append(source.substring(actualStart, actualEnd));
            }
        }

        // 提取函数签名（function关键字、函数名、参数列表）
        int bodyStart = findFunctionBodyStart(func);
        if (bodyStart > start && bodyStart <= source.length()) {
            sb.append(source.substring(start, bodyStart));
            lastPosition = bodyStart;
        } else {
            // 生成默认函数签名
            Name functionName = func.getFunctionName();
            if (functionName != null) {
                sb.append("function ").append(functionName.getIdentifier()).append("()");
            } else {
                sb.append("function ()");
            }
            lastPosition = start;
        }

        // 处理函数体，但不递归处理函数定义本身
        AstNode body = func.getBody();
        if (body != null && body instanceof Block) {
            handleBlockContentOnly((Block) body);
        }

        // 处理函数末尾内容
        if (lastPosition >= 0 && lastPosition < end) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(end, source.length());
            if (actualStart < actualEnd) {
                sb.append(source.substring(actualStart, actualEnd));
                lastPosition = actualEnd;
            }
        }
    }

    /**
     * 处理块内容，避免重复处理函数定义
     */
    private void handleBlockContentOnly(Block block) {
        int start = block.getAbsolutePosition();
        int end = start + block.getLength();
        
        // 确保索引有效
        if (start < 0 || end < 0 || start > source.length() || end > source.length()) {
            // 处理新添加的块节点
            sb.append(" {");
            // 处理块内的子节点
            AstNode child = (AstNode) block.getFirstChild();
            while (child != null) {
                // 避免处理FunctionNode节点
                if (!(child instanceof FunctionNode)) {
                    traverse(child);
                }
                child = (AstNode) child.getNext();
            }
            sb.append("} ");
            return;
        }

        // 添加左花括号
        if (start > lastPosition && lastPosition >= 0) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(start, source.length());
            if (actualStart < actualEnd) {
                sb.append(source.substring(actualStart, actualEnd));
            }
        }
        sb.append('{');
        lastPosition = Math.max(0, start + 1); // 跳过'{'

        // 处理块内的子节点，但避免处理FunctionNode节点以防止重复输出
        AstNode child = (AstNode) block.getFirstChild();
        while (child != null) {
            // 跳过FunctionNode节点的处理
            if (!(child instanceof FunctionNode)) {
                traverse(child);
            }
            child = (AstNode) child.getNext();
        }

        // 添加右花括号
        if (lastPosition >= 0 && lastPosition < end) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(end, source.length());
            String remaining = source.substring(actualStart, actualEnd);
            if (remaining.contains("}")) {
                sb.append(remaining);
                lastPosition = actualEnd;
            } else {
                sb.append('}');
                lastPosition = actualEnd;
            }
        }
    }

    /**
     * 查找函数体的开始位置
     */
    private int findFunctionBodyStart(FunctionNode func) {
        // 在源码中查找函数体开始的 '{'
        int pos = func.getAbsolutePosition();
        if (pos < 0 || pos >= source.length()) {
            return func.getAbsolutePosition();
        }
        
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
        
        // 确保索引有效
        if (declStart < 0 || declEnd < 0 || declStart > source.length() || declEnd > source.length()) {
            // 处理新添加的变量声明
            // 添加变量声明关键字
            switch (decl.getType()) {
                case Token.VAR:
                    sb.append("var ");
                    break;
                case Token.LET:
                    sb.append("let ");
                    break;
                case Token.CONST:
                    sb.append("const ");
                    break;
                default:
                    sb.append("var ");
                    break;
            }
            
            // 处理变量初始化器
            boolean first = true;
            for (VariableInitializer varInit : decl.getVariables()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                generateNodeSource(varInit);
            }
            return;
        }

        // 提取声明关键字（let/var/const）
        int keywordEnd = declStart;
        AstNode firstChild = (AstNode) decl.getFirstChild();
        if (firstChild != null) {
            keywordEnd = firstChild.getAbsolutePosition();
        }

        // 添加声明关键字
        if (declStart < keywordEnd && keywordEnd <= source.length()) {
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
        if (lastPosition >= 0 && lastPosition < declEnd) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(declEnd, source.length());
            if (actualStart < actualEnd) {
                sb.append(source.substring(actualStart, actualEnd));
                lastPosition = actualEnd;
            }
        }
    }

    // 处理表达式语句
    private void handleExpressionStatement(ExpressionStatement stmt) {
        int stmtStart = stmt.getAbsolutePosition();
        int stmtEnd = stmtStart + stmt.getLength();
        
        // 检查索引有效性
        if (stmtStart < 0 || stmtEnd < 0 || stmtStart > source.length() || stmtEnd > source.length()) {
            // 处理新添加的表达式语句
            generateNodeSource(stmt.getExpression());
            sb.append(";");
            return;
        }
        
        // 处理表达式
        traverse(stmt.getExpression());

        // 检查是否需要添加分号
        if (stmtEnd > 0 && stmtEnd <= source.length() && lastPosition >= 0 && lastPosition < stmtEnd) {
            int actualStart = Math.max(0, lastPosition);
            int actualEnd = Math.min(stmtEnd, source.length());
            String trailing = source.substring(actualStart, actualEnd);
            if (!trailing.trim().endsWith(";")) {
                sb.append(';');
                lastPosition = actualEnd;
            } else {
                sb.append(trailing);
                lastPosition = actualEnd;
            }
        }
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
            Name functionName = func.getFunctionName();
            if (functionName != null) {
                System.out.print(", name=" + functionName.getIdentifier());
            }
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
            Name functionName = func.getFunctionName();
            if (functionName != null && targetName.equals(functionName.getIdentifier())) {
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

    public String convertAndFix(AstRoot root,String injectCode) {
        String result = convert(root);
        result = result.replace("const InjectCode = KubeLoader", injectCode);
        return result;
    }
}

