// ============================================
// 04 · class 访问器 get / set
// ============================================
// requires-transform
//
// KubeJS 里用 getter 暴露「计算属性」很常见（比如 tooltip 的组装文本）。
// 注意类体里 getter 的方法体也带花括号，很容易被「按括号配对」的写法截断。

class Temperature {
    constructor(celsius) {
        this._c = celsius
    }

    get celsius() {
        return this._c
    }

    set celsius(v) {
        this._c = v
    }

    get fahrenheit() {
        return this._c * 9 / 5 + 32
    }

    set fahrenheit(v) {
        this._c = (v - 32) * 5 / 9
    }
}

const t = new Temperature(0)

__check('getter', t.celsius, 0)
__check('派生 getter', t.fahrenheit, 32)

t.celsius = 100
__check('setter 改了内部状态', t.fahrenheit, 212)

t.fahrenheit = 212
__check('setter 反向换算', t.celsius, 100)
