// ============================================
// 怪写法 07 · 合法但很怪的类成员名
// ============================================
// requires-transform
//
// 契约：成员名可以是保留字（属性名允许），访问器名、静态访问器都要照常生成。
// 其中「静态访问器」（static get x()）是单例写法里极常见的一种。

class Weird {
    get if() {
        return 'get-if'
    }

    get class() {
        return 'get-class'
    }

    set for(v) {
        this._for = v
    }

    get for() {
        return this._for
    }

    static get 面儿() {
        return Weird._面儿 === undefined ? 'static-get' : Weird._面儿
    }

    static set 面儿(v) {
        Weird._面儿 = v
    }

    static get() {
        return 'method-named-get'
    }

    normal() {
        return 1
    }

    toString() {
        return 'Weird!'
    }

    valueOf() {
        return 42
    }
}

const w = new Weird()

__check('访问器名是保留字 if', w.if, 'get-if')
__check('访问器名是保留字 class', w.class, 'get-class')

w.for = 7
__check('setter 名是保留字 for', w.for, 7)

__check('静态 getter', Weird.面儿, 'static-get')
Weird.面儿 = 'changed'
__check('静态 setter', Weird.面儿, 'changed')

__check('叫 get 的静态方法不是访问器', Weird.get(), 'method-named-get')
__check('普通方法', w.normal(), 1)
__check('toString 被覆盖', String(w), 'Weird!')
__check('valueOf 参与运算', w + 0, 42)
