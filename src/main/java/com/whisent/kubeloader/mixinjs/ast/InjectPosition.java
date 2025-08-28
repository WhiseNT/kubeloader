package com.whisent.kubeloader.mixinjs.ast;

public enum InjectPosition {
    BEFORE,     // 在目标位置之前注入
    AFTER,      // 在目标位置之后注入
    REPLACE,    // 替换目标位置
    REMOVE,     // 移除目标位置
    HEAD,       // 在函数开头注入
    TAIL,       // 在函数末尾注入
    INVOKE      // 在函数调用时注入
}