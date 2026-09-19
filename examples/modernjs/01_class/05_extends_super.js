// ============================================
// 05 · class 继承：extends + super
// ============================================
// requires-transform
//
// Rhino 里既没有 class 也没有 super 关键字，转换器要把 super(...) 与 super.m()
// 都改写成对父类的显式访问。

class Base {
    constructor(name) {
        this.name = name
    }

    describe() {
        return 'base:' + this.name
    }
}

class Child extends Base {
    constructor(name, extra) {
        super(name)
        this.extra = extra
    }

    describe() {
        return super.describe() + '+' + this.extra
    }

    onlyChild() {
        return 'child'
    }
}

const c = new Child('n', 'x')

__check('super(...) 调到了父类构造器', c.name, 'n')
__check('super.method() 能取到父类结果', c.describe(), 'base:n+x')
__checkTrue('instanceof 父类', c instanceof Base)
__checkTrue('instanceof 子类', c instanceof Child)
__check('子类自己的方法', c.onlyChild(), 'child')

const base = new Base('b')
__check('父类实例不受子类影响', base.describe(), 'base:b')
