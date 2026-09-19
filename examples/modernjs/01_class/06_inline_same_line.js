// ============================================
// 06 · class 写在同行 / 一个文件多个 class
// ============================================
// requires-transform
//
// 早期版本要求 class 必须独占一行行首。写惯现代 JS 的人不会那样写：
// 一行里放两个 class、字段和方法挤在一行、类收尾之后紧跟语句（ASI 隐患），
// 这些都得能转。

class A { m() { return 1 } } class B { m() { return 2 } }

const sum = new A().m() + new B().m()

__check('同一行两个 class', sum, 3)

class C { x = 5; get() { return this.x } }

__check('同行写了字段和实例方法', new C().get(), 5)

class D { m() { return 7 } } const d = new D()

__check('类收尾后紧跟语句', d.m(), 7)

class E {
    m() { return 'e' }
}
const e = new E()
__check('普通多行 class 作对照', e.m(), 'e')
