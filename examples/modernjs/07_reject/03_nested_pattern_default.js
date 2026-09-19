// ============================================
// 03 · 嵌套模式里的默认值：目前不支持
// ============================================
// expect-error: before function parameters
//
// 只做了「一层模式 + 普通标识符成员」的摊平。遇到嵌套（{ a: { b = 1 } }）时
// 转换器选择不动它，让 Rhino 报语法错误——响亮失败，不会静默算错值。
//
// 顺带说明：Rhino 对此报的是 "missing ( before function parameters"，
// 完全看不出跟解构有关，所以 KLScriptLoader 里专门补了一句解释。

const src = { a: {} }
const { a: { b = 1 } } = src

__check('这行不该被执行到', b, 'never')
