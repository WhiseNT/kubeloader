// ============================================
// 怪写法 10 · 字段初始值互相依赖（顺序敏感）
// ============================================
// requires-transform
//
// 契约：实例字段的初始值必须**按书写顺序**、在构造器里求值。
// 一旦转换器把它们挂到 prototype 上、或顺序打乱，结果就会静默变错。

class Order {
    a = 1
    b = this.a + 1
    c = this.method()
    d = [this.b, this.c].join('/')
    method() {
        return 10
    }
    e = this.d.length
}

const o = new Order()

__check('按书写顺序求值', o.b, 2)
__check('字段初始值可以调方法', o.c, 10)
__check('引用前面字段的结果', o.d, '2/10')
__check('继续往后依赖', o.e, 4)

class Two {
    first = []
    second = this.first.length
}
const t1 = new Two()
t1.first.push('x')
const t2 = new Two()

__check('引用类型字段不共享（第一个实例）', t1.second, 0)
__check('引用类型字段不共享（改过之后）', t1.first.length, 1)
__check('另一个实例不受影响', t2.first.length, 0)
