// ============================================
// 09-03 · 组合场景：注册表风格（贴近 KubeJS 写法）
// ============================================
// requires-transform
//
// 模仿 KubeJS 里 item / recipe 注册的常见形状：链式注册 + 选项对象 + 解构默认值。

class ItemRegistry {
    constructor() {
        this.items = {}
        this.count = 0
    }

    register(name, config) {
        const { stackSize = 64, tags = [] } = config || {}
        this.items[name] = { name: name, stackSize: stackSize, tags: tags }
        this.count += 1
        return this
    }

    describe(name) {
        const item = this.items[name]
        if (!item) {
            return 'missing:' + name
        }
        return item.name + '(' + item.stackSize + ')[' + item.tags.join(',') + ']'
    }

    static defaultProps() {
        return { stackSize: 64, tags: [] }
    }
}

const reg = new ItemRegistry()
reg.register('iron_ingot', { tags: ['metal'] })
    .register('diamond', { stackSize: 16 })
    .register('stick')

__check('链式注册的条数', reg.count, 3)
__check('没给配置 → 用默认值', reg.describe('stick'), 'stick(64)[]')
__check('给了部分配置', reg.describe('diamond'), 'diamond(16)[]')
__check('数组型默认值', reg.describe('iron_ingot'), 'iron_ingot(64)[metal]')
__check('查不到的名字', reg.describe('nope'), 'missing:nope')

const props = ItemRegistry.defaultProps()
const { stackSize: size = 1, extra = 'none' } = props

__check('解构静态方法返回的对象（有值）', size, 64)
__check('解构静态方法返回的对象（缺省）', extra, 'none')
