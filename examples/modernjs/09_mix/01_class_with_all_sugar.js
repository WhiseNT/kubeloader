// ============================================
// 09-01 · 组合场景：class + 展开 + 解构默认值 + 数字分隔符
// ============================================
// requires-transform
//
// 单个特性都对，叠在一起也可能错。这里把四类特性塞进一个类里。

class LootTable {
    static DEFAULT = { rolls: 1, empty: false }

    constructor(id, options) {
        this.id = id
        this.options = { ...LootTable.DEFAULT, ...(options || {}) }
    }

    get rolls() {
        return this.options.rolls
    }

    describe({ prefix = 'loot' }) {
        const { rolls, empty } = this.options
        return prefix + ':' + this.id + ':' + rolls + ':' + empty
    }

    static of(id, options) {
        return new LootTable(id, options)
    }
}

const t = new LootTable('minecraft:chest', { rolls: 3 })
const auto = LootTable.of('minecraft:barrel')

__check('构造器里合并配置：覆盖项生效', t.rolls, 3)
__check('构造器里合并配置：未覆盖项用默认值', t.options.empty, 'false')
__check('getter 读合并后的配置', auto.rolls, 1)
__check('方法里的解构默认值', t.describe({}), 'loot:minecraft:chest:3:false')
__check('方法里的解构默认值（给定）', t.describe({ prefix: 'drop' }), 'drop:minecraft:chest:3:false')
__check('静态默认配置没被实例污染', LootTable.DEFAULT.rolls, 1)
__check('静态工厂方法', auto.id, 'minecraft:barrel')
