// ============================================
// 丑写法 07 · 解构的空白地狱
// ============================================
// requires-transform
//
// 契约：模式里怎么加空格 / 换行都不许改变取值结果。
// 空位（[ , a ]）前后带空格尤其容易数错下标。

const   obj   =   {  a  :  1  ,  b  :  2  }

var {  a  =  9  ,  b  :  renamed  =  8  } = obj
__check('花括号内多空格：有值', a, 1)
__check('花括号内多空格：改名有值', renamed, 2)

var {  x  =  1  ,  y  =  2  } = {}
__check('等式两侧不留空格', x + y, 3)

const empty = {}
var {  missing  =  'd'  } = empty
__check('缺键仍然用默认值', missing, 'd')

const arr = [10, 20]
var [  first  =  9  ,  ,  third  =  9  ] = arr
__check('空位前后带空格：第一位', first, 10)
__check('空位前后带空格：越界位', third, 9)

function mixed( { p = 1 } , [ q = 2 ] , r = 3 ) {
    return p + q + r
}
__check('形参里两种模式混用（都缺省）', mixed({}, [], undefined), 6)
__check('形参里两种模式混用（都给值）', mixed({ p: 10 }, [20], 30), 60)

function tight({p=1},[q=2]){return p+q}
__check('形参里不留空格', tight({}, []), 3)
