// ============================================
// 丑写法 04 · 类体里注释乱插
// ============================================
// requires-transform
//
// 注释里带着 ; { } 和引号 —— 成员切分与花括号配对都不能被骗到。
// 另外：叫 get 的方法（不是访问器）与真正的 getter 要能区分开。

class Commented {
    /* 成员之前 */
    x = 1; // 行尾注释

    m() {
        // 方法体里的注释
        return this.x // 又一条行尾注释
    }

    /* 这条注释里有 ; 和 { 和 } —— 不能影响成员切分 */
    get y() { return 2 } // getter 行尾

    /* 注释里有引号 '{' 和注释符 // */
    set y(v) { this._y = v }

    get y2() { return this._y }

    get() { return 'method-named-get' }
}

const c = new Commented()

__check('字段 + 行尾注释', c.x, 1)
__check('方法体里的注释', c.m(), 1)
__check('注释里带 ; { } 之后的 getter', c.y, 2)

c.y = 9
__check('注释里带引号之后的 setter', c.y2, 9)
__check('叫 get 的方法不是访问器', c.get(), 'method-named-get')
