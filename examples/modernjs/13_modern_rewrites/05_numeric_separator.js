// ============================================
// 13-05 · 数字分隔符（1_000）—— 实测可用，转换器会去掉下划线
// ============================================
// requires-transform
//
// sweep 逐文件结果：`var v = 1_000;` → PASS。下划线只是为了好读，
// 转换器负责把它变成普通数字字面量；原文本身过不了 Rhino，所以标了 requires-transform。
//
// 所以不必刻意避开下划线 —— 这一条记的就是这件事。

const MS_PER_TICK = 50
const TICKS_PER_SECOND = 1_000 / MS_PER_TICK
const scriptCount = 20

function perTickLimit(total) {
    return total / TICKS_PER_SECOND
}

const names = [...['alpha', 'beta']]
const doubled = names.map((name) => name + '!')

__check('每秒 tick 数', TICKS_PER_SECOND, 20)
__check('每 tick 上限', perTickLimit(scriptCount), 1)
__check('模板串拼出来', `limit=${perTickLimit(scriptCount)}`, 'limit=1')
__check('展开与箭头仍由转换器负责', doubled.join(','), 'alpha!,beta!')
