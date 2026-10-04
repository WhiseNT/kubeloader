// ============================================
// 13-08 · 类表达式（const C = class {...}）→ 改成类声明
// ============================================
// requires-transform
//
// sweep 逐文件结果：`var C = class { m(){ return 1 } }` → UNSUPPORTED_SYNTAX。
// 转换器只认「类声明」（class X { ... } 写在行首），类表达式它不动。
//
// Rhino 能跑的版本：写成类声明；需要"按条件造不同类"的场景，
// 用声明 + 工厂函数返回实例。

class Vec2 {
    constructor(x, y) {
        this.x = x
        this.y = y
    }

    lengthSq() {
        return this.x * this.x + this.y * this.y
    }
}

function makeVec2(x, y) {
    return new Vec2(x, y)
}

const a = new Vec2(3, 4)
const b = makeVec2(1, 2)

__check('类声明能用', a.lengthSq(), 25)
__check('工厂函数返回实例', b.lengthSq(), 5)
__check('展开仍由转换器负责', [...[a.x, a.y]].join(','), '3,4')
