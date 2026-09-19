// ============================================
// 04 · 逻辑赋值的短路语义（右侧不该被多算）
// ============================================
// requires-transform
//
// 摊平后如果写成「先算右侧再判断」，副作用就会多跑一遍。
// 这里用计数器把这件事钉死。

let calls = 0

function pick() {
    calls += 1
    return 7
}

let hasValue = 'have'
hasValue ||= pick()
__check('||= 左侧有值时右侧不求值', calls, 0)
__check('||= 左侧有值时值不变', hasValue, 'have')

let isEmpty = null
isEmpty ||= pick()
__check('||= 需要赋值时右侧求值一次', calls, 1)
__check('||= 赋值结果正确', isEmpty, 7)

let isTrue = 1
isTrue &&= pick()
__check('&&= 左侧为真时右侧求值一次', calls, 2)

let isFalse = 0
isFalse &&= pick()
__check('&&= 左侧为假时右侧不求值', calls, 2)
__check('&&= 短路时值不变', isFalse, 0)

let notNull = 0
notNull ??= pick()
__check('??= 左侧是 0（非 null/undefined）时不求值', calls, 2)
__check('??= 短路时保留 0', notNull, 0)
