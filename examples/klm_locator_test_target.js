// ============================================
// KLM 定位表达式测试 - 目标文件
// 此文件会被上面的 mixin 声明文件注入代码
// ============================================

// 测试 1: 简单函数
function testSimpleFunction() {
    console.log('testSimpleFunction - 原始代码')
}

// 测试 2: 命名函数
function testNamedFunction() {
    console.log('testNamedFunction - 原始代码')
}

// 测试 3: 带参数的函数
function testParamFunction(a, b) {
    console.log('testParamFunction - 参数:', a, b)
}

// 测试 4 & 5: 同名函数（测试索引选择器）
function testDuplicateFunction() {
    console.log('testDuplicateFunction - 第一个')
}

function testDuplicateFunction() {
    console.log('testDuplicateFunction - 第二个')
}

// 测试 6 & 7: 嵌套函数（测试链式表达式）
function testOuter() {
    console.log('testOuter - 外部函数')
    
    function testInner() {
        console.log('testInner - 内部函数')
    }
    
    console.log('testOuter - 调用 console.log')
}

// 测试 8: tail 注入测试
function testTailFunction() {
    console.log('testTailFunction - 原始代码')
}

// 测试 9: 优先级测试
function testPriorityFunction() {
    console.log('testPriorityFunction - 原始代码')
}

// 测试 10: 变量声明函数
function testVariableFunction() {
    console.log('testVariableFunction - 原始代码')
}

// ============================================
// 运行所有测试
// ============================================
function runAllTests() {
    console.log('=== KLM 定位表达式测试开始 ===')
    console.log('')
    
    testSimpleFunction()
    console.log('')
    
    testNamedFunction()
    console.log('')
    
    testParamFunction('hello', 'world')
    console.log('')
    
    testDuplicateFunction()
    console.log('')
    
    testTailFunction()
    console.log('')
    
    testPriorityFunction()
    console.log('')
    
    testVariableFunction()
    console.log('')
    
    testOuter()
    console.log('')
    
    console.log('=== KLM 定位表达式测试结束 ===')
}
