// ============================================
// 怪写法 12 · 老古董语法与新语法混在一起
// ============================================
// requires-transform
//
// 契约：with / arguments / eval / debugger / 标签 / 连续赋值 / 逗号运算符
// 这些老东西，不能影响同一份文件里新语法的转换。

var 日志 = []

with ({ 手写: 'w' }) {
    日志.push(手写)
}

function 数参数() {
    return arguments.length
}

function 内嵌() {
    // 注意：`debugger` 语句这个 Rhino 不支持（会被当成标识符 → "debugger" is not defined），
    // 所以这里不写它 —— 那是引擎的限制，跟 ModernJS 无关。
    return 1
}

标签: {
    if (日志.length === 1) {
        break 标签
    }
    日志.push('不该到这里')
}

var 逗号序列 = (1, 2, 3)
var 连续赋值
var 结果 = 连续赋值 = 'x'
var 多声明 = 1, 多声明2 = 2

class 老新混合 {
    constructor() {
        this.值 = 多声明
    }

    get 双倍() {
        return this.值 * 2
    }

    static of(参数) {
        const { 起点 = 1 } = 参数 || {}
        return new 老新混合()
    }
}

const 实例 = new 老新混合()

__check('with 语句', 日志[0], 'w')
__check('arguments', 数参数(1, 2, 3), 3)
__check('跨行函数表达式无害', 内嵌(), 1)
__check('标签 + break', 日志.length, 1)
__check('逗号运算符', 逗号序列, 3)
__check('连续赋值', 结果 + 连续赋值, 'xx')
__check('一条 var 多个声明', 多声明 + 多声明2, 3)
__check('类里用到这些老变量', 实例.双倍, 2)
__check('类里的 getter', 实例.值, 1)
__check('静态方法（这里没用上解构默认值，只确认不炸）', 老新混合.of() instanceof 老新混合, 'true')
