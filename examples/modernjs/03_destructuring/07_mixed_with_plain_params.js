// ============================================
// 07 · 普通默认参数与解构默认参数共存
// ============================================
// requires-transform
//
// 一个签名里可能既有 `count = 1` 这种普通默认参数，又有 `{ n = 3 }` 这样的模式。
// 解析签名的两套逻辑挨在一起，很容易把模式的 = 当参数默认值切坏。

function build(id, { trim = false }, count = 1) {
    return id + '/' + trim + '/' + count
}

__check('三种形参共存：只给必填', build('a', {}), 'a/false/1')
__check('三种形参共存：模式有值', build('a', { trim: true }), 'a/true/1')
__check('三种形参共存：全给定', build('a', {}, 5), 'a/false/5')

function plainFirst(a = 0, { b = 1 }) {
    return a + '|' + b
}

__check('普通默认参数在前（缺省）', plainFirst(undefined, {}), '0|1')
__check('普通默认参数在前（给定）', plainFirst(7, { b: 8 }), '7|8')

function patternFirst({ b = 1 }, a = 0) {
    return a + '|' + b
}

__check('模式在前、普通默认参数在后（缺省）', patternFirst({}), '0|1')
__check('模式在前、普通默认参数在后（给定）', patternFirst({ b: 5 }, 9), '9|5')
