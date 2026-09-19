// ============================================
// 丑写法 11 · 深层嵌套
// ============================================
// requires-transform
//
// 契约：嵌套再深，边界（类体、函数体、块）也不能找错。
// 这里把 class 套在函数里、函数套在方法里、解构套在最里层。

function outer() {
    class Middle {
        make() {
            function inner() {
                const { deep = 3 } = {}
                return { deep }
            }
            return inner()
        }
    }
    return new Middle().make().deep
}

__check('函数 → 类 → 方法 → 函数 → 解构', outer(), 3)

const config = {
    groups: [
        { name: 'a', items: [...[1, 2]] },
        { name: 'b', items: [...[3]] }
    ],
    lookup: { [('k' + 1)]: { [('k' + 2)]: 5 } }
}

__check('嵌套里的展开', config.groups[0].items.length + config.groups[1].items.length, 3)
__check('嵌套里的计算属性名', config.lookup.k1.k2, 5)

class Level1 {
    constructor() {
        this.level2 = {
            level3: {
                level4: function ({ n = 6 }) {
                    return n
                }
            }
        }
    }

    deep() {
        const { level2 } = this
        const { level3 } = level2
        const { level4 } = level3
        return level4({})
    }
}

__check('类里层层解构 + 形参默认值', new Level1().deep(), 6)

const nested = [[...[[1, 2]]]].map(function (x) {
    return x.length
}).join(',')

__check('多层数组里的展开', nested, '1')
