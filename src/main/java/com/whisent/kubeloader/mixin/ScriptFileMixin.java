package com.whisent.kubeloader.mixin;

import com.whisent.kubeloader.ast.AstToSourceConverter;
import com.whisent.kubeloader.impl.mixin_interface.ScriptFileInfoInterface;
import dev.latvian.mods.kubejs.script.ScriptFile;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptSource;
import dev.latvian.mods.rhino.Node;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Mixin(value = ScriptFile.class, remap = false)
public class ScriptFileMixin {
    @Shadow @Final public ScriptFileInfo info;


    @Shadow @Final public ScriptPack pack;

    @Shadow @Final public ScriptSource source;

    @Inject(method = "load", at = @At("HEAD"))
    public void kubeLoader$load(CallbackInfo ci) {
        System.out.println("正在加载文件：");
        String mixinPath = ((ScriptFileInfoInterface) this.info).getMixinProperty();
        System.out.println(((ScriptFileInfoInterface) this.info).getMixinProperty());
        if (!Objects.equals(mixinPath, "")) {
            String source = String.join("\n", this.info.lines);
            String stringFunc = "function a() {\n" +
                    "  print('Hello, World!');\n" +
                    "}\n" +
                    "test();";
            AstToSourceConverter converter = new AstToSourceConverter(source);
            Parser parser = new Parser(this.pack.manager.context);
            Parser parser1 = new Parser(this.pack.manager.context);
            AstRoot root = parser.parse(source, this.info.file, 0);

            AstRoot injectRoot = parser1.parse(stringFunc, "inject.js", 0);

            String reconstructed = converter.convert(root);
            converter.printAst(root);
            System.out.println("转换后的源代码：");
            System.out.println(reconstructed);
            System.out.println("提取代码");
            converter.findFunctionNodesByName(root, "onTest");
        }


    }

    private void printAstTree(AstNode node, int indentLevel) {
        if (node == null) {
            System.out.println("  ".repeat(indentLevel) + "├─ [null]");
            return;
        }

        // 计算绝对位置
        int absPos = node.getAbsolutePosition();
        StringBuilder indent = new StringBuilder("  ".repeat(indentLevel));

        // 打印节点信息
        String nodeInfo = indent + "├─ " + node.shortName() +
                " [pos:" + absPos +
                ", len:" + node.getLength() + "]";

        // 添加类型特定信息
        if (node instanceof Name) {
            nodeInfo += " (" + ((Name) node).getIdentifier() + ")";
        } else if (node instanceof VariableInitializer) {
            VariableInitializer vi = (VariableInitializer) node;
            if (vi.getTarget() instanceof Name) {
                nodeInfo += " (var: " + ((Name) vi.getTarget()).getIdentifier() + ")";
            }
            if (vi.getInitializer() != null) {
                nodeInfo += " = " + vi.getInitializer().shortName();
            }
        } else if (node instanceof ExpressionStatement) {
            nodeInfo += " (expr: " + ((ExpressionStatement) node).getExpression().shortName() + ")";
        }

        System.out.println(nodeInfo);

        // 使用唯一ID防止重复打印
        Set<AstNode> visited = new HashSet<>();

        // 递归处理子节点
        for (Node child : node) {
            if (child instanceof AstNode) {
                AstNode astChild = (AstNode) child;

                // 检查是否已访问
                if (!visited.add(astChild)) {
                    System.out.println(indent + "  └─ [已跳过重复节点]");
                    continue;
                }

                printAstTree(astChild, indentLevel + 1);
            }
        }

        // 特殊处理VariableDeclaration的子节点
        if (node instanceof VariableDeclaration) {
            for (VariableInitializer vi : ((VariableDeclaration) node).getVariables()) {
                if (!visited.contains(vi)) {
                    printAstTree(vi, indentLevel + 1);
                }
            }
        }
    }

    // 将 AST 转换为字符串
    private String convertAstToString(AstNode node) {
        StringBuilder sb = new StringBuilder();
        convertNode(node, sb, 0);
        return sb.toString();
    }

    // 递归转换节点
    private void convertNode(AstNode node, StringBuilder sb, int indentLevel) {
        if (node == null) return;

        // 添加缩进
        String indent = "  ".repeat(indentLevel);

        // 处理不同类型的节点
        if (node instanceof AstRoot) {
            for (AstNode child : ((AstRoot) node).getStatements()) {
                convertNode(child, sb, indentLevel);
                sb.append("\n");
            }
        }
        else if (node instanceof ExpressionStatement) {
            sb.append(indent);
            convertNode(((ExpressionStatement) node).getExpression(), sb, 0);
            sb.append(";");
        }
        else if (node instanceof VariableDeclaration) {
            VariableDeclaration varDecl = (VariableDeclaration) node;
            sb.append(indent).append(varDecl.isStatement() ? "var " : "");

            for (int i = 0; i < varDecl.getVariables().size(); i++) {
                if (i > 0) sb.append(", ");
                convertNode(varDecl.getVariables().get(i), sb, 0);
            }

            if (varDecl.isStatement()) sb.append(";");
        }
        else if (node instanceof VariableInitializer) {
            VariableInitializer vi = (VariableInitializer) node;
            convertNode(vi.getTarget(), sb, 0);
            if (vi.getInitializer() != null) {
                sb.append(" = ");
                convertNode(vi.getInitializer(), sb, 0);
            }
        }
        else if (node instanceof Name) {
            sb.append(((Name) node).getIdentifier());
        }
        else if (node instanceof FunctionCall) {
            FunctionCall call = (FunctionCall) node;
            convertNode(call.getTarget(), sb, 0);
            sb.append("(");

            for (int i = 0; i < call.getArguments().size(); i++) {
                if (i > 0) sb.append(", ");
                convertNode(call.getArguments().get(i), sb, 0);
            }

            sb.append(")");
        }
        else if (node instanceof StringLiteral) {
            sb.append("\"").append(((StringLiteral) node).getValue()).append("\"");
        }
        else if (node instanceof NumberLiteral) {
            sb.append(((NumberLiteral) node).getValue());
        }
        else {
            // 使用 Rhino 内置的 toSource() 作为回退
            sb.append(node.getString());
        }
    }



}
