// ============================================
// 03 · String.prototype.at / replaceAll / matchAll
// ============================================
// 不标 requires-transform（补丁由运行器先装，同线上 mixin）。
//
// replaceAll 有个坑：替换串里的 $& 是「匹配到的内容」的占位符，
// 补丁内部若直接委托 replace 而不处理，就会把字面 $& 漏出去。

__check('String.prototype.at 正下标', 'abc'.at(0), 'a')
__check('String.prototype.at 负下标', 'abc'.at(-1), 'c')

__check('replaceAll 用字符串替换（元字符按字面处理）', 'a-b-c'.replaceAll('-', '+'), 'a+b+c')
__check('replaceAll 不会把 . 当正则元字符', 'a.b.c'.replaceAll('.', '/'), 'a/b/c')
__check('replaceAll 保留 $& 语义', 'ab'.replaceAll('a', '[$&]'), '[a]b')
__check('replaceAll 用全局正则', 'a1b2'.replaceAll(/\d/g, '#'), 'a#b#')

const matches = 'a1b2c3'.matchAll(/\d/g)
__check('matchAll 返回可遍历的集合', matches.length, 3)
__check('matchAll 第一项内容', matches[0][0], '1')

let joined = ''
for (const m of 'x1y2'.matchAll(/(\d)/g)) {
    joined += m[1]
}
__check('matchAll 能配合 for-of', joined, '12')
