// ============================================
// 13-11 · 生成器（function* / yield）—— 转换结果合法，这里再钉住运行期
// ============================================
// requires-transform
//
// sweep 逐文件结果：`function* g(){ yield 1; }` → PASS。
// 但「能过 parser」不等于「运行期真的对」，所以这个文件把运行期也跑一遍。
// 注意 `async function*` 是另一回事（实测 UNSUPPORTED_SYNTAX）。

function* steps() {
    yield 'a'
    yield 'b'
    return 'done'
}

const it = steps()
const seen = [it.next().value, it.next().value]
const last = it.next()

__check('生成器取值', seen.join(','), 'a,b')
__check('结束标记', last.done, true)
__check('return 的值', last.value, 'done')
__check('展开仍由转换器负责', [...[1]].length, 1)
