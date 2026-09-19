// ============================================
// 08 · class 体里的花括号不能被字符串/注释/模板串骗到
// ============================================
// requires-transform
//
// 类体的结束位置是按花括号配对找的。如果只是「数字符」而不看词法，
// 那么 return "}" 里的那个 } 会把类体提前截断，粘出一堆非法语句。

class A {
    m() { return '}' }
}

class B {
    m() { return '{' }
}

class C {
    /* } */
    m() { return 5 }
}

class D {
    m() { return `}` }
}

class E {
    m() { return "class Fq { m() {} }" }
}

class G {
    get x() { return '}' }
}

class H {
    m() {
        const s = '}{'
        return s.length
    }
}

__check('方法返回含 } 的字符串', new A().m(), '}')
__check('方法返回含 { 的字符串', new B().m(), '{')
__check('类体块注释里的 }', new C().m(), 5)
__check('模板串里的 }', new D().m(), '}')
__check('字符串里的 class', new E().m(), 'class Fq { m() {} }')
__check('getter 体里的 }', new G().x, '}')
__check('方法体里混合 }{ 的字符串', new H().m(), 2)
