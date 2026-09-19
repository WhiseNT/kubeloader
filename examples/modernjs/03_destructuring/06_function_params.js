// ============================================
// 06 · 函数形参里的解构默认值
// ============================================
// requires-transform
//
// 形参里的模式会被换成临时参数名，真正的绑定下沉到函数体开头。
// 这一步还和「默认参数」那一步挨着（都是解析签名），最容易互相踩。

function label({ name = 'anon' }) {
    return name
}

__check('形参解构默认值（缺省命中）', label({}), 'anon')
__check('形参解构默认值（不缺省）', label({ name: 'iron' }), 'iron')

function box([first = 9]) {
    return first
}

__check('形参里的数组模式（缺省）', box([]), 9)
__check('形参里的数组模式（有值）', box([3]), 3)

function two({ a = 1 }, { b = 2 }) {
    return a + b
}

__check('多个形参都是模式（都缺省）', two({}, {}), 3)
__check('多个形参都是模式（都给值）', two({ a: 10 }, { b: 20 }), 30)

function both({ x = 1 }, y) {
    return x + '/' + y
}

__check('模式 + 普通形参混用', both({}, 5), '1/5')

function noPattern(a, b) {
    return a + b
}

__check('同文件里的普通函数不受影响', noPattern(1, 2), 3)
