// ============================================
// 11 · 解构默认值出现在 class 方法里
// ============================================
// requires-transform
//
// 两个特性叠在一起：class 要转成 prototype，方法签名里的模式又要摊平进方法体。
// 转换顺序或注入位置稍有偏差，注入的语句就会跑到类外面去。

class Inventory {
    constructor(items) {
        this.items = items
    }

    firstOf({ fallback = 'empty' }) {
        return this.items.length > 0 ? this.items[0] : fallback
    }

    pick([first = 'none']) {
        return first
    }

    static describe({ name = 'inv', size = 0 }) {
        return name + ':' + size
    }

    get summary() {
        const { length = 0 } = this.items
        return 'n=' + length
    }
}

const inv = new Inventory(['a', 'b'])
const emptyInv = new Inventory([])

__check('方法形参解构默认值（有值）', inv.firstOf({}), 'a')
__check('方法形参解构默认值（缺省）', emptyInv.firstOf({}), 'empty')
__check('方法里的数组模式（缺省）', inv.pick([]), 'none')
__check('方法里的数组模式（有值）', inv.pick(['x']), 'x')
__check('静态方法里的解构默认值（缺省）', Inventory.describe({}), 'inv:0')
__check('静态方法里的解构默认值（给定）', Inventory.describe({ name: 'x', size: 3 }), 'x:3')
__check('getter 体里的解构', inv.summary, 'n=2')
