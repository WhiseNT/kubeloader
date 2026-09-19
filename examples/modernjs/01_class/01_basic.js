// ============================================
// 01 · class 基础：构造器 + 实例方法
// ============================================
// requires-transform
//
// KubeJS 场景：TooltipHelper / SetApi 这类工具类几乎都是 class 写的。
// Rhino 里没有 class 关键字（报 "identifier is a reserved word: class"），
// 只能靠 ModernJS 转成「函数 + prototype」。

class Counter {
    constructor(start) {
        this.value = start
    }

    add(n) {
        this.value += n
        return this
    }

    reset() {
        this.value = 0
    }
}

const c = new Counter(1)
c.add(2).add(3)

console.log('counter value =', c.value)

__check('构造器参数生效', c.value, 6)
__checkTrue('实例是自己类的实例', c instanceof Counter)
__check('new 出来的是对象', typeof c, 'object')

c.reset()
__check('同一个实例上再调方法', c.value, 0)

const d = new Counter(10)
c.add(1)
__check('两个实例互不影响', d.value, 10)
__check('两个实例互不影响（另一个）', c.value, 1)
