// ============================================
// 05 · for-of 里带默认值的模式：目前不支持
// ============================================
// expect-error: before function parameters
//
// for (const { v = 1 } of list) —— 模式后面跟的是 of 而不是 =，
// 所以「声明位置 + 赋值右侧」的改写方式套不上，转换器放过它。
//
// 可行的替代写法（本文件下半部分演示的是能跑的写法）：
//   for (const item of list) { const v = item.v === undefined ? 1 : item.v }

let total = 0
for (const { v = 1 } of [{}, { v: 2 }]) {
    total += v
}

__check('这行不该被执行到', total, 'never')
