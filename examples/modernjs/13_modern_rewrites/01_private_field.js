// ============================================
// 13-01 · 私有字段（#x）→ 下划线约定
// ============================================
// requires-transform
//
// 外部语料里很常见 `class C { #x = 1; get(){ return this.#x } }`。
// 实测这个 Rhino 分支连解析都过不了（`illegal character: #`），
// 而转换器不改写它 —— 脚本会以「语法错误」结束，不会静默算错。
//
// Rhino 能跑的版本：约定以下划线开头。注意这只是命名约定，
// 运行时仍然能从外部读到 —— 真正要保密就别放在对象上。
//
// 这个文件里的 class / 箭头 / 模板串都照常交给转换器处理。

class Counter {
    constructor() {
        this._value = 0
    }

    bump() {
        this._value += 1
        return this._value
    }
}

const counter = new Counter()
const bumps = [0, 0, 0].map(() => counter.bump())

__check('下划线字段能读能写', counter._value, 3)
__check('箭头里调方法', bumps.join(','), '1,2,3')
__check('模板串里取值', `value=${counter._value}`, 'value=3')
