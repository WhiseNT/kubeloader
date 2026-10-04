// ============================================
// 13-03 · 可选链（a?.b）→ && 短路 + 显式判空
// ============================================
// requires-transform
//
// 实测可选链会让转换器在转换阶段直接拒绝（`missing name after . operator`），
// 也就是脚本被跳过并给出提示 —— 属于「响亮失败」，不是静默算错。
//
// Rhino 能跑的等价写法：用 && 短路取属性；需要兜底值时显式判断，
// 别指望 `??`（它同样被硬拒绝）。

const config = { ui: { title: 'Panel' } }
const blank = { ui: null }

const title = config.ui && config.ui.title
const none = blank.ui && blank.ui.title

function pick(value, fallback) {
    return value === undefined || value === null ? fallback : value
}

const flag = [...[true]][0]

__check('&& 短路取到值', title, 'Panel')
__check('&& 短路保持 null 语义', none, null)
__check('显式兜底：null/undefined 才替换', pick(undefined, 'dflt') + '/' + pick(0, 'dflt'), 'dflt/0')
__check('展开仍由转换器负责', flag, true)
