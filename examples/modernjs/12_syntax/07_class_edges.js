// ============================================
// 语法边角 07 · 类本身的边角
// ============================================
// requires-transform
//
// 契约：类头可以跨行（名字、extends、`{` 各占一行，extends 还能单独占一行），
// 静态访问器要挂到类本身而不是 prototype，字段初始值按书写顺序求值，
// 成员名与变量名都可以是 Unicode。

class
    基类
{
    constructor() {
        this.顺序 = []
        this.顺序.push('基类构造')
    }

    get 名字() {
        return '基类'
    }
}

class
    子类
        extends
            基类
{
    constructor() {
        super()
        this.顺序.push('子类构造')
    }

    get 名字() {
        return '子类(' + super.名字 + ')'
    }

    static get 单例() {
        if (!子类.实例) {
            子类.实例 = new 子类()
        }
        return 子类.实例
    }

    static set 单例(v) {
        子类.实例 = v
    }
}

const 实例 = new 子类()

__check('类头跨行 + 构造顺序', 实例.顺序.join(','), '基类构造,子类构造')
__check('子类 getter + super 取父类', 实例.名字, '子类(基类)')
__check('静态访问器不是同一个实例', 子类.单例 === 实例, 'false')
__check('静态访问器返回实例', 子类.单例 instanceof 子类, 'true')
__check('静态 setter 可覆盖', (子类.单例 = 实例, 子类.单例 === 实例), 'true')
