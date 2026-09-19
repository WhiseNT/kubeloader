// ============================================
// 丑写法 02 · Tab 缩进 + 无空格 + 行尾空格
// ============================================
// requires-transform
//
// 制表符缩进的代码很常见（尤其从别处粘过来的）。
// 这里 class 与 { 之间、成员之间全用 Tab；末尾还留了行尾空格。
//
// 注意 get 与属性名之间用的是 Tab（不是空格）——这一处最容易在
// 「get + 空格」的写法上被漏掉。

class	Tabbed	{
	m()	{	return	1	}
	static	s()	{	return	3	}
}
   
class	WithAccessor	{
	constructor()	{	this._v	=	10	}
	get	v()	{	return	this._v	}
	set	v(nv)	{	this._v	=	nv	}
}

const t = new Tabbed()
const w = new WithAccessor()

__check('Tab 分隔的类头', t.m(), 1)
__check('Tab 分隔的静态方法', Tabbed.s(), 3)
__check('Tab 分隔的 getter', w.v, 10)

w.v = 42
__check('Tab 分隔的 setter', w.v, 42)
