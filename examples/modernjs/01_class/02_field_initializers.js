// ============================================
// 02 · class 字段初始值（含引用类型字段）
// ============================================
// requires-transform
//
// 字段初始值必须做到「每个实例各一份」——如果转换器把它塞到了 prototype 上，
// 那么所有实例会共享同一个数组、同一个对象，这是最容易悄悄出错的地方。

class Config {
    name = 'default'
    count = 0
    tags = []
    meta = {}

    constructor(name) {
        this.name = name
    }

    describe() {
        return this.name + '/' + this.count + '/' + this.tags.length
    }
}

const a = new Config('a')
const b = new Config('b')

a.tags.push('x')
a.meta.k = 1

console.log('a.describe() =', a.describe())

__check('字段初始值生效', a.name, 'a')
__check('构造器覆盖字段初始值', a.count, 0)
__check('describe 拼接', a.describe(), 'a/0/1')
__check('引用类型字段：另一个实例不受影响', b.tags.length, 0)
__check('引用类型字段：对象也不共享', String(b.meta.k), 'undefined')
__check('自己的还是自己的', a.tags.length, 1)
