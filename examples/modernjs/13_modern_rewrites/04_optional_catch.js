// ============================================
// 13-04 · 可选 catch 绑定（catch {}）—— 实测可用，不必自己补参数
// ============================================
// requires-transform
//
// sweep 逐文件结果：`try { throw 1 } catch { }` → PASS（转换结果过得了 Rhino parser）。
// 所以**可以直接写** catch {}，不用像老 ES5 那样补一个用不到的参数。
//
// 这个文件把「能跑」这件事钉住；真正不支持的是可选链的调用/下标形态，
// 那几种记在同目录 07。

const items = [...['x']]

function safeParse(text) {
    try {
        return JSON.parse(text)
    } catch (e) {
        return null
    }
}

let threw = false
try {
    items.push(null.notAThing)
} catch (e) {
    threw = true
}

__check('显式绑定参数照样能吞异常', safeParse('{'), null)
__check('正常路径通过', safeParse('{"ok":1}').ok, 1)
__check('真的抛了也接得住', threw, true)
__check('展开仍由转换器负责', items.join(','), 'x')
