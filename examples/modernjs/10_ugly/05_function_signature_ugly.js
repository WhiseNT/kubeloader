// ============================================
// 丑写法 05 · 函数签名的空白地狱
// ============================================
// requires-transform
//
// 契约：签名里空格怎么加都不许改变语义。
//   - 名字和括号之间加空格
//   - 括号内外、逗号前后乱加空格 / 换行
//   - 默认参数与剩余参数混着写，且 ... 与名字之间也留空格

function   spaced   (  a  ,  b  )   {   return   a  +  b   }

function   withDefaults   (  a  =  1  ,  b  =  2  )   {   return   a  +  b   }

function   withRest   (  first  ,  ...  rest  )   {   return   first  +  rest.length   }

function   tight(a,b){return a*b}

function   broken   (
        a ,
        b
) { return a + b }

const expr = function  (  x  )  {  return  x  *  2  }

__check('名字与括号之间有空格', spaced(1, 2), 3)
__check('默认参数前后多空格（缺省）', withDefaults(), 3)
__check('默认参数前后多空格（给值）', withDefaults(5, 6), 11)
__check('... 与名字之间也有空格', withRest('a', 1, 2), 'a2')
__check('完全不留空格', tight(3, 4), 12)
__check('参数表跨行', broken(1, 2), 3)
__check('函数表达式带多余空格', expr(3), 6)
