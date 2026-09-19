// ============================================
// 07 · class 出现在函数体 / 方法体里（嵌套）
// ============================================
// requires-transform
//
// KubeJS 脚本里经常「在函数里就地定义一个 helper class」。
// 这类写法会因为缩进、周围代码的干扰而更容易出错。

function makeFromFunctionBody() {
    class Inner {
        m() { return 7 }
    }
    return new Inner().m()
}

__check('函数体内的 class', makeFromFunctionBody(), 7)

class Outer {
    makeFromMethodBody() {
        class Inner {
            m() { return 8 }
        }
        return new Inner().m()
    }
}

__check('方法体内的 class', new Outer().makeFromMethodBody(), 8)

class Holder {
    m() {
        class Deep { }
        return new Deep() instanceof Object
    }
}

__check('嵌套 class instanceof Object', new Holder().m(), 'true')

function twice() {
    class N { constructor(v) { this.v = v } get() { return this.v * 2 } }
    return new N(3).get() + new N(4).get()
}

__check('函数体内 class 可多次实例化', twice(), 14)
