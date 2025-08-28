package com.whisent.kubeloader.mixinjs.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MixinDSLLexer {
    public enum TokenType {
        MIXIN_KEYWORD,      // Mixin
        DOT,                // .
        IDENTIFIER,         // type, at, in, inject等标识符
        STRING_LITERAL,     // 'FunctionDeclaration' 等字符串字面量
        LEFT_PAREN,         // (
        RIGHT_PAREN,        // )
        LEFT_BRACE,         // {
        RIGHT_BRACE,        // }
        FUNCTION_KEYWORD,   // function关键字
        COMMA,              // ,
        WHITESPACE,         // 空格、换行等空白字符
        EOF,                // 文件结束符
        UNKNOWN             // 未知token
    }

    public static class Token {
        public final TokenType type;
        public final String value;
        public final int position;

        public Token(TokenType type, String value, int position) {
            this.type = type;
            this.value = value;
            this.position = position;
        }

        @Override
        public String toString() {
            return "Token{" +
                    "type=" + type +
                    ", value='" + value + '\'' +
                    ", position=" + position +
                    '}';
        }
    }

    private final String input;
    private int position;

    public MixinDSLLexer(String input) {
        this.input = input;
        this.position = 0;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (position < input.length()) {
            Token token = nextToken();
            if (token.type != TokenType.WHITESPACE) { // 跳过空白字符
                tokens.add(token);
            }
        }
        tokens.add(new Token(TokenType.EOF, "", position));
        return tokens;
    }

    private Token nextToken() {
        if (position >= input.length()) {
            return new Token(TokenType.EOF, "", position);
        }

        char ch = input.charAt(position);
        int startPos = position;

        // 处理单字符token
        switch (ch) {
            case '.':
                position++;
                return new Token(TokenType.DOT, ".", startPos);
            case '(':
                position++;
                return new Token(TokenType.LEFT_PAREN, "(", startPos);
            case ')':
                position++;
                return new Token(TokenType.RIGHT_PAREN, ")", startPos);
            case '{':
                position++;
                return new Token(TokenType.LEFT_BRACE, "{", startPos);
            case '}':
                position++;
                return new Token(TokenType.RIGHT_BRACE, "}", startPos);
            case ',':
                position++;
                return new Token(TokenType.COMMA, ",", startPos);
            case '\'':
                return readStringLiteral(startPos);
            case ' ':
            case '\t':
            case '\r':
            case '\n':
                return readWhitespace(startPos);
        }

        // 处理关键字和标识符
        if (Character.isLetter(ch) || ch == '_') {
            return readIdentifierOrKeyword(startPos);
        }

        // 未知字符
        position++;
        return new Token(TokenType.UNKNOWN, String.valueOf(ch), startPos);
    }

    private Token readStringLiteral(int startPos) {
        StringBuilder sb = new StringBuilder();
        position++; // 跳过开始的引号

        while (position < input.length() && input.charAt(position) != '\'') {
            sb.append(input.charAt(position));
            position++;
        }

        if (position < input.length()) {
            position++; // 跳过结束的引号
        }

        return new Token(TokenType.STRING_LITERAL, sb.toString(), startPos);
    }

    private Token readWhitespace(int startPos) {
        StringBuilder sb = new StringBuilder();
        while (position < input.length() && 
               (input.charAt(position) == ' ' || 
                input.charAt(position) == '\t' || 
                input.charAt(position) == '\r' || 
                input.charAt(position) == '\n')) {
            sb.append(input.charAt(position));
            position++;
        }
        return new Token(TokenType.WHITESPACE, sb.toString(), startPos);
    }

    private Token readIdentifierOrKeyword(int startPos) {
        StringBuilder sb = new StringBuilder();
        while (position < input.length() && 
               (Character.isLetterOrDigit(input.charAt(position)) || 
                input.charAt(position) == '_')) {
            sb.append(input.charAt(position));
            position++;
        }

        String identifier = sb.toString();
        
        // 检查是否为关键字
        if ("Mixin".equals(identifier)) {
            return new Token(TokenType.MIXIN_KEYWORD, identifier, startPos);
        } else if ("function".equals(identifier)) {
            return new Token(TokenType.FUNCTION_KEYWORD, identifier, startPos);
        } else {
            return new Token(TokenType.IDENTIFIER, identifier, startPos);
        }
    }
}