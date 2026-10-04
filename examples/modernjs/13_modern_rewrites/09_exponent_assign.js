// ============================================
// 13-09 · 指数赋值（v **= 3）→ 拆开写或用 Math.pow
// ============================================
// requires-transform
//
// sweep 逐文件结果：幂运算本身可以，复合赋值不行：
//   2 ** 3   → PASS
//   v **= 3  → UNSUPPORTED_SYNTAX
//
// Rhino 能跑的版本：`v = v * v * v`，或 `v = Math.pow(v, 3)`。

let acc = 2
acc = acc * acc * acc

const base = 5
const cubed = Math.pow(base, 3)
const powered = 2 ** 10

__check('拆开写', acc, 8)
__check('Math.pow', cubed, 125)
__check('幂运算本身可用', powered, 1024)
__check('展开仍由转换器负责', [...[1, 2]].length, 2)
