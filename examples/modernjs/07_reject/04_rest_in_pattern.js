// ============================================
// 04 · 模式里的 rest（[a, ...r]）：Rhino 不支持
// ============================================
// expect-error: syntax error
//
// 注意这和「函数剩余参数 function f(a, ...rest)」不是一回事：
// 前者是解构模式里的 rest，Rhino 直接语法错误；后者由转换器改写成
// slice.call(arguments) 后可用。

const arr = [1, 2, 3]
const [head, ...tail] = arr

__check('这行不该被执行到', head, 'never')
