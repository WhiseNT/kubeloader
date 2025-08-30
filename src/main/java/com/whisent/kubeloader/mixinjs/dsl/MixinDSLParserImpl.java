package com.whisent.kubeloader.mixinjs.dsl;

import com.whisent.kubeloader.mixinjs.dsl.MixinDSLLexer.Token;
import com.whisent.kubeloader.mixinjs.dsl.MixinDSLLexer.TokenType;
import com.whisent.kubeloader.utils.Debugger;

import java.util.ArrayList;
import java.util.List;

public class MixinDSLParserImpl {
    private final List<Token> tokens;
    private int position;

    public MixinDSLParserImpl(List<Token> tokens) {
        this.tokens = tokens;
        this.position = 0;
        Debugger.out("创建MixinDSLParserImpl实例，token数量: " + tokens.size());
    }

    public List<MixinDSL> parse() {
        Debugger.out("开始解析DSL，token列表:");
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            Debugger.out("  [" + i + "] " + token);
        }
        
        List<MixinDSL> result = new ArrayList<>();

        while (position < tokens.size() && currentToken().type != TokenType.EOF) {
            Debugger.out("循环解析，当前位置: " + position);
            MixinDSL dsl = parseMixinDSL();
            if (dsl != null) {
                Debugger.out("成功解析一个DSL对象: " + dsl);
                result.add(dsl);
            } else {
                Debugger.out("DSL解析返回null");
            }
        }

        return result;
    }

    private MixinDSL parseMixinDSL() {
        Debugger.out("开始解析MixinDSL，当前位置: " + position);

        // 检查是否以Mixin关键字开始
        if (!match(TokenType.MIXIN_KEYWORD)) {
            Debugger.out("未找到Mixin关键字，跳过");
            // 跳过直到找到可能的开始位置
            skipToNextStatement();
            return null;
        }

        Debugger.out("找到Mixin关键字");
        MixinDSL dsl = new MixinDSL();

        // 解析.type('...')
        skipWhitespaceAndNewlines();
        Debugger.out("解析.type()，当前位置: " + position);
        if (!match(TokenType.DOT)) {
            Debugger.out("期望'.'但未找到");
            return dsl;
        }

        skipWhitespaceAndNewlines();
        Token typeToken = currentToken();
        Debugger.out("typeToken: " + typeToken);
        if (!match(TokenType.IDENTIFIER) || !"type".equals(typeToken.value)) {
            Debugger.out("期望'type'标识符但未找到，实际为: " + (typeToken != null ? typeToken.value : "null"));
            return dsl;
        }

        skipWhitespaceAndNewlines();
        if (!match(TokenType.LEFT_PAREN)) {
            Debugger.out("期望'('但未找到");
            return dsl;
        }

        skipWhitespaceAndNewlines();
        Token typeValueToken = currentToken();
        Debugger.out("typeValueToken: " + typeValueToken);
        if (!match(TokenType.STRING_LITERAL)) {
            Debugger.out("期望字符串字面量但未找到");
            return dsl;
        }

        String type = typeValueToken.value;

        Debugger.out("解析到类型: " + type);
        dsl.setType(type);

        skipWhitespaceAndNewlines();
        if (!match(TokenType.RIGHT_PAREN)) {
            Debugger.out("期望')'但未找到");
            return dsl;
        }

        // 解析.at('...')
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        Debugger.out("解析.at()，当前位置: " + position);
        if (!match(TokenType.DOT)) {
            Debugger.out("期望'.'但未找到");
            return dsl;
        }

        skipWhitespaceAndNewlines();
        Token atToken = currentToken();
        Debugger.out("atToken: " + atToken);
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.IDENTIFIER) || !"at".equals(atToken.value)) {
            Debugger.out("期望'at'标识符但未找到，实际为: " + (atToken != null ? atToken.value : "null"));
            return dsl;
        }

        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.LEFT_PAREN)) {
            Debugger.out("期望'('但未找到");
            return dsl;
        }

        skipWhitespaceAndNewlines();
        Token atValueToken = currentToken();
        Debugger.out("atValueToken: " + atValueToken);
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.STRING_LITERAL)) {
            Debugger.out("期望字符串字面量但未找到");
            return dsl;
        }

        String at = atValueToken.value;
        dsl.setAt(at);
        Debugger.out("解析到位置: " + at);

        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.RIGHT_PAREN)) {
            Debugger.out("期望')'但未找到");
            return dsl;
        }

        // 解析.in('...')
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        Debugger.out("解析.in()，当前位置: " + position);
        if (!match(TokenType.DOT)) {
            Debugger.out("期望'.'但未找到");
            return dsl;
        }

        skipWhitespaceAndNewlines();
        Token inToken = currentToken();
        Debugger.out("inToken: " + inToken);
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.IDENTIFIER) || !"in".equals(inToken.value)) {
            Debugger.out("期望'in'标识符但未找到，实际为: " + (inToken != null ? inToken.value : "null"));
            return dsl;
        }

        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.LEFT_PAREN)) {
            Debugger.out("期望'('但未找到");
            return dsl;
        }

        skipWhitespaceAndNewlines();
        Token inValueToken = currentToken();
        Debugger.out("inValueToken: " + inValueToken);
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.STRING_LITERAL)) {
            Debugger.out("期望字符串字面量但未找到");
            return dsl;
        }

        String in = inValueToken.value;
        dsl.setTarget(in);
        Debugger.out("解析到目标: " + in);

        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.RIGHT_PAREN)) {
            Debugger.out("期望')'但未找到");
            return dsl;
        }

        // 解析.locate(...) - 可选
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        Debugger.out("检查locate方法，当前位置: " + position + "，当前token类型: " + (position < tokens.size() ? currentToken().type : "EOF"));
        if (position < tokens.size() && currentToken().type == TokenType.DOT) {
            Debugger.out("找到点号，跳过点号");
            match(TokenType.DOT); // 跳过点号
            skipWhitespaceAndNewlines();
            
            Token locateToken = currentToken();
            Debugger.out("locateToken类型: " + locateToken.type + "，值: " + locateToken.value);
            if (locateToken.type == TokenType.IDENTIFIER && "locate".equals(locateToken.value)) {
                Debugger.out("找到locate方法");
                position++; // 手动增加位置，跳过locate标识符
                skipWhitespaceAndNewlines();
                
                if (position >= tokens.size() || currentToken().type != TokenType.LEFT_PAREN) {
                    Debugger.out("期望'('但未找到，当前位置: " + position + "，当前token类型: " + (position < tokens.size() ? currentToken().type : "EOF"));
                    if (position < tokens.size()) {
                        Debugger.out("当前token值: " + currentToken().value);
                    }
                    return dsl;
                }
                position++; // 跳过左括号
                
                skipWhitespaceAndNewlines();
                if (position >= tokens.size()) {
                    Debugger.out("意外到达文件末尾");
                    return dsl;
                }
                
                Token locationValueToken = currentToken();
                Debugger.out("locationValueToken类型: " + locationValueToken.type + "，值: " + locationValueToken.value);
                if (locationValueToken.type != TokenType.STRING_LITERAL && 
                    locationValueToken.type != TokenType.IDENTIFIER && 
                    locationValueToken.type != TokenType.NUMBER_LITERAL) {
                    Debugger.out("期望数字或字符串字面量但未找到，实际类型: " + locationValueToken.type);
                    return dsl;
                }
                
                try {
                    // 尝试解析为整数
                    int location = Integer.parseInt(locationValueToken.value);
                    dsl.setTargetLocation(location);
                    Debugger.out("解析到目标位置: " + location);
                } catch (NumberFormatException e) {
                    Debugger.out("无法解析目标位置为数字: " + locationValueToken.value);
                    return dsl;
                }
                
                position++; // 跳过数字字面量
                skipWhitespaceAndNewlines();
                
                if (position >= tokens.size() || currentToken().type != TokenType.RIGHT_PAREN) {
                    Debugger.out("期望')'但未找到，当前位置: " + position + "，当前token类型: " + (position < tokens.size() ? currentToken().type : "EOF"));
                    return dsl;
                }
                position++; // 跳过右括号
                Debugger.out("locate方法解析完成");
            } else {
                // 不是locate方法，回退位置
                Debugger.out("不是locate方法，回退位置");
                position--;
            }
        } else {
            Debugger.out("未找到DOT或已到达文件末尾");
        }
        
        // 解析.priority(...) - 可选
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        Debugger.out("检查priority方法，当前位置: " + position + "，当前token类型: " + (position < tokens.size() ? currentToken().type : "EOF"));
        if (position < tokens.size() && currentToken().type == TokenType.DOT) {
            Debugger.out("找到点号，跳过点号");
            match(TokenType.DOT); // 跳过点号
            skipWhitespaceAndNewlines();
            
            Token priorityToken = currentToken();
            Debugger.out("priorityToken类型: " + priorityToken.type + "，值: " + priorityToken.value);
            if (priorityToken.type == TokenType.IDENTIFIER && "priority".equals(priorityToken.value)) {
                Debugger.out("找到priority方法");
                position++; // 手动增加位置，跳过priority标识符
                skipWhitespaceAndNewlines();
                
                if (position >= tokens.size() || currentToken().type != TokenType.LEFT_PAREN) {
                    Debugger.out("期望'('但未找到，当前位置: " + position + "，当前token类型: " + (position < tokens.size() ? currentToken().type : "EOF"));
                    if (position < tokens.size()) {
                        Debugger.out("当前token值: " + currentToken().value);
                    }
                    return dsl;
                }
                position++; // 跳过左括号
                
                skipWhitespaceAndNewlines();
                if (position >= tokens.size()) {
                    Debugger.out("意外到达文件末尾");
                    return dsl;
                }
                
                Token priorityValueToken = currentToken();
                Debugger.out("priorityValueToken类型: " + priorityValueToken.type + "，值: " + priorityValueToken.value);
                if (priorityValueToken.type != TokenType.STRING_LITERAL && 
                    priorityValueToken.type != TokenType.IDENTIFIER && 
                    priorityValueToken.type != TokenType.NUMBER_LITERAL) {
                    Debugger.out("期望数字或字符串字面量但未找到，实际类型: " + priorityValueToken.type);
                    return dsl;
                }
                
                try {
                    // 尝试解析为整数
                    int priority = Integer.parseInt(priorityValueToken.value);
                    dsl.setPriority(priority);
                    Debugger.out("解析到优先级: " + priority);
                } catch (NumberFormatException e) {
                    Debugger.out("无法解析优先级为数字: " + priorityValueToken.value);
                    return dsl;
                }
                
                position++; // 跳过数字字面量
                skipWhitespaceAndNewlines();
                
                if (position >= tokens.size() || currentToken().type != TokenType.RIGHT_PAREN) {
                    Debugger.out("期望')'但未找到，当前位置: " + position + "，当前token类型: " + (position < tokens.size() ? currentToken().type : "EOF"));
                    return dsl;
                }
                position++; // 跳过右括号
                Debugger.out("priority方法解析完成");
            } else {
                // 不是priority方法，回退位置
                Debugger.out("不是priority方法，回退位置");
                position--;
            }
        } else {
            Debugger.out("未找到DOT或已到达文件末尾");
        }

        // 解析.inject(...)
        Debugger.out("开始解析.inject方法，当前位置: " + position);
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        Debugger.out("跳过换行符后，当前位置: " + position + "，token数量: " + tokens.size());
        if (position >= tokens.size()) {
            Debugger.out("已到达文件末尾，无法继续解析");
            return dsl;
        }
        
        Debugger.out("当前位置token类型: " + currentToken().type + "，值: " + currentToken().value);
        if (!match(TokenType.DOT)) {
            Debugger.out("期望'.'但未找到");
            return dsl;
        }

        skipWhitespaceAndNewlines();
        Token injectToken = currentToken();
        Debugger.out("injectToken: " + injectToken);
        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.IDENTIFIER) || !"inject".equals(injectToken.value)) {
            Debugger.out("期望'inject'标识符但未找到，实际为: " + (injectToken != null ? injectToken.value : "null"));
            return dsl;
        }

        // 跳过可能存在的换行符
        skipWhitespaceAndNewlines();
        if (!match(TokenType.LEFT_PAREN)) {
            Debugger.out("期望'('但未找到");
            return dsl;
        }

        // 解析inject函数体
        String injectCode = parseInjectFunction();
        Debugger.out("解析inject函数体结果: " + injectCode);
        if (injectCode != null) {
            String extractedBody = EventProbeTextProcessor.extractFunctionBody(injectCode);
            Debugger.out("提取函数体结果: " + extractedBody);
            dsl.setAction(extractedBody);
            Debugger.out("设置actionCode: " + extractedBody);
        } else {
            Debugger.out("injectCode为null，无法设置actionCode");
        }

        // 不再尝试匹配RIGHT_PAREN，因为parseInjectFunction已经处理了

        Debugger.out("MixinDSL解析完成: " + dsl);
        return dsl;
    }

    private void skipWhitespaceAndNewlines() {
        Debugger.out("开始跳过空白字符和换行符，当前位置: " + position);
        while (position < tokens.size() && 
               (currentToken().type == TokenType.NEWLINE || 
                currentToken().type == TokenType.WHITESPACE)) {
            Debugger.out("跳过token: " + currentToken().type + " 值: '" + currentToken().value + "'");
            position++;
        }
        Debugger.out("跳过完成，新位置: " + position);
    }

    private String parseInjectFunction() {
        Debugger.out("开始解析inject函数体，当前位置: " + position);

        // 收集函数声明的原始代码，包括换行符
        StringBuilder functionCodeBuilder = new StringBuilder();
        
        // 记录是否刚刚添加了function关键字
        boolean afterFunctionKeyword = false;
        
        // 收集function关键字和参数列表，直到找到函数体开始的大括号
        while (position < tokens.size() && currentToken().type != TokenType.LEFT_BRACE) {
            Token token = currentToken();
            Debugger.out("收集函数声明部分，token类型: " + token.type + " 值: '" + token.value + "'");
            
            // 如果前一个token是function关键字，而当前token是标识符，则添加空格
            if (afterFunctionKeyword && token.type == TokenType.IDENTIFIER) {
                functionCodeBuilder.append(" ");
                afterFunctionKeyword = false;
            }
            
            // 处理注释
            if (token.type == TokenType.COMMENT) {
                functionCodeBuilder.append(token.value);
            }
            // 对于字符串字面量，使用原始值（包括引号）
            else if (token.type == TokenType.STRING_LITERAL) {
                functionCodeBuilder.append(token.originalValue);
            } else {
                functionCodeBuilder.append(token.value);
            }
            
            // 标记是否刚刚处理了function关键字
            if (token.type == TokenType.FUNCTION_KEYWORD) {
                afterFunctionKeyword = true;
            }
            
            Debugger.out("收集函数声明部分: " + token.value + " 类型: " + token.type);
            position++;
        }

        // 添加左大括号
        if (position < tokens.size() && currentToken().type == TokenType.LEFT_BRACE) {
            Token token = currentToken();
            functionCodeBuilder.append(token.value);
            Debugger.out("添加左大括号: " + token.value);
        } else {
            Debugger.out("未找到函数体开始的大括号");
            return null;
        }

        position++; // 跳过左大括号

        // 查找匹配的右大括号，考虑嵌套情况
        int braceCount = 1; // 已经匹配了一个左大括号

        // 继续解析直到找到匹配的右大括号
        while (position < tokens.size() && braceCount > 0) {
            Token token = currentToken();
            Debugger.out("解析函数体中的token，类型: " + token.type + " 值: '" + token.value + "' braceCount: " + braceCount);
            
            // 添加所有token的值，包括换行符
            // 处理注释
            if (token.type == TokenType.COMMENT) {
                functionCodeBuilder.append(token.value);
            }
            // 对于字符串字面量，使用原始值（包括引号）
            else if (token.type == TokenType.STRING_LITERAL) {
                functionCodeBuilder.append(token.originalValue);
            } else {
                functionCodeBuilder.append(token.value);
            }
            Debugger.out("解析函数体中的token: " + token + " braceCount: " + braceCount);

            switch (token.type) {
                case LEFT_BRACE:
                    braceCount++;
                    Debugger.out("左大括号，计数: " + braceCount);
                    break;
                case RIGHT_BRACE:
                    braceCount--;
                    Debugger.out("右大括号，计数: " + braceCount);
                    break;
                case EOF:
                    Debugger.out("意外的文件结束");
                    return null;
            }

            if (braceCount > 0) {
                position++;
            }
        }

        if (braceCount != 0) {
            Debugger.out("大括号不匹配");
            return null;
        }

        // position现在指向右大括号，我们需要添加它到函数代码中
        if (position < tokens.size() && currentToken().type == TokenType.RIGHT_BRACE) {
            Token token = currentToken();
            functionCodeBuilder.append(token.value);
            Debugger.out("添加右大括号: " + token.value);
            position++; // 跳过右大括号
        }

        // 获取完整的函数声明代码
        String functionCode = functionCodeBuilder.toString();
        Debugger.out("完整函数代码: " + functionCode);

        // 使用EventProbeTextProcessor提取函数体
        String functionBody = EventProbeTextProcessor.extractFunctionBody(functionCode);
        Debugger.out("提取的函数体: " + functionBody);

        return functionCode; // 返回完整的函数代码而不是函数体
    }

    private boolean match(TokenType expectedType) {
        Debugger.out("尝试匹配类型: " + expectedType + "，当前位置: " + position + "，当前token: " + (currentToken() != null ? currentToken().type : "null") + "，值: " + (currentToken() != null ? currentToken().value : "null"));
        if (position < tokens.size() && tokens.get(position).type == expectedType) {
            Debugger.out("匹配成功");
            position++;
            return true;
        }
        Debugger.out("匹配失败");
        return false;
    }

    private Token currentToken() {
        if (position < tokens.size()) {
            return tokens.get(position);
        }
        return null;
    }

    private void skipToNextStatement() {
        Debugger.out("跳到下一个语句，当前位置: " + position);
        while (position < tokens.size() &&
                currentToken().type != TokenType.MIXIN_KEYWORD &&
                currentToken().type != TokenType.EOF) {
            position++;
        }
        Debugger.out("跳转后位置: " + position);
    }
}