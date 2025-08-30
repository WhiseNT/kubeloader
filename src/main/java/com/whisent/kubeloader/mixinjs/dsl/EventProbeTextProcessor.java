package com.whisent.kubeloader.mixinjs.dsl;

import java.util.List;

public class EventProbeTextProcessor {
    
    /**
     * 在指定的事件回调函数中注入代码
     * 
     * @param sourceCode 原始源代码
     * @param eventName 事件名称，如'StartupEvents.init'或'ItemEvents.rightclicked'
     * @param targetLocation 目标位置，指定要注入第几个匹配的事件
     * @param position 注入位置 ('head' 或 'tail')
     * @param functionBody 要注入的函数体
     * @return 注入后的源代码
     */
    public static String injectEventProbe(String sourceCode, String eventName, int targetLocation, String position, String functionBody) {
        System.out.println("应用DSL: " + eventName + " 在位置 " + targetLocation + " " + position + " 注入 " + functionBody);
        
        // 使用Lexer进行词法分析
        EventProbeLexer lexer = new EventProbeLexer(sourceCode);
        List<EventProbeLexer.Token> tokens = lexer.tokenize();
        
        System.out.println("词法分析完成，生成的token数量: " + (tokens.size() - 1)); // 不包括EOF
        for (EventProbeLexer.Token token : tokens) {
            if (token.type != EventProbeLexer.TokenType.EOF) {
                System.out.println("Token: " + token);
            }
        }
        
        // 查找事件订阅
        int eventStartIndex = findEventSubscription(tokens, eventName, targetLocation);
        if (eventStartIndex == -1) {
            System.out.println("未找到事件订阅: " + eventName + " 位置: " + targetLocation);
            return sourceCode;
        }
        
        System.out.println("找到事件订阅，起始位置: " + eventStartIndex);
        
        // 查找左括号
        int leftParenIndex = findLeftParen(tokens, eventStartIndex);
        if (leftParenIndex == -1) {
            System.out.println("未找到左括号");
            return sourceCode;
        }
        
        System.out.println("找到左括号，位置: " + leftParenIndex);
        
        // 查找箭头函数操作符（跳过参数）
        int arrowIndex = findArrowOperator(tokens, leftParenIndex);
        if (arrowIndex == -1) {
            System.out.println("未找到箭头操作符");
            return sourceCode;
        }
        
        System.out.println("找到箭头操作符，位置: " + arrowIndex);
        
        int braceIndex = findOpenBrace(tokens, arrowIndex);
        if (braceIndex == -1) {
            System.out.println("未找到开大括号");
            return sourceCode;
        }
        
        System.out.println("找到开大括号，位置: " + braceIndex);
        
        // 查找匹配的闭大括号
        int closeBraceIndex = findMatchingCloseBrace(tokens, braceIndex);
        if (closeBraceIndex == -1) {
            System.out.println("未找到匹配的闭大括号");
            return sourceCode;
        }
        
        System.out.println("找到匹配的闭大括号，位置: " + closeBraceIndex);
        
        // 重建代码并注入
        return rebuildCodeWithInjection(sourceCode, tokens, braceIndex, closeBraceIndex, position, functionBody);
    }
    
    /**
     * 查找事件订阅的位置
     */
    public static int findEventSubscription(List<EventProbeLexer.Token> tokens, String eventName, int targetLocation) {
        // 检查事件名称是否为空
        if (eventName == null || eventName.isEmpty()) {
            return -1;
        }
        
        // 将事件名称拆分为多个部分，例如 "StartupEvents.init" 拆分为 ["StartupEvents", "init"]
        String[] eventParts = eventName.split("\\.");
        
        int foundCount = 0; // 记录找到的匹配项数量
        
        for (int i = 0; i < tokens.size() - eventParts.length; i++) {
            boolean match = true;
            
            // 检查是否匹配事件名称的所有部分
            for (int j = 0; j < eventParts.length; j++) {
                EventProbeLexer.Token token = tokens.get(i + j * 2); // 考虑中间的DOT token
                if (token.type != EventProbeLexer.TokenType.IDENTIFIER || !eventParts[j].equals(token.value)) {
                    match = false;
                    break;
                }
                
                // 检查点号
                if (j < eventParts.length - 1) {
                    EventProbeLexer.Token dotToken = tokens.get(i + j * 2 + 1);
                    if (dotToken.type != EventProbeLexer.TokenType.DOT) {
                        match = false;
                        break;
                    }
                }
            }
            
            if (match) {
                // 如果找到了匹配项
                if (foundCount == targetLocation) {
                    // 这是我们要找的第targetLocation个匹配项
                    return i;
                }
                foundCount++; // 增加找到的匹配项计数
            }
        }
        
        return -1;
    }
    
    // 保持向后兼容的重载方法
    public static String injectEventProbe(String sourceCode, String eventName, String position, String functionBody) {
        return injectEventProbe(sourceCode, eventName, 0, position, functionBody);
    }
    
    /**
     * 查找左括号的位置
     */
    private static int findLeftParen(List<EventProbeLexer.Token> tokens, int startIndex) {
        for (int i = startIndex; i < tokens.size() - 1; i++) {
            EventProbeLexer.Token token = tokens.get(i);
            if (token.type == EventProbeLexer.TokenType.LEFT_PAREN) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 查找箭头操作符的位置（跳过参数）
     */
    private static int findArrowOperator(List<EventProbeLexer.Token> tokens, int startIndex) {
        for (int i = startIndex; i < tokens.size() - 1; i++) {
            EventProbeLexer.Token token = tokens.get(i);
            // 查找 => 符号
            if (token.type == EventProbeLexer.TokenType.ARROW_OPERATOR && "=>".equals(token.value)) {
                return i;
            }
            
            // 如果遇到右括号，说明已经跳过了参数部分
            if (token.type == EventProbeLexer.TokenType.RIGHT_PAREN) {
                // 继续查找箭头操作符
                continue;
            }
        }
        return -1;
    }
    
    /**
     * 查找开大括号的位置
     */
    private static int findOpenBrace(List<EventProbeLexer.Token> tokens, int startIndex) {
        for (int i = startIndex; i < tokens.size() - 1; i++) {
            EventProbeLexer.Token token = tokens.get(i);
            if (token.type == EventProbeLexer.TokenType.LEFT_BRACE) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 查找匹配的闭大括号
     */
    private static int findMatchingCloseBrace(List<EventProbeLexer.Token> tokens, int openBraceIndex) {
        int braceCount = 1;
        for (int i = openBraceIndex + 1; i < tokens.size() - 1; i++) {
            EventProbeLexer.Token token = tokens.get(i);
            if (token.type == EventProbeLexer.TokenType.LEFT_BRACE) {
                braceCount++;
            } else if (token.type == EventProbeLexer.TokenType.RIGHT_BRACE) {
                braceCount--;
                if (braceCount == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * 重建代码并注入函数体
     */
    private static String rebuildCodeWithInjection(String sourceCode, List<EventProbeLexer.Token> tokens, 
            int openBraceIndex, int closeBraceIndex, String position, String functionBody) {
        
        EventProbeLexer.Token openBraceToken = tokens.get(openBraceIndex);
        EventProbeLexer.Token closeBraceToken = tokens.get(closeBraceIndex);
        
        // 获取开大括号后和闭大括号前的内容
        int openBraceEnd = openBraceToken.position + 1;
        int closeBraceStart = closeBraceToken.position;
        
        String beforeCode = sourceCode.substring(0, openBraceEnd);
        String functionBodyContent = sourceCode.substring(openBraceEnd, closeBraceStart);
        String afterCode = sourceCode.substring(closeBraceStart);
        
        String injectedBody;
        if ("head".equals(position)) {
            // 在函数体头部注入
            injectedBody = "\n" + functionBody + functionBodyContent;
        } else { // tail
            // 在函数体尾部注入
            injectedBody = functionBodyContent + "\n" + functionBody + "\n";
        }
        
        return beforeCode + injectedBody + afterCode;
    }
    
    /**
     * 应用EventProbe DSL到源代码
     * 
     * @param sourceCode 源代码
     * @param dsl EventProbe DSL对象
     * @return 应用后的源代码
     */
    public static String applyDSL(String sourceCode, EventProbeDSL dsl) {
        return injectEventProbe(sourceCode, dsl.getEventName(), dsl.getTargetLocation(), dsl.getPosition(), dsl.getFunctionBody());
    }
    
    /**
     * 从函数声明中提取函数体
     * 
     * @param functionCode 函数声明代码，例如 "function myFunc() { console.log('test'); }"
     * @return 函数体内容，例如 "console.log('test');"
     */
    public static String extractFunctionBody(String functionCode) {
        //System.out.println("提取函数体，输入代码: " + functionCode);
        // 使用Lexer解析函数代码
        EventProbeLexer lexer = new EventProbeLexer(functionCode);
        List<EventProbeLexer.Token> tokens = lexer.tokenize();
        
        //System.out.println("词法分析完成，token数量: " + tokens.size());
        for (EventProbeLexer.Token token : tokens) {
            if (token.type != EventProbeLexer.TokenType.EOF) {
                System.out.println("Token: " + token);
            }
        }
        
        // 查找function关键字
        int functionIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type == EventProbeLexer.TokenType.FUNCTION_KEYWORD) {
                functionIndex = i;
                //System.out.println("找到function关键字，位置: " + functionIndex);
                break;
            }
        }
        
        if (functionIndex == -1) {
            //System.out.println("未找到function关键字，返回原始代码");
            return functionCode;
        }
        
        // 查找开大括号
        int openBraceIndex = -1;
        for (int i = functionIndex; i < tokens.size(); i++) {
            if (tokens.get(i).type == EventProbeLexer.TokenType.LEFT_BRACE) {
                openBraceIndex = i;
                //System.out.println("找到开大括号，位置: " + openBraceIndex);
                break;
            }
        }
        
        if (openBraceIndex == -1) {
            //System.out.println("未找到开大括号，返回原始代码");
            return functionCode;
        }
        
        // 查找匹配的闭大括号
        int closeBraceIndex = findMatchingCloseBrace(tokens, openBraceIndex);
        if (closeBraceIndex == -1) {
            //System.out.println("未找到匹配的闭大括号，返回原始代码");
            return functionCode;
        }
        
        //System.out.println("找到匹配的闭大括号，位置: " + closeBraceIndex);
        
        EventProbeLexer.Token openBraceToken = tokens.get(openBraceIndex);
        EventProbeLexer.Token closeBraceToken = tokens.get(closeBraceIndex);
        
        // 提取函数体内容（不包括大括号）
        int openBraceEnd = openBraceToken.position + 1;
        int closeBraceStart = closeBraceToken.position;
        
        //System.out.println("提取范围: " + openBraceEnd + " 到 " + closeBraceStart);
        
        // 保留原始格式，包括引号和其他字符
        String result = functionCode.substring(openBraceEnd, closeBraceStart);
        //System.out.println("提取结果: " + result);
        return result;
    }
}