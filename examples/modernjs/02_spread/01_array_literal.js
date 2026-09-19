// ============================================
// 01 · 数组字面量里的展开
// ============================================
// requires-transform
//
// KubeJS 里拼接物品列表、合并标签数组时很常见：[...主表, ...追加表]。
// Rhino 的语法里没有 ...，得转换器改成 concat / apply。

const a = [1, 2]
const b = [3, 4]

const c = [0, ...a, ...b, 5]

__check('拼接顺序', c.join(','), '0,1,2,3,4,5')
__check('结果长度', c.length, 6)
__check('原数组没被改动', a.join(','), '1,2')

// 注意：[...a, [...b, 6]] 的第二个成员是「嵌套的数组字面量」，不是展开，
// 所以结果就是 [1, 2, [3, 4, 6]]（要拉平得写 [...a, ...b, 6]）。
// 另外这个 Rhino 把数组转成字符串时是 Java 风格（[3, 4, 6]），
// 所以嵌套数组不能靠 join 断言，要直接看长度和元素。
const nested = [...a, [...b, 6]]
__check('嵌套数组作为成员：长度', nested.length, 3)
__check('嵌套数组作为成员：第一个是展开结果', nested[1], 2)
__check('嵌套数组作为成员：内层数组本身', nested[2].join(','), '3,4,6')

const flattened = [...a, ...b, 6]
__check('连续展开 + 元素（对照）', flattened.join(','), '1,2,3,4,6')

const copy = [...a]
copy.push(9)
__check('展开可当浅拷贝', copy.length, 3)
__check('浅拷贝不影响原数组', a.length, 2)

const empty = [...[]]
__check('展开空数组', empty.length, 0)
