// ============================================
// 02 · 可选 catch 绑定 catch { }
// ============================================
// requires-transform
//
// 不关心异常对象时可以写 `catch {`。Rhino 要求括号里有东西
// （报 "missing ( before catch-block condition"）。

function caught() {
    try {
        throw new Error('boom')
    } catch {
        return 'caught'
    }
    return 'not-reached'
}

function emptyHandler() {
    try {
        throw 1
    } catch {
    }
    return 'after'
}

function withBinding() {
    let msg = ''
    try {
        throw new Error('real')
    } catch (e) {
        msg = e.message
    }
    return msg
}

function nested() {
    let log = ''
    try {
        try {
            throw 1
        } catch {
            log += 'inner'
            throw 2
        }
    } catch {
        log += '|outer'
    }
    return log
}

__check('可选 catch 绑定：能捕获', caught(), 'caught')
__check('空的 catch 体', emptyHandler(), 'after')
__check('同一文件里带绑定的 catch 仍可用', withBinding(), 'real')
__check('嵌套 try / catch（内外都无绑定）', nested(), 'inner|outer')
