// ============================================
// 丑写法 08 · 字符串 / 注释里全是「像代码」的片段
// ============================================
// requires-transform
//
// 契约：只有真代码才改写，字符串 / 模板串 / 注释里的一律原样。
// 这里把每个特性的「源码形态」都塞进文本里，专门骗那些按文本替换的实现。
//
// 注意下面注释里也写了 class / function…{ —— 之前正是这种注释会被切开，
// 插入的换行终止注释，后半段变成真代码。

const s1 = "class A { m() { return '}' } }"
const s2 = 'var { a = 1 } = x'
const s3 = "f(...args)"
const s4 = "1_000"
const s5 = `catch { }`
const s6 = "a ||= b"
const s7 = "function f(a = 1) { return a }"
const s8 = "{ [k]: 1 }"
// 注释：class B { m() { return 2 } }
// 注释：function g(b = 2) { return b }
/* 注释：const { c = 3 } = y */
/* 注释：return {p, q} */
/* 注释：f(...rest) 与 1_000 与 a ||= b */
const re = /class|function|\{|\}|\.\.\.|1_000/

class Real {
    m() {
        return 42
    }
}

const realSource = { a: 1, b: 2 }
const k = 'dyn'
const realComputed = { [k]: 7 }
const { v: realV = 5, w = 6 } = {}
const realSpread = [0, ...[1, 2]]
const realSugar = 1_000
let realAssign = null
realAssign ||= 3

function realReturn() {
    const p = 1
    const q = 2
    return { p, q }
}

__check('字符串里的 class 保持原样', s1, "class A { m() { return '}' } }")
__check('字符串里的解构保持原样', s2, 'var { a = 1 } = x')
__check('字符串里的展开保持原样', s3, 'f(...args)')
__check('字符串里的数字分隔符保持原样', s4, '1_000')
__check('模板串里的 catch 保持原样', s5, 'catch { }')
__check('字符串里的逻辑赋值保持原样', s6, 'a ||= b')
__check('字符串里的函数默认参数保持原样', s7, 'function f(a = 1) { return a }')
__check('字符串里的计算属性名保持原样', s8, '{ [k]: 1 }')

__check('真 class 仍然转换', new Real().m(), 42)
__check('真对象字面量不受影响', realSource.a + realSource.b, 3)
__check('真计算属性名仍然生效', realComputed.dyn, 7)
__check('真解构默认值仍然生效', realV + w, 11)
__check('真展开仍然生效', realSpread.join(','), '0,1,2')
__check('真数字分隔符仍然生效', realSugar, 1000)
__check('真逻辑赋值仍然生效', realAssign, 3)
__check('真 return 简写仍然展开', realReturn().p + realReturn().q, 3)
__check('正则里的关键词不受影响', String(re.test('class')), 'true')
