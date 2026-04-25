package com.whisent.kubeloader.klm.dsl;

import com.whisent.kubeloader.utils.Debugger;

import java.util.List;

public class EventProbeTextProcessor {
    
    /**
     * 在指定的事件回调函数中注入代码
     */
    public static String injectEventProbe(String sourceCode, String eventName, int targetLocation, String position, String functionBody) {
        return injectEventProbe(sourceCode, eventName, targetLocation, position, functionBody, -1);
    }
    
    /**
     * 在指定的事件回调函数中注入代码（支持offset）
     */
    public static String injectEventProbe(String sourceCode, String eventName, int targetLocation, String position, String functionBody, int offset) {
        Debugger.out("应用DSL: " + eventName + " 在位置 " + targetLocation + " " + position + " 注入 " + functionBody + " offset=" + offset);
        
        EventProbeLexer lexer = new EventProbeLexer(sourceCode);
        List<EventProbeLexer.Token> tokens = lexer.tokenize();
        
        Debugger.out("词法分析完成，生成的token数量: " + (tokens.size() - 1));
        
        int eventStartIndex = findEventSubscription(tokens, eventName, targetLocation);
        if (eventStartIndex == -1) {
            Debugger.out("未找到事件订阅: " + eventName + " 位置: " + targetLocation);
            return sourceCode;
        }
        
        int leftParenIndex = findLeftParen(tokens, eventStartIndex);
        if (leftParenIndex == -1) {
            Debugger.out("未找到左括号");
            return sourceCode;
        }
        
        int arrowIndex = findArrowOperator(tokens, leftParenIndex);
        if (arrowIndex == -1) {
            Debugger.out("未找到箭头操作符");
            return sourceCode;
        }
        
        int braceIndex = findOpenBrace(tokens, arrowIndex);
        if (braceIndex == -1) {
            Debugger.out("未找到开大括号");
            return sourceCode;
        }
        
        int closeBraceIndex = findMatchingCloseBrace(tokens, braceIndex);
        if (closeBraceIndex == -1) {
            Debugger.out("未找到匹配的闭大括号");
            return sourceCode;
        }
        
        return rebuildCodeWithInjection(sourceCode, tokens, braceIndex, closeBraceIndex, position, functionBody, offset);
    }
    
    /**
     * 查找事件订阅的位置
     */
    public static int findEventSubscription(List<EventProbeLexer.Token> tokens, String eventName, int targetLocation) {
        if (eventName == null || eventName.isEmpty()) return -1;
        
        String[] eventParts = eventName.split("\\.");
        int foundCount = 0;
        
        for (int i = 0; i < tokens.size() - eventParts.length; i++) {
            boolean match = true;
            for (int j = 0; j < eventParts.length; j++) {
                EventProbeLexer.Token token = tokens.get(i + j * 2);
                if (token.type != EventProbeLexer.TokenType.IDENTIFIER || !eventParts[j].equals(token.value)) {
                    match = false;
                    break;
                }
                if (j < eventParts.length - 1) {
                    EventProbeLexer.Token dotToken = tokens.get(i + j * 2 + 1);
                    if (dotToken.type != EventProbeLexer.TokenType.DOT) {
                        match = false;
                        break;
                    }
                }
            }
            if (match) {
                if (foundCount == targetLocation) return i;
                foundCount++;
            }
        }
        return -1;
    }
    
    // 保持向后兼容的重载方法
    public static String injectEventProbe(String sourceCode, String eventName, String position, String functionBody) {
        return injectEventProbe(sourceCode, eventName, 0, position, functionBody);
    }
    
    private static int findLeftParen(List<EventProbeLexer.Token> tokens, int startIndex) {
        for (int i = startIndex; i < tokens.size() - 1; i++) {
            if (tokens.get(i).type == EventProbeLexer.TokenType.LEFT_PAREN) return i;
        }
        return -1;
    }
    
    private static int findArrowOperator(List<EventProbeLexer.Token> tokens, int startIndex) {
        for (int i = startIndex; i < tokens.size() - 1; i++) {
            EventProbeLexer.Token token = tokens.get(i);
            if (token.type == EventProbeLexer.TokenType.ARROW_OPERATOR && "=>".equals(token.value)) return i;
        }
        return -1;
    }
    
    private static int findOpenBrace(List<EventProbeLexer.Token> tokens, int startIndex) {
        for (int i = startIndex; i < tokens.size() - 1; i++) {
            if (tokens.get(i).type == EventProbeLexer.TokenType.LEFT_BRACE) return i;
        }
        return -1;
    }
    
    private static int findMatchingCloseBrace(List<EventProbeLexer.Token> tokens, int openBraceIndex) {
        int braceCount = 1;
        for (int i = openBraceIndex + 1; i < tokens.size() - 1; i++) {
            EventProbeLexer.Token token = tokens.get(i);
            if (token.type == EventProbeLexer.TokenType.LEFT_BRACE) braceCount++;
            else if (token.type == EventProbeLexer.TokenType.RIGHT_BRACE) {
                braceCount--;
                if (braceCount == 0) return i;
            }
        }
        return -1;
    }
    
    /**
     * 重建代码并注入函数体
     */
    private static String rebuildCodeWithInjection(String sourceCode, List<EventProbeLexer.Token> tokens, 
            int openBraceIndex, int closeBraceIndex, String position, String functionBody) {
        return rebuildCodeWithInjection(sourceCode, tokens, openBraceIndex, closeBraceIndex, position, functionBody, -1);
    }
    
    /**
     * 重建代码并注入函数体（支持offset）
     */
    private static String rebuildCodeWithInjection(String sourceCode, List<EventProbeLexer.Token> tokens, 
            int openBraceIndex, int closeBraceIndex, String position, String functionBody, int offset) {
        
        EventProbeLexer.Token openBraceToken = tokens.get(openBraceIndex);
        EventProbeLexer.Token closeBraceToken = tokens.get(closeBraceIndex);
        
        int openBraceEnd = openBraceToken.position + 1;
        int closeBraceStart = closeBraceToken.position;
        
        String beforeCode = sourceCode.substring(0, openBraceEnd);
        String functionBodyContent = sourceCode.substring(openBraceEnd, closeBraceStart);
        String afterCode = sourceCode.substring(closeBraceStart);
        
        String injectedBody;
        if (offset >= 0) {
            String[] lines = functionBodyContent.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            int insertLine = Math.min(offset, lines.length);
            for (int i = 0; i < insertLine; i++) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("\n").append(functionBody);
            for (int i = insertLine; i < lines.length; i++) {
                sb.append("\n").append(lines[i]);
            }
            injectedBody = sb.toString();
        } else if ("head".equals(position)) {
            injectedBody = "\n" + functionBody + functionBodyContent;
        } else {
            injectedBody = functionBodyContent + "\n" + functionBody + "\n";
        }
        
        return beforeCode + injectedBody + afterCode;
    }
    
    /**
     * 应用EventProbe DSL到源代码
     */
    public static String applyDSL(String sourceCode, EventProbeDSL dsl) {
        return injectEventProbe(sourceCode, dsl.getEventName(), dsl.getTargetLocation(), dsl.getPosition(), dsl.getFunctionBody(), dsl.getOffset());
    }
    
    /**
     * 从函数声明中提取函数体
     */
    public static String extractFunctionBody(String functionCode) {
        EventProbeLexer lexer = new EventProbeLexer(functionCode);
        List<EventProbeLexer.Token> tokens = lexer.tokenize();
        
        int functionIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type == EventProbeLexer.TokenType.FUNCTION_KEYWORD) {
                functionIndex = i;
                break;
            }
        }
        if (functionIndex == -1) return functionCode;
        
        int openBraceIndex = -1;
        for (int i = functionIndex; i < tokens.size(); i++) {
            if (tokens.get(i).type == EventProbeLexer.TokenType.LEFT_BRACE) {
                openBraceIndex = i;
                break;
            }
        }
        if (openBraceIndex == -1) return functionCode;
        
        int closeBraceIndex = findMatchingCloseBrace(tokens, openBraceIndex);
        if (closeBraceIndex == -1) return functionCode;
        
        EventProbeLexer.Token openBraceToken = tokens.get(openBraceIndex);
        EventProbeLexer.Token closeBraceToken = tokens.get(closeBraceIndex);
        
        int openBraceEnd = openBraceToken.position + 1;
        int closeBraceStart = closeBraceToken.position;
        
        return functionCode.substring(openBraceEnd, closeBraceStart);
    }
}
