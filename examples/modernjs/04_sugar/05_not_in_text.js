// ============================================
// 05 · 文本里的语法糖不该被改写
// ============================================
// requires-transform
//
// 日志模板、正则、注释里出现 1_000 / catch { } / ||= 太正常了。
// 这些必须原样保留，只有真代码才改写。

const s1 = '1_000'
const s2 = 'catch { }'
const s3 = 'a ||= 5'
const s4 = `模板里的 1_000 与 ||=`

// 注释里的 1_000 与 a ||= 5 也不该被改
/* 块注释里的 catch { } 同理 */

const realNumber = 1_000
let realAssign = null
realAssign ||= 3

function realCatch() {
    try {
        throw 1
    } catch {
        return 'ok'
    }
}

__check('字符串里的数字分隔符保持原样', s1, '1_000')
__check('字符串里的 catch {} 保持原样', s2, 'catch { }')
__check('字符串里的 ||= 保持原样', s3, 'a ||= 5')
__check('模板串文本保持原样', s4, '模板里的 1_000 与 ||=')
__check('同一文件里真的数字分隔符仍生效', realNumber, 1000)
__check('同一文件里真的 ||= 仍生效', realAssign, 3)
__check('同一文件里真的可选 catch 仍生效', realCatch(), 'ok')
