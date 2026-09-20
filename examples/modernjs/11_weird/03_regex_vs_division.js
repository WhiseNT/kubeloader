// ============================================
// 怪写法 03 · 正则与除法的歧义
// ============================================
// requires-transform
//
// 契约：`/` 到底是正则开始还是除号，词法扫描必须判对。
// 判错的话，后面的 `}` `{` `//` 都可能被吃掉，结构就崩了。

const r1 = /[{]}/
const r2 = /[/}]/
const r3 = /\}/
const r4 = /=/
const r5 = /a\/b/
const r6 = /[/*]/

const 除 = 10 / 2 / 5
const 除二 = (10 / 2) / 5
const 混合 = 100 / 4 + 25 / 5

class WithRegex {
    m() {
        return /}/.test('}')
    }
    n() {
        return 'a/b'.split('/').length
    }
}

const 替换 = 'x}y'.replace(/}/g, '!')

__check('正则里含 { 与 }', String(r1.test('{}')), 'true')
__check('正则里含 ] 与 }', String(r2.test('}')), 'true')
__check('正则里转义 }', String(r3.test('}')), 'true')
__check('正则就是一个等号', String(r4.test('=')), 'true')
__check('正则里转义斜杠', String(r5.test('a/b')), 'true')
__check('正则字符组里有 /', String(r6.test('*')), 'true')
__check('除法链（不是正则）', 除, 1)
__check('带括号的除法', 除二, 1)
__check('除法混合加法', 混合, 30)
__check('类方法里用正则', new WithRegex().m(), 'true')
__check('类方法里用除法（字符串切分）', new WithRegex().n(), 2)
__check('replace 用含 } 的正则', 替换, 'x!y')
