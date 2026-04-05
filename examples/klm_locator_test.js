// mixin(value = "server_scripts/klm_locator_test_target.js")

// ============================================
// KLM 定位表达式测试 - Mixin 声明文件
// ============================================

// 测试 1: 简单函数名（向后兼容）
KLM.type('FunctionDeclaration')
    .at('head')
    .in('testSimpleFunction')
    .inject(function() {
        console.log('[Test 1] 简单函数名注入 - 向后兼容')
    })

// 测试 2: 定位表达式 - 按函数名
KLM.type('FunctionDeclaration')
    .at('head')
    .in('function[name="testNamedFunction"]')
    .inject(function() {
        console.log('[Test 2] 定位表达式 - 按函数名')
    })

// 测试 3: 定位表达式 - 按函数名+参数数量
KLM.type('FunctionDeclaration')
    .at('head')
    .in('function[name="testParamFunction"][params="2"]')
    .inject(function() {
        console.log('[Test 3] 定位表达式 - 按函数名+参数数量')
    })

// 测试 4: 定位表达式 - 按索引选择（第二个同名函数）
KLM.type('FunctionDeclaration')
    .at('head')
    .in('function[name="testDuplicateFunction"][1]')
    .inject(function() {
        console.log('[Test 4] 定位表达式 - 索引选择器 (第二个)')
    })

// 测试 5: 定位表达式 - 负数索引（最后一个）
KLM.type('FunctionDeclaration')
    .at('tail')
    .in('function[name="testDuplicateFunction"][-1]')
    .inject(function() {
        console.log('[Test 5] 定位表达式 - 负数索引 (最后一个)')
    })

// 测试 6: 链式表达式 - 在函数内查找函数
KLM.type('FunctionDeclaration')
    .at('head')
    .in('function[name="testOuter"] > function[name="testInner"]')
    .inject(function() {
        console.log('[Test 6] 链式表达式 - 函数内查找函数')
    })

// 测试 7: 链式表达式 - 在函数内查找调用
KLM.type('FunctionDeclaration')
    .at('head')
    .in('function[name="testOuter"] > call[target="console.log"]')
    .inject(function() {
        console.log('[Test 7] 链式表达式 - 函数内查找调用')
    })

// 测试 8: 注入位置 - tail
KLM.type('FunctionDeclaration')
    .at('tail')
    .in('function[name="testTailFunction"]')
    .inject(function() {
        console.log('[Test 8] 注入位置 - tail')
    })

// 测试 9: 优先级测试
KLM.type('FunctionDeclaration')
    .at('head')
    .in('function[name="testPriorityFunction"]')
    .priority(10)
    .inject(function() {
        console.log('[Test 9] 优先级 10 - 应该先执行')
    })

KLM.type('FunctionDeclaration')
    .at('head')
    .in('function[name="testPriorityFunction"]')
    .priority(5)
    .inject(function() {
        console.log('[Test 9] 优先级 5 - 应该后执行')
    })

// 测试 10: 定位表达式 - 变量声明
KLM.type('FunctionDeclaration')
    .at('head')
    .in('function[name="testVariableFunction"]')
    .inject(function() {
        console.log('[Test 10] 定位表达式 - 变量声明函数')
    })
