// ============================================
// 09 · class 方法上的默认参数
// ============================================
// requires-transform
//
// 这是 issue #22 的源头：方法签名里的 = 会被误当成「方法体里的赋值」。
// 静态方法、实例方法、方法体里本来就有 = 的情况，都要分开照顾。

class Builder {
    static of(id, count = 1) {
        const out = { id: id, count: count }
        return out
    }

    withLabel(label = 'none') {
        return label
    }

    bodyContainsEquals(flag) {
        if (flag) {
            const x = 1
        }
        return 'ok'
    }

    static plainNoDefault(a) {
        return a
    }
}

__check('静态方法默认参数（缺省）', Builder.of('a').count, 1)
__check('静态方法默认参数（给定）', Builder.of('a', 3).count, 3)
__check('静态方法默认参数（id 不受影响）', Builder.of('a').id, 'a')
__check('实例方法默认参数（缺省）', new Builder().withLabel(), 'none')
__check('实例方法默认参数（给定）', new Builder().withLabel('L'), 'L')
__check('方法体里有 = 不该被误判', new Builder().bodyContainsEquals(true), 'ok')
__check('没有默认参数的静态方法', Builder.plainNoDefault('v'), 'v')
