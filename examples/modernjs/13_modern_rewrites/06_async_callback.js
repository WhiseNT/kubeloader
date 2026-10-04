// ============================================
// 13-06 · async / await → 回调（事件驱动）
// ============================================
// requires-transform
//
// 实测 `async function` / `await` 会被转换器拒绝（`missing ; before statement`），
// 提示里给的建议是：改用回调/事件，或给这个脚本单独加 `//engine: graaljs`。
//
// 这个 Rhino 没有事件循环，await「等一下再来」这件事本身就不成立，
// 所以 KubeJS 脚本里正确的形态是「注册回调 + 被调用时才执行」。

const seen = []

function loadThen(step, done) {
    // 真实场景里这里是注册事件/命令回调；这里同步模拟一次
    const result = step * 2
    done(result)
    return result
}

function collect(value) {
    seen.push(value)
}

const first = loadThen(3, collect)
const second = loadThen(4, collect)

__check('回调形态：同步阶段拿到返回值', first + '/' + second, '6/8')
__check('回调被调用了', seen.join(','), '6,8')
__check('展开仍由转换器负责', [...seen].length, 2)
