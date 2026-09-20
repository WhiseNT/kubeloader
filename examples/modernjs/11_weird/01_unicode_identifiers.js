// ============================================
// 怪写法 01 · Unicode 标识符 / $ / _
// ============================================
// requires-transform
//
// 契约：标识符里出现非 ASCII 字母，不能影响任何一个转换步骤。
// 转换器判断「是不是标识符」用的是 Character.isLetter，对 Unicode 字母是成立的；
// 但类成员分类、解构目标校验、属性名加引号这些地方都得一致才行。

var 名字 = 1
var $dollar = 2
var _under = 3
var _$混合1 = 4

function 取两倍(参数) {
    return 参数 * 2
}

class 类 {
    constructor(初始) {
        this.值 = 初始
    }

    get 数目() {
        return this.值
    }

    set 数目(v) {
        this.值 = v
    }

    加(增量 = 1) {
        return this.值 + 增量
    }

    static 零() {
        return new 类(0)
    }
}

const { 中文键 = 5 } = {}
const [ 第一项 = 6 ] = []
const { 键: 改名后 = 7 } = {}
const 对象 = { [名字]: 8 }

__check('Unicode 变量', 名字, 1)
__check('$ 开头', $dollar, 2)
__check('_ 开头', _under, 3)
__check('$ 与 _ 混用', _$混合1, 4)
__check('Unicode 函数名与参数', 取两倍(3), 6)

const 实例 = new 类(10)
__check('Unicode 类与构造器', 实例.数目, 10)
__check('Unicode 方法 + 默认参数', 实例.加(), 11)
__check('Unicode getter / setter', (实例.数目 = 20, 实例.数目), 20)
__check('Unicode 静态方法', 类.零().数目, 0)

__check('Unicode 解构默认值', 中文键, 5)
__check('Unicode 数组模式默认值', 第一项, 6)
__check('Unicode 改名 + 默认值', 改名后, 7)
__check('Unicode 计算属性名', 对象[名字], 8)
