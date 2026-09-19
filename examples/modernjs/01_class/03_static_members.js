// ============================================
// 03 · class 静态成员
// ============================================
// requires-transform
//
// 静态字段的值里可以带括号、可以带运算（static BASE = 2 * 3），
// 静态方法里可以引用别的静态成员，也可以用默认参数（默认值取静态字段）。

class Registry {
    static COUNT = 0
    static BASE = 2 * 3
    static NAMES = {}

    static make(name) {
        Registry.COUNT += 1
        Registry.NAMES[name] = true
        return name
    }

    static has(name) {
        return Registry.NAMES[name] === true
    }

    static withDefault(a, b = Registry.BASE) {
        return a + b
    }
}

const first = Registry.make('iron')
Registry.make('gold')

__check('静态方法返回值', first, 'iron')
__check('静态字段能累加', Registry.COUNT, 2)
__check('静态字段是对象', Registry.NAMES.iron, 'true')
__checkTrue('静态方法读静态字段', Registry.has('gold'))
__check('带括号的静态字段值', Registry.BASE, 6)
__check('静态方法里的默认参数取静态字段', Registry.withDefault(1), 7)
__check('静态方法里的默认参数可覆盖', Registry.withDefault(1, 2), 3)
