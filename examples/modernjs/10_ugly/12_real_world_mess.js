// ============================================
// 丑写法 12 · 真实世界的综合丑
// ============================================
// requires-transform
//
// 一份「赶工产物」的样子：注释掉的旧代码、重复命名、调试残留、
// 混用单双引号、风格前后不一致。契约只有一个：语义别变。

// TODO 以后换成配置读的
// class OldThing {
//     old() { return 'deprecated' }
// }

var ITEM_MAP = {}
var item_map = {} // 历史遗留，没人敢删

function reg(id, cfg) {
    var c = cfg || {}
    var size = c.stackSize === undefined ? 64 : c.stackSize
    ITEM_MAP[id] = { id: id, size: size }
    return ITEM_MAP[id]
}

// console.log('debug 1')
// console.log('debug 2')

reg("minecraft:iron_ingot", { stackSize: 16 })
reg('minecraft:stick', {})

class Registry2 {
    constructor(items) {
        this.items = items
    }

    get size() {
        return Object.keys(this.items).length
    }

    describe({ prefix = 'x' }) {
        var names = []
        for (var key in this.items) {
            names.push(key)
        }
        return prefix + ':' + names.join('|')
    }
}

const r2 = new Registry2(ITEM_MAP)

__check('注册表大小', r2.size, 2)
__check('getter 读到的是真数量', new Registry2({}).size, 0)
__check('describe 带上了前缀', r2.describe({ prefix: 'p' }).indexOf('p:') === 0, 'true')
__check('describe 里能看到两个条目', r2.describe({}).indexOf('|') > 0, 'true')

var fallback = item_map.iron || reg('fallback:iron', { stackSize: 1 })
__check('空 map 走默认注册', fallback.size, 1)
