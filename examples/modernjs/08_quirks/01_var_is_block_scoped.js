// ============================================
// 08-01 · 这个 Rhino 的 var 是按块作用域的
// ============================================
// expect-error: is not defined
//
// 这不是 ModernJS 转换器的问题，而是这个 Rhino 分支的运行期行为，
// 但写脚本的人必须知道，否则会写出「看起来没问题、一跑就 ReferenceError」的代码。
//
// 标准 JS 里 var 是「函数作用域」，块里声明的 var 出了块照样能读。
// 这个分支不是——实测：
//   function f() { if (t) { var x = 1 } return x }        → ReferenceError: x is not defined
//   var s = 0; for (var i = 0; i < 3; i++) { s += i } i    → ReferenceError: i is not defined
//   function g() { for (var i = 0; i < 3; i++) {} return typeof i }  → "undefined"
//
// 规避办法：在使用它的那一层声明变量。
//   累加器放到循环外面：
//     var s = 0; for (var i = 0; i < 3; i++) { s += i }   s    // 3，没问题
//   或者干脆用 let / const 明确表达作用域意图。

function readOutsideBlock() {
    if (true) {
        var inner = 1
    }
    return inner
}

readOutsideBlock()

// 作为对照：累加器声明在循环外面就没问题（这段本身不会被执行到，
// 因为上面的 ReferenceError 已经把脚本中断了）。
var total = 0
for (var i = 0; i < 3; i++) {
    total += i
}
__check('累加器放外层就没问题', total, 3)
