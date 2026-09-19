// ============================================
// 08 · 接收者是括号组 / 字面量时的展开：目前不支持
// ============================================
// expect-error: syntax error
//
// 写成 new X().m(...args) / f().m(...args) / 'x'.m(...args) 时，接收者不是
// 标识符或成员链。要保住 this 必须让接收者只求值一次（否则 new X() 会被构造两次），
// 那需要包一层 IIFE；现在的实现选择**不改写**。
//
// 为什么宁可报错：之前的实现会退化成 `接收者.m.apply(null, args)`，而非严格模式下
// apply(null) 的 this 是全局对象 —— 方法体里读 this.x 静默拿到 undefined（实测直接
// 算出 NaN），属于最危险的静默算错。现在变成一条明确的语法错误，至少不会算错值。
//
// 替代写法：先把接收者存进变量，再展开调用。
//   const c = new Calc(100);  c.add(...args)          ← 这样就行
//   const s = 'x';            s.concat(...words)       ← 这样也行

class Calc {
    constructor(b) {
        this.b = b
    }

    add(a, c) {
        return this.b + a + c
    }
}

const base = 'x'
const words = ['a', 'b']

__check('存进变量后展开调用是没问题的（对照）', base.concat(...words), 'xab')

__check('这一行不该被执行到', new Calc(100).add(...[1, 2]), 'never')
