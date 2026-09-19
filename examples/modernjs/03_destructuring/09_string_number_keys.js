// ============================================
// 09 · 字符串键 / 数字键 / 保留字当键名
// ============================================
// requires-transform
//
// 取值表达式一律写成 obj["键"]：写成 obj[键] 会被当成变量，
// 而保留字（if / class 之类）当键名时也不能裸着用。

const empty = {}

const { 'name with space': spaced = 'x' } = empty
__check('带空格的字符串键 + 默认值', spaced, 'x')

const { 0: zero = 'z' } = empty
__check('数字键 + 默认值', zero, 'z')

const { 'if': keyword = 'kw-default' } = empty
__check('保留字当键名 + 默认值', keyword, 'kw-default')

const filled = { 'a b': 1, 0: 'zero', 'if': 'kw', 'class': 'cls' }

const { 'a b': ab = 0, 0: zeroVal = 'd', 'if': kw = 'd', 'class': cls = 'd' } = filled

__check('取值：带空格键', ab, 1)
__check('取值：数字键', zeroVal, 'zero')
__check('取值：保留字键 if', kw, 'kw')
__check('取值：保留字键 class', cls, 'cls')
