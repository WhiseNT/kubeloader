// ============================================
// 03 · 成员调用 / 方法调用里的展开（this 必须保住）
// ============================================
// requires-transform
//
// 最容易错的一种：o.m(...args) 如果被简单改成 o.m.apply(null, args)，
// this 就丢了。而非严格模式下 apply(null) 的 this 是**全局对象**，
// 方法体里读 this.xxx 会静默拿到 undefined（实测直接算出 NaN）——
// 属于「不报错但结果错」，所以在补上接收者与响亮失败之间，转换器选择了后者。
//
// 这里只放「接收者是标识符/成员链」的形式（转换器支持）；
// 接收者是括号组或字面量的形式（new X().m(...)、'x'.m(...)）见 07_reject/08。

const obj = {
    base: 10,
    add(a, b) {
        return this.base + a + b
    }
}

__check('对象方法展开：this 保住了', obj.add(...[1, 2]), 13)

class Calc {
    constructor(b) {
        this.b = b
    }

    add(a, c) {
        return this.b + a + c
    }
}

const calc = new Calc(100)
__check('class 实例方法展开：this 保住了', calc.add(...[1, 2]), 103)

const arr = [1, 5, 3]
__check('内建函数也能展开传参', Math.max(...arr), 5)

const helper = {
    join(sep, a, b) {
        return a + sep + b
    }
}
__check('成员链接收者', helper.join(...['-', 'a', 'b']), 'a-b')

const nested = {
    inner: {
        base: 1,
        sum(a, b) {
            return this.base + a + b
        }
    }
}
__check('多层成员链接收者', nested.inner.sum(...[2, 3]), 6)
