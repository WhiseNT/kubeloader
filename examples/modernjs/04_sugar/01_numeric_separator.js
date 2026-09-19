// ============================================
// 01 · 数字分隔符 1_000
// ============================================
// requires-transform
//
// 大数字用下划线分组可读性好，Rhino 不认（报 "missing ; before statement"）。
// 十进制、小数、十六进制、二进制、八进制都要能处理。

const thousand = 1_000
const million = 2_500_000
const decimal = 1_000.5
const hex = 0xFF_FF
const binary = 0b1010_1010
const octal = 0o17_17

__check('十进制', thousand, 1000)
__check('多位分组', million, 2500000)
__check('带小数的分组', decimal, 1000.5)
__check('十六进制分组', hex, 65535)
__check('二进制分组', binary, 170)
__check('八进制分组', octal, 975)

__check('参与运算', thousand + million, 2501000)
__check('分组不影响类型', typeof thousand, 'number')
