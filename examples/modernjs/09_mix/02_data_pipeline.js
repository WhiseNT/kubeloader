// ============================================
// 09-02 · 组合场景：数组处理管线
// ============================================
// requires-transform
//
// 这是 KubeJS 脚本里最常见的形状：一堆链式数组操作 + 箭头函数 + 解构。

const raw = [
    { name: 'iron', count: 1_000 },
    { name: 'gold', count: 2_500 },
    { name: 'iron', count: 500 }
]

const total = raw
    .map(item => item.count)
    .reduce((sum, n) => sum + n, 0)

__check('数字分隔符参与求和', total, 4000)

const names = Array.from(new Set(raw.map(item => item.name))).join(',')
__check('Set 去重', names, 'iron,gold')

const byName = {}
for (const item of raw) {
    byName[item.name] = (byName[item.name] || 0) + item.count
}
__check('for-of 里累加', byName.iron, 1500)
__check('for-of 里累加（另一个键）', byName.gold, 2500)

const top = raw.filter(item => item.count > 600).map(item => item.name)
__check('filter + map', top.join(','), 'iron,gold')

const merged = raw.map(item => ({ ...item, label: item.name + 'x' + item.count }))
__check('map 里展开对象并追加字段', merged[0].label, 'ironx1000')

const flat = [1, [2, 3], [4]].flat()
__check('flat（补丁提供）', flat.join(','), '1,2,3,4')
