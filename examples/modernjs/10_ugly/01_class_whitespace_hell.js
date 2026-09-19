// ============================================
// 丑写法 01 · class 的空白地狱
// ============================================
// requires-transform
//
// 契约：格式再丑，语义不许变。
// 这里把空格/换行/缩进全都搞乱：类名和 { 分两行、get 后面好几个空格、
// 方法参数括号里外都塞空格、extends 单独占一行。

class   Ugly1
     {
   constructor(  a  )
     {
       this.a   =   a
     }

   get   x(  )
   {
     return   this.a
   }

   set   x(  v  )
   {
     this.a  =  v
   }

   m(  )   {   return   this.a   }
}

class   Ugly2
    extends
        Ugly1
{
    m() { return super.m() + 1 }
}

const u1 = new Ugly1(5)
const u2 = new Ugly2(3)

__check('类名与 { 分两行', u1.m(), 5)
__check('get 后多个空格', u1.x, 5)

u1.x = 9
__check('set 后多个空格、参数括号带空格', u1.x, 9)

__check('extends 单独占一行', u2.m(), 4)
__check('父类仍正常', new Ugly1(1).m(), 1)
