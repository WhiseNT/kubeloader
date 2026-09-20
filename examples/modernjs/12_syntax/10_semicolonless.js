// ============================================
// 语法边角 10 · 全篇不写分号（KubeJS 脚本的常见风格）
// ============================================
// requires-transform
//
// 契约：不写分号时，语句边界全靠换行与 ASI。转换器按行切分语句时，
// 既不能把相邻两条粘在一起，也不能把续行误当成新语句。

class 计数器 {
    constructor() {
        this.值 = 0
        this.历史 = []
    }

    加(n) {
        this.值 += n
        this.历史.push(this.值)
        return this
    }

    get 当前() {
        return this.值
    }
}

const c = new 计数器()
c.加(1).加(2).加(3)

const { 当前 } = c
const 列表 = [1, 2, 3]
const 末位 = 列表[列表.length - 1]

function 加倍(x) {
    return x * 2
}

const 结果 = 加倍(c.当前)

__check('链式调用', c.当前, 6)
__check('解构取值', 当前, 6)
__check('下标取值', 末位, 3)
__check('函数调用', 结果, 12)
