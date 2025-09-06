package com.whisent.kubeloader.mixinjs.dsl;

import com.whisent.kubeloader.Kubeloader;

import java.util.ArrayList;
import java.util.List;

public class MixinDSLLexer {
    public enum TokenType {
        MIXIN_KEYWORD,      // Mixin
        DOT,                // .
        IDENTIFIER,         // type, at, in, inject等标识符
        STRING_LITERAL,     // 'FunctionDeclaration' 等字符串字面量
        NUMBER_LITERAL,     // 数字字面量
        LEFT_PAREN,         // (
        RIGHT_PAREN,        // )
        LEFT_BRACE,         // {
        RIGHT_BRACE,        // }
        FUNCTION_KEYWORD,   // function关键字
        COMMA,              // ,
        WHITESPACE,         // 空格、制表符、回车等空白字符
        NEWLINE,            // 换行符
        COMMENT,            // 注释
        EOF,                // 文件结束符
        UNKNOWN             // 未知token
    }

    public static class Token {
        public final TokenType type;
        public final String value;
        public final String originalValue; // 保留原始值（包括引号）
        public final int position;

        public Token(TokenType type, String value, int position) {
            this.type = type;
            this.value = value;
            this.originalValue = value;
            this.position = position;
        }

        public Token(TokenType type, String value, String originalValue, int position) {
            this.type = type;
            this.value = value;
            this.originalValue = originalValue;
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
                return readStringLiteral('\'', startPos);
            case '"':
                return readStringLiteral('"', startPos);
            case '\n':
                position++;
                return new Token(TokenType.NEWLINE, "\n", startPos);
            case ' ':
            case '\t':
            case '\r':
                return readWhitespace(startPos);
            case '/':
                // 检查是否是注释
                if (position + 1 < input.length()) {
                    char nextCh = input.charAt(position + 1);
                    if (nextCh == '/') {
                        return readSingleLineComment(startPos);
                    } else if (nextCh == '*') {
                        return readMultiLineComment(startPos);
                    }
                }
                break;
        }

        // 处理数字
        if (Character.isDigit(ch)) {
            return readNumberLiteral(startPos);
        }

        // 处理关键字和标识符
        if (Character.isLetter(ch) || ch == '_') {
            return readIdentifierOrKeyword(startPos);
        }

        // 未知字符
        position++;
        return new Token(TokenType.UNKNOWN, String.valueOf(ch), startPos);
    }

    private Token readStringLiteral(char quoteChar, int startPos) {
        StringBuilder sb = new StringBuilder();
        sb.append(quoteChar); // 添加开始引号到结果中
        position++; // 跳过开始的引号

        while (position < input.length() && input.charAt(position) != quoteChar) {
            sb.append(input.charAt(position));
            position++;
        }

        if (position < input.length()) {
            sb.append(quoteChar); // 添加结束引号到结果中
            position++; // 跳过结束的引号
        }

        // 只返回引号内的内容，不包含引号本身（用于比较）
        String value = sb.substring(1, sb.length() - 1);
        // 保留完整的原始值（包括引号）用于代码重建
        String originalValue = sb.toString();
        return new Token(TokenType.STRING_LITERAL, value, originalValue, startPos);
    }

    private Token readSingleLineComment(int startPos) {
        StringBuilder sb = new StringBuilder();
        // 添加 "//" 到结果中
        sb.append("//");
        position += 2; // 跳过 "//"

        // 读取直到行尾
        while (position < input.length() && input.charAt(position) != '\n') {
            sb.append(input.charAt(position));
            position++;
        }

        return new Token(TokenType.COMMENT, sb.toString(), startPos);
    }

    private Token readMultiLineComment(int startPos) {
        StringBuilder sb = new StringBuilder();
        // 添加 "/*" 到结果中
        sb.append("/*");
        position += 2; // 跳过 "/*"

        // 读取直到遇到 "*/"
        while (position + 1 < input.length()) {
            char ch = input.charAt(position);
            sb.append(ch);
            if (ch == '*' && input.charAt(position + 1) == '/') {
                sb.append('/');
                position += 2; // 跳过 "*/"
                break;
            }
            position++;
        }

        return new Token(TokenType.COMMENT, sb.toString(), startPos);
    }

    private Token readWhitespace(int startPos) {
        StringBuilder sb = new StringBuilder();
        while (position < input.length() && 
               (input.charAt(position) == ' ' || 
                input.charAt(position) == '\t' || 
                input.charAt(position) == '\r')) {
            sb.append(input.charAt(position));
            position++;
        }
        return new Token(TokenType.WHITESPACE, sb.toString(), startPos);
    }

    private Token readNumberLiteral(int startPos) {
        StringBuilder sb = new StringBuilder();
        while (position < input.length() && Character.isDigit(input.charAt(position))) {
            sb.append(input.charAt(position));
            position++;
        }
        return new Token(TokenType.NUMBER_LITERAL, sb.toString(), startPos);
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
        if (Kubeloader.MIXIN_IDENTIFIER.equals(identifier)) {
            return new Token(TokenType.MIXIN_KEYWORD, identifier, startPos);
        } else if ("function".equals(identifier)) {
            return new Token(TokenType.FUNCTION_KEYWORD, identifier, startPos);
        } else {
            return new Token(TokenType.IDENTIFIER, identifier, startPos);
        }
    }
}