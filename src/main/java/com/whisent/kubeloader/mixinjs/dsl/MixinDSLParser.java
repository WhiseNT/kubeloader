package com.whisent.kubeloader.mixinjs.dsl;

import java.util.List;

/**
 * 解析Mixin DSL语法的类
 * 支持如下DSL语法:
 * Mixin.type('FunctionDecleration')
 *      .at('head')
 *      .in('onTest')
 *      .inject(function onTest() {
 *          console.log('ts.js')
 *      })
 */
public class MixinDSLParser {
    
    /**
     * 解析Mixin DSL语法
     * 
     * @param source DSL源代码
     * @return MixinDSL对象列表
     */
    public static List<MixinDSL> parse(String source) {
        //System.out.println("开始使用Lexer和Parser解析DSL代码: " + source);
        
        // 使用Lexer进行词法分析
        MixinDSLLexer lexer = new MixinDSLLexer(source);
        List<MixinDSLLexer.Token> tokens = lexer.tokenize();
        
        //System.out.println("词法分析完成，生成的token数量: " + (tokens.size() - 1)); // 不包括EOF
        for (MixinDSLLexer.Token token : tokens) {
            if (token.type != MixinDSLLexer.TokenType.EOF) {
                System.out.println("Token: " + token);
            }
        }
        
        // 使用Parser进行语法分析
        MixinDSLParserImpl parser = new MixinDSLParserImpl(tokens);
        List<MixinDSL> result = parser.parse();
        
        //System.out.println("语法分析完成，解析到 " + result.size() + " 个MixinDSL对象");
        return result;
    }
}