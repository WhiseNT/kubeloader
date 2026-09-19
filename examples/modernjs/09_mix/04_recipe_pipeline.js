// ============================================
// 09-04 · 组合场景：配方对象与方法
// ============================================
// requires-transform

class Recipe {
    constructor(id, inputs, output) {
        this.id = id
        this.inputs = inputs
        this.output = output
    }

    scaled(factor = 1) {
        return new Recipe(this.id, this.inputs, {
            item: this.output.item,
            count: this.output.count * factor
        })
    }

    toString() {
        return this.id + '=' + this.output.count + 'x' + this.output.item
    }
}

function collect(id) {
    const extra = Array.prototype.slice.call(arguments, 1)
    return { id: id, inputs: extra }
}

const base = new Recipe('mix', ['a', 'b'], { item: 'alloy', count: 1_000 })

__check('toString 用到了数字分隔符', base.toString(), 'mix=1000xalloy')
__check('scaled 默认参数', base.scaled().output.count, 1000)
__check('scaled 给定参数', base.scaled(3).output.count, 3000)
__check('scaled 不改原对象', base.output.count, 1000)
__check('scaled 结果仍是 Recipe', base.scaled(2) instanceof Recipe, 'true')

const r = collect('r1', 'x', 'y', 'z')
__check('用 arguments 收集变参', r.inputs.join(','), 'x,y,z')

const merged = { ...base.output, extra: true }
__check('展开对象：原字段', merged.item, 'alloy')
__check('展开对象：新字段', merged.extra, 'true')

const { item: outItem = 'none', count: outCount = 1, missing: m = 'd' } = base.output
__check('解构默认值（有值）', outItem, 'alloy')
__check('解构默认值（有值，数字）', outCount, 1000)
__check('解构默认值（缺键）', m, 'd')
