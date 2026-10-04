// ============================================
// 13-10 · BigInt（1n）→ 普通数字，超范围就用字符串
// ============================================
// requires-transform
//
// sweep 逐文件结果：`var v = 1n;` → UNSUPPORTED_SYNTAX。这个 Rhino 没有 BigInt。
//
// Rhino 能跑的版本：普通 number。要注意超过 2^53 就丢精度 ——
// 需要精确的场景（长整型 id、时间戳纳秒）改用字符串，或拆成两个数字。
// 下面第二条断言就是故意展示「超范围会糊掉」。

const safeMax = 9007199254740991
const key = 'id_' + safeMax

function joinIds(ids) {
    return ids.map((id) => `${id}`).join('|')
}

__check('安全整数范围内没问题', safeMax + 1 > safeMax, true)
__check('超出范围就丢精度（刻意展示）', 9007199254740993 === 9007199254740992, true)
__check('要精确就用字符串', joinIds([1, 2, 3]), '1|2|3')
__check('展开仍由转换器负责', [...[key]].length, 1)
