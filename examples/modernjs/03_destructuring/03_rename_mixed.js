// ============================================
// 03 · 改名 + 默认值混在一个模式里
// ============================================
// requires-transform
//
// 同一个模式里可以既有「原样取的成员」、又有「改名的」、又有「带默认值的」。
// 转换时得给每个成员分别判断，不能一刀切。

const src = { id: 'iron', count: null }

const { id, count = 1, missing = 'x' } = src

__check('混合：没有默认值的成员照常取出', id, 'iron')
__check('混合：null 不触发默认值', count, 'null')
__check('混合：缺键触发默认值', missing, 'x')

const pair = { left: 1 }
const { left: l = 0, right: r = 0 } = pair

__check('同一声明里多个改名 + 默认值（有值）', l, 1)
__check('同一声明里多个改名 + 默认值（缺省）', r, 0)

const tpl = { w: 10 }
const { w = 1, h: height = 2 } = tpl

__check('原样取与改名取同时存在（原样）', w, 10)
__check('原样取与改名取同时存在（改名）', height, 2)
