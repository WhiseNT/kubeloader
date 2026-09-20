// ============================================
// 语法边角 13 · 类的计算成员名与字符串成员名
// ============================================
// requires-transform
//
// 契约：成员名不一定是普通标识符 —— 计算名（[expr]）和字符串字面量（'x'）都合法，
// 三种路径都要覆盖：实例方法、访问器、静态成员。
//
// 拼成员访问时不能无条件加 `.`，拼 defineProperty 的属性名时也不能直接套引号，
// 否则引号嵌套会破掉、整个脚本变语法错；而分类时如果要求名字必须是标识符，
// 成员会被**静默丢掉**（运行时才报 Cannot find function xxx）。
//
// 来源：引外部语料（test262 的 computed-property-names/class/*）做合法性扫描时发现，
// 本地语料此前只覆盖了对象字面量的计算键，类成员这一支完全没测过。

const 键 = 'dyn'

class 成员名 {
    constructor() {
        this.值 = 1
    }

    取() {
        return this.值
    }

    [键]() {
        return 'computed'
    }

    [键 + '后缀']() {
        return 'computed-expr'
    }

    '字面名'() {
        return 'literal'
    }

    get [键 + '值']() {
        return this.值 * 10
    }

    set [键 + '值'](v) {
        this.值 = v
    }

    static [键 + '静']() {
        return 'static-computed'
    }

    [键 + '字段'] = 7
    ['字面字段'] = 8
    static ['静字段'] = 5
    static [键 + '静字段'] = 6
}

const 实例 = new 成员名()

__check('普通方法与字段', 实例.取(), 1)
__check('计算名方法（变量）', 实例[键](), 'computed')
__check('计算名方法（表达式）', 实例[键 + '后缀'](), 'computed-expr')
__check('字符串成员名', 实例['字面名'](), 'literal')
__check('计算名 getter', 实例[键 + '值'], 10)
__check('计算名 setter', (实例[键 + '值'] = 3, 实例.取()), 3)
__check('静态计算名方法', 成员名[键 + '静'](), 'static-computed')
__check('计算名实例字段', 实例[键 + '字段'], 7)
__check('字符串名实例字段', 实例['字面字段'], 8)
__check('字符串名静态字段', 成员名['静字段'], 5)
__check('计算名静态字段', 成员名[键 + '静字段'], 6)
