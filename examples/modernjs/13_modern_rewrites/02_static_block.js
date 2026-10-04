// ============================================
// 13-02 · 静态块（static {}）→ 构造函数里初始化
// ============================================
// requires-transform
//
// `class C { static tags = ['a']; static { C.tags.push('b') } }` 这种写法
// 实测过不了这个 Rhino（`missing name after . operator`）。
//
// Rhino 能跑的版本：把初始化放进构造函数（或一个显式的 init 方法）。

const defaults = ['a']

class Registry {
    constructor() {
        this.tags = [...defaults]
    }

    addTag(name) {
        this.tags.push(name)
        return this.tags.length
    }
}

const registry = new Registry()
const size = registry.addTag('b')

__check('构造函数里做初始化', registry.tags.join(','), 'a,b')
__check('addTag 返回长度', size, 2)
__check('不会污染默认值', defaults.join(','), 'a')
