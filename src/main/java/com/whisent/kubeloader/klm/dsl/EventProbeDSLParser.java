package com.whisent.kubeloader.klm.dsl;

import java.util.ArrayList;
import java.util.List;

public class EventProbeDSLParser {
    private final List<EventProbeLexer.Token> tokens;
    private int position;

    public EventProbeDSLParser(List<EventProbeLexer.Token> tokens) {
        this.tokens = tokens;
        this.position = 0;
    }

    public static List<EventProbeDSL> parse(String source) {
        // 使用Lexer进行词法分析
        EventProbeLexer lexer = new EventProbeLexer(source);
        List<EventProbeLexer.Token> tokens = lexer.tokenize();

        // 使用Parser进行语法分析
        EventProbeDSLParser parser = new EventProbeDSLParser(tokens);
        return parser.parse();
    }

    public List<EventProbeDSL> parse() {
        List<EventProbeDSL> result = new ArrayList<>();
        
        while (position < tokens.size() && currentToken().type != EventProbeLexer.TokenType.EOF) {
            if (currentToken().type == EventProbeLexer.TokenType.EVENT_PROBE_KEYWORD) {
                EventProbeDSL dsl = parseEventProbeDSL();
                if (dsl != null) {
                    result.add(dsl);
                }
            } else {
                // 跳过其他token
                position++;
            }
        }
        
        return result;
    }

    private EventProbeDSL parseEventProbeDSL() {
        // 匹配 EventProbe 关键字
        if (!match(EventProbeLexer.TokenType.EVENT_PROBE_KEYWORD)) {
            return null;
        }

        EventProbeDSL dsl = new EventProbeDSL();

        // 匹配 .on('事件名称')
        if (!match(EventProbeLexer.TokenType.DOT)) return null;
        if (!match(EventProbeLexer.TokenType.IDENTIFIER) || !"on".equals(currentToken(-1).value)) return null;
        if (!match(EventProbeLexer.TokenType.LEFT_PAREN)) return null;
        if (!match(EventProbeLexer.TokenType.STRING_LITERAL)) return null;
        dsl.setEventName(currentToken(-1).value);
        if (!match(EventProbeLexer.TokenType.RIGHT_PAREN)) return null;

        // 匹配 .at('位置')
        if (!match(EventProbeLexer.TokenType.DOT)) return null;
        if (!match(EventProbeLexer.TokenType.IDENTIFIER) || !"at".equals(currentToken(-1).value)) return null;
        if (!match(EventProbeLexer.TokenType.LEFT_PAREN)) return null;
        if (!match(EventProbeLexer.TokenType.STRING_LITERAL)) return null;
        dsl.setPosition(currentToken(-1).value);
        if (!match(EventProbeLexer.TokenType.RIGHT_PAREN)) return null;

        // 匹配 .inject(function() { 函数体 } 或 function name() { 函数体 })
        if (!match(EventProbeLexer.TokenType.DOT)) return null;
        if (!match(EventProbeLexer.TokenType.IDENTIFIER) || !"inject".equals(currentToken(-1).value)) return null;
        if (!match(EventProbeLexer.TokenType.LEFT_PAREN)) return null;
        
        // 解析函数声明
        String functionBody = parseFunctionDeclaration();
        if (functionBody == null) return null;
        
        dsl.setFunctionBody(functionBody);
        
        if (!match(EventProbeLexer.TokenType.RIGHT_PAREN)) return null;

        return dsl;
    }

    private String parseFunctionDeclaration() {
        if (!match(EventProbeLexer.TokenType.FUNCTION_KEYWORD)) return null;
        
        // 可选的函数名
        if (currentToken().type == EventProbeLexer.TokenType.IDENTIFIER) {
            match(EventProbeLexer.TokenType.IDENTIFIER);
        }
        
        if (!match(EventProbeLexer.TokenType.LEFT_PAREN)) return null;
        if (!match(EventProbeLexer.TokenType.RIGHT_PAREN)) return null;
        if (!match(EventProbeLexer.TokenType.LEFT_BRACE)) return null;
        
        // 提取函数体
        StringBuilder functionBody = new StringBuilder();
        int braceCount = 1;
        int startPos = position;
        
        while (position < tokens.size() && braceCount > 0) {
            EventProbeLexer.Token token = currentToken();
            if (token.type == EventProbeLexer.TokenType.LEFT_BRACE) {
                braceCount++;
            } else if (token.type == EventProbeLexer.TokenType.RIGHT_BRACE) {
                braceCount--;
                if (braceCount == 0) {
                    break;
                }
            }
            position++;
        }
        
        if (braceCount != 0) return null;
        
        // 获取函数体内容
        // 这里简化处理，实际应该重构原始代码
        if (!match(EventProbeLexer.TokenType.RIGHT_BRACE)) return null;
        
        return extractFunctionBody(startPos);
    }
    
    private String extractFunctionBody(int startPos) {
        if (startPos >= position - 1) return "";
        
        EventProbeLexer.Token startToken = tokens.get(startPos);
        EventProbeLexer.Token endToken = tokens.get(position - 1);
        
        // 简化处理，实际应该重构原始代码
        return "/* function body */";
    }

    private boolean match(EventProbeLexer.TokenType expectedType) {
        if (position < tokens.size() && currentToken().type == expectedType) {
            position++;
            return true;
        }
        return false;
    }

    private EventProbeLexer.Token currentToken() {
        if (position < tokens.size()) {
            return tokens.get(position);
        }
        return new EventProbeLexer.Token(EventProbeLexer.TokenType.EOF, "", position);
    }

    private EventProbeLexer.Token currentToken(int offset) {
        int pos = position + offset;
        if (pos >= 0 && pos < tokens.size()) {
            return tokens.get(pos);
        }
        return new EventProbeLexer.Token(EventProbeLexer.TokenType.EOF, "", pos);
    }
}