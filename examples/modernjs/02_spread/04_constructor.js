// ============================================
// 04 · 构造调用里的展开：new P(...args)
// ============================================
// requires-transform
//
// new 和普通调用不一样，apply 是套不上去的，只能用中间函数来借道。

class Point {
    constructor(x, y) {
        this.x = x
        this.y = y
    }

    sum() {
        return this.x + this.y
    }
}

const p = new Point(...[3, 4])

__check('new class 展开实参', p.sum(), 7)
__checkTrue('展开 new 出来的仍是实例', p instanceof Point)

function Pair(a, b) {
    this.a = a
    this.b = b
}

const q = new Pair(...[1, 2])

__check('new 普通构造函数展开', q.a + q.b, 3)

const args = [5]
const r = new Point(...args, 6)
__check('展开与固定实参混用', r.sum(), 11)
