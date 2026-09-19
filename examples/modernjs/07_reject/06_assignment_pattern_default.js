// ============================================
// 06 · 不带声明的解构赋值默认值：目前不支持
// ============================================
// expect-error: before function parameters
//
// ({ a = 3 } = obj) 是「解构赋值」而不是「解构声明」。Rhino 连普通的
// ({ a } = obj) 都报 invalid object initializer，带默认值自然更不行。
//
// 替代写法：先取出来再判断。

let a
const obj = {}

__check('这行不该被执行到', String(a), 'never')

;({ a = 3 } = obj)
