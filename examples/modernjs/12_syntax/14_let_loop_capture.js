// ============================================
// 语法边角 14 · let 循环里闭包捕获「当轮」的值
// ============================================
//
// 契约：标准 JS 里 `for (let i ...)` 每轮一个新绑定，闭包各自记住当轮的值。
// 这个 Rhino 分支只建一个绑定（实测全读到 3,3,3），所以转换器会补一层参数快照
// （见 ModernJSLetIterationConverter）。本文件用断言把「补对了」钉住。
//
// 注意：这里**不能**标 requires-transform —— 原文本来就能跑（只是算错值），
// 标了会自相矛盾（那一条要求原文跑不通）。

var 逃逸 = []
for (let i = 0; i < 3; i++) {
    逃逸.push(function () { return i })
}

var 同轮 = []
for (let i = 0; i < 3; i++) {
    var 取当前 = function () { return i }
    同轮.push(取当前())
}

var 共享 = []
for (var j = 0; j < 3; j++) {
    共享.push(function () { return j })
}

var 数组 = [7, 8, 9]
var 按下标 = []
for (let k = 0; k < 数组.length; k++) {
    按下标.push(function () { return 数组[k] })
}

__check('let 循环 + 逃逸闭包（标准 0,1,2）',
    逃逸[0]() + ',' + 逃逸[1]() + ',' + 逃逸[2](), '0,1,2')
__check('let 循环 + 同轮内调用', 同轮.join(','), '0,1,2')
__check('var 循环保持共享（标准 3,3,3）',
    共享[0]() + ',' + 共享[1]() + ',' + 共享[2](), '3,3,3')
__check('let 循环 + 用下标访问', 按下标[0]() + ',' + 按下标[2](), '7,9')

// for-of / for-in 也是「每轮一个新绑定」，同样会被补偿。
// 注意 var 的那两条：var 是函数作用域、本来就该共享，转换器必须原样放过。
var 遍历值 = []
for (const x of [1, 2]) {
    遍历值.push(function () { return x })
}

var 遍历键 = []
for (const kk in { a: 1, b: 2 }) {
    遍历键.push(function () { return kk })
}

var 共享遍历 = []
for (var v of [1, 2]) {
    共享遍历.push(function () { return v })
}

var 关键字串 = []
for (const s of ['of', 'in']) {
    关键字串.push(function () { return s })
}

__check('for-of 捕获当轮的值', 遍历值[0]() + ',' + 遍历值[1](), '1,2')
__check('for-in 捕获当轮的值', 遍历键.map(function (f) { return f() }).sort().join(','), 'a,b')
__check('for-of + var 保持共享（标准 2,2）',
    共享遍历[0]() + ',' + 共享遍历[1](), '2,2')
__check('被遍历的字符串里含 of/in 关键字', 关键字串[0]() + ',' + 关键字串[1](), 'of,in')

// 箭头函数同样会被补偿 —— 但只处理能「确定边界」的形态：
//   块体            () => { return i }
//   单行表达式体    () => x / () => obj[k] / () => f(a, b)
// 下面这些故意不动（宁可少修，也不猜错边界生成语法错误）：
//   含运算符     () => i + 1      —— 实测仍是共享值（4,4,4）
//   对象字面量体 () => ({ v: i })
//   async 箭头   async () => i    —— 这个 Rhino 分支本身就解析不了（missing formal parameter）
var 箭头 = []
for (const x of [1, 2]) {
    箭头.push(() => x)
}

var 箭头块体 = []
for (let i = 0; i < 3; i++) {
    箭头块体.push(() => { return i })
}

var 箭头下标 = []
var 源 = [7, 8, 9]
for (let i = 0; i < 源.length; i++) {
    箭头下标.push(() => 源[i])
}

var 套一层 = []
for (let i = 0; i < 2; i++) {
    套一层.push(function () { return () => i })
}

__check('for-of + 箭头（最常见的组合）', 箭头[0]() + ',' + 箭头[1](), '1,2')
__check('块体箭头捕获当轮的值', 箭头块体[0]() + ',' + 箭头块体[2](), '0,2')
__check('箭头 + 下标访问', 箭头下标[0]() + ',' + 箭头下标[2](), '7,9')
__check('函数里套箭头（只包一层，不需要包两次）',
    套一层[0]()() + ',' + 套一层[1]()(), '0,1')

// 嵌套：外层和内层的循环变量都要按「当轮」快照。
// 关键在「层间」那组 —— 外层体里、内层循环外的闭包只能拿到外层的名字。
// 如果把整条嵌套链的名字并起来，它会被塞进内层的 j，而那里 j 还不存在 → ReferenceError。
var 嵌套 = []
for (let i = 0; i < 2; i++) {
    for (let j = 0; j < 2; j++) {
        嵌套.push(function () { return i + ',' + j })
    }
}

var 层间 = []
var 内层闭包 = []
for (let i = 0; i < 2; i++) {
    层间.push(function () { return i })
    for (let j = 0; j < 2; j++) {
        内层闭包.push(function () { return j })
    }
}

var 先后 = []
for (let i = 0; i < 2; i++) { 先后.push(function () { return i }) }
for (let k = 5; k < 7; k++) { 先后.push(function () { return k }) }

var 三层 = []
for (let i = 0; i < 2; i++) {
    for (let j = 0; j < 2; j++) {
        for (let k = 0; k < 2; k++) {
            三层.push(() => { return i + '' + j + '' + k })
        }
    }
}

__check('嵌套 for：外层与内层都取当轮值',
    嵌套[0]() + '|' + 嵌套[1]() + '|' + 嵌套[2]() + '|' + 嵌套[3](), '0,0|0,1|1,0|1,1')
__check('层间的闭包只拿外层变量', 层间[0]() + ',' + 层间[1](), '0,1')
__check('内层闭包', 内层闭包[0]() + ',' + 内层闭包[1](), '0,1')
__check('两条先后循环（栈要弹干净）',
    先后[0]() + ',' + 先后[1]() + ',' + 先后[2]() + ',' + 先后[3](), '0,1,5,6')
__check('三层嵌套 + 箭头（块体）', 三层[0]() + ',' + 三层[7](), '000,111')

// for-of / for-in 的绑定模式：支持单个标识符、[a, b]、{v}、{k: local}。
// 带默认值、rest、嵌套模式、计算属性、字符串键的一律不动（宁可少修）。
var 数组解构 = []
for (const [a, b] of [[1, 2], [3, 4]]) {
    数组解构.push(function () { return a + ',' + b })
}

var 对象解构 = []
for (const { v } of [{ v: 1 }, { v: 2 }]) {
    对象解构.push(function () { return v })
}

var 改名解构 = []
for (const { v: 本地 } of [{ v: 1 }, { v: 2 }]) {
    改名解构.push(function () { return 本地 })
}

__check('for-of + 数组解构', 数组解构[0]() + '|' + 数组解构[1](), '1,2|3,4')
__check('for-of + 对象解构（简写）', 对象解构[0]() + ',' + 对象解构[1](), '1,2')
__check('for-of + 对象解构（改名）', 改名解构[0]() + ',' + 改名解构[1](), '1,2')
