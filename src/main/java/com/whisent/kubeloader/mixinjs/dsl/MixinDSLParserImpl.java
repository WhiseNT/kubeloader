package com.whisent.kubeloader.mixinjs.dsl;

import com.whisent.kubeloader.mixinjs.dsl.MixinDSLLexer.Token;
import com.whisent.kubeloader.mixinjs.dsl.MixinDSLLexer.TokenType;

import java.util.ArrayList;
import java.util.List;

public class MixinDSLParserImpl {
    private final List<Token> tokens;
    private int position;

    public MixinDSLParserImpl(List<Token> tokens) {
        this.tokens = tokens;
        this.position = 0;
    }

    public List<MixinDSL> parse() {
        List<MixinDSL> result = new ArrayList<>();

        while (position < tokens.size() && currentToken().type != TokenType.EOF) {
            MixinDSL dsl = parseMixinDSL();
            if (dsl != null) {
                result.add(dsl);
            }
        }

        return result;
    }

    private MixinDSL parseMixinDSL() {
        System.out.println("开始解析MixinDSL，当前位置: " + position);

        // 检查是否以Mixin关键字开始
        if (!match(TokenType.MIXIN_KEYWORD)) {
            System.out.println("未找到Mixin关键字，跳过");
            // 跳过直到找到可能的开始位置
            skipToNextStatement();
            return null;
        }

        System.out.println("找到Mixin关键字");
        MixinDSL dsl = new MixinDSL();

        // 解析.type('...')
        if (!match(TokenType.DOT)) {
            System.out.println("期望'.'但未找到");
            return dsl;
        }

        Token typeToken = currentToken();
        if (!match(TokenType.IDENTIFIER) || !"type".equals(typeToken.value)) {
            System.out.println("期望'type'标识符但未找到，实际为: " + (typeToken != null ? typeToken.value : "null"));
            return dsl;
        }

        if (!match(TokenType.LEFT_PAREN)) {
            System.out.println("期望'('但未找到");
            return dsl;
        }

        Token typeValueToken = currentToken();
        if (!match(TokenType.STRING_LITERAL)) {
            System.out.println("期望字符串字面量但未找到");
            return dsl;
        }

        String type = typeValueToken.value;
        dsl.setType(type);
        System.out.println("解析到类型: " + type);

        if (!match(TokenType.RIGHT_PAREN)) {
            System.out.println("期望')'但未找到");
            return dsl;
        }

        // 解析.at('...')
        if (!match(TokenType.DOT)) {
            System.out.println("期望'.'但未找到");
            return dsl;
        }

        Token atToken = currentToken();
        if (!match(TokenType.IDENTIFIER) || !"at".equals(atToken.value)) {
            System.out.println("期望'at'标识符但未找到，实际为: " + (atToken != null ? atToken.value : "null"));
            return dsl;
        }

        if (!match(TokenType.LEFT_PAREN)) {
            System.out.println("期望'('但未找到");
            return dsl;
        }

        Token atValueToken = currentToken();
        if (!match(TokenType.STRING_LITERAL)) {
            System.out.println("期望字符串字面量但未找到");
            return dsl;
        }

        String at = atValueToken.value;
        dsl.setAt(at);
        System.out.println("解析到位置: " + at);

        if (!match(TokenType.RIGHT_PAREN)) {
            System.out.println("期望')'但未找到");
            return dsl;
        }

        // 解析.in('...')
        if (!match(TokenType.DOT)) {
            System.out.println("期望'.'但未找到");
            return dsl;
        }

        Token inToken = currentToken();
        if (!match(TokenType.IDENTIFIER) || !"in".equals(inToken.value)) {
            System.out.println("期望'in'标识符但未找到，实际为: " + (inToken != null ? inToken.value : "null"));
            return dsl;
        }

        if (!match(TokenType.LEFT_PAREN)) {
            System.out.println("期望'('但未找到");
            return dsl;
        }

        Token inValueToken = currentToken();
        if (!match(TokenType.STRING_LITERAL)) {
            System.out.println("期望字符串字面量但未找到");
            return dsl;
        }

        String in = inValueToken.value;
        dsl.setTarget(in);
        System.out.println("解析到目标: " + in);

        if (!match(TokenType.RIGHT_PAREN)) {
            System.out.println("期望')'但未找到");
            return dsl;
        }

        // 解析.inject(...)
        if (!match(TokenType.DOT)) {
            System.out.println("期望'.'但未找到");
            return dsl;
        }

        Token injectToken = currentToken();
        if (!match(TokenType.IDENTIFIER) || !"inject".equals(injectToken.value)) {
            System.out.println("期望'inject'标识符但未找到，实际为: " + (injectToken != null ? injectToken.value : "null"));
            return dsl;
        }

        if (!match(TokenType.LEFT_PAREN)) {
            System.out.println("期望'('但未找到");
            return dsl;
        }

        // 解析inject函数体
        String injectCode = parseInjectFunction();
        if (injectCode != null) {
            dsl.setAction(injectCode);
            System.out.println("解析到注入代码: " + injectCode);
        }

        // 不再尝试匹配RIGHT_PAREN，因为parseInjectFunction已经处理了

        System.out.println("MixinDSL解析完成");
        return dsl;
    }

    private String parseInjectFunction() {
        System.out.println("开始解析inject函数体，当前位置: " + position);

        // 跳过function关键字和参数列表，找到函数体开始的大括号
        while (position < tokens.size() && currentToken().type != TokenType.LEFT_BRACE) {
            System.out.println("跳过函数声明部分: " + currentToken().value);
            position++;
        }

        // 如果没有找到左大括号，返回null
        if (position >= tokens.size() || currentToken().type != TokenType.LEFT_BRACE) {
            System.out.println("未找到函数体开始的大括号");
            return null;
        }

        // 记录函数体开始位置（不包括左大括号）
        int startTokenPos = position + 1;
        position++; // 跳过左大括号

        // 查找匹配的右大括号，考虑嵌套情况
        int braceCount = 1; // 已经匹配了一个左大括号

        // 继续解析直到找到匹配的右大括号
        while (position < tokens.size() && braceCount > 0) {
            Token token = currentToken();
            System.out.println("解析函数体中的token: " + token);

            switch (token.type) {
                case LEFT_BRACE:
                    braceCount++;
                    System.out.println("左大括号，计数: " + braceCount);
                    break;
                case RIGHT_BRACE:
                    braceCount--;
                    System.out.println("右大括号，计数: " + braceCount);
                    break;
                case EOF:
                    System.out.println("意外的文件结束");
                    return null;
            }

            if (braceCount > 0) {
                position++;
            }
        }

        if (braceCount != 0) {
            System.out.println("大括号不匹配");
            return null;
        }

        // position现在指向右大括号，不包括它
        int endTokenPos = position - 1;
        position++; // 移动到右大括号之后

        // 如果没有内容（空函数体），返回空字符串
        if (startTokenPos > endTokenPos) {
            return "";
        }

        // 构造函数体代码 - 从原始tokens中提取（不包括大括号）
        StringBuilder functionCode = new StringBuilder();
        for (int i = startTokenPos; i <= endTokenPos; i++) { // 不包括大括号
            Token token = tokens.get(i);

            // 对于字符串字面量，需要添加引号
            if (token.type == TokenType.STRING_LITERAL) {
                functionCode.append("'").append(token.value).append("'");
            } else {
                functionCode.append(token.value);
            }

            // 添加适当的空格（简化处理）
            if (i < endTokenPos) { // 不是最后一个token
                TokenType currentType = token.type;
                TokenType nextType = tokens.get(i + 1).type;

                // 在某些token之间添加空格
                if (currentType == TokenType.FUNCTION_KEYWORD ||
                        currentType == TokenType.IDENTIFIER ||
                        currentType == TokenType.STRING_LITERAL ||
                        currentType == TokenType.RIGHT_PAREN ||
                        currentType == TokenType.RIGHT_BRACE ||
                        currentType == TokenType.LEFT_BRACE) {

                    if (nextType == TokenType.IDENTIFIER ||
                            nextType == TokenType.FUNCTION_KEYWORD ||
                            nextType == TokenType.STRING_LITERAL ||
                            nextType == TokenType.LEFT_BRACE ||
                            nextType == TokenType.LEFT_PAREN ||
                            nextType == TokenType.RIGHT_BRACE) {
                        functionCode.append(" ");
                    }
                }
            }
        }

        return functionCode.toString();
    }

    private boolean match(TokenType expectedType) {
        System.out.println("尝试匹配类型: " + expectedType + "，当前位置: " + position + "，当前token: " + (currentToken() != null ? currentToken().type : "null") + "，值: " + (currentToken() != null ? currentToken().value : "null"));
        if (position < tokens.size() && tokens.get(position).type == expectedType) {
            System.out.println("匹配成功");
            position++;
            return true;
        }
        System.out.println("匹配失败");
        return false;
    }

    private Token currentToken() {
        if (position < tokens.size()) {
            return tokens.get(position);
        }
        return null;
    }

    private void skipToNextStatement() {
        while (position < tokens.size() &&
                currentToken().type != TokenType.MIXIN_KEYWORD &&
                currentToken().type != TokenType.EOF) {
            position++;
        }
    }
}