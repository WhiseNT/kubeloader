// ============================================
// 02 · Array.prototype.at / flat / flatMap
// ============================================
// 不标 requires-transform：补丁由运行器（等同线上 mixin）先装好，
// 原文也能跑；这里验证的是补丁行为正确、且在完整脚本链路里可用。
//
// 这几个方法在 KubeJS 脚本里用得极多，而 Rhino 一个都没有：
//   arr.at(-1)                    → 取末尾元素
//   arr.flat()                    → 拉平一层
//   arr.flatMap(fn)               → map 后拉平一层

const arr = [1, 2, 3]

__check('.at(0) 正下标', arr.at(0), 1)
__check('.at(-1) 负下标', arr.at(-1), 3)
__check('.at(-2) 负下标', arr.at(-2), 2)
__check('.at 越界给 undefined', String(arr.at(9)), 'undefined')

__check('.flat 默认拉平一层', [[1, 2], [3]].flat().join(','), '1,2,3')

// flat(2) 只拉两层：[1,[2,[3,[4]]]] → [1,2,3,[4]]，第四项仍是数组
const twoLevels = [1, [2, [3, [4]]]].flat(2)
__check('.flat(2) 后的长度', twoLevels.length, 4)
__check('.flat(2) 第四项仍是数组', Array.isArray(twoLevels[3]), 'true')
__check('.flat(2) 第四项的内容', twoLevels[3].join(','), '4')

__check('.flat(Infinity) 拉平到底', [1, [2, [3, [4]]]].flat(Infinity).join(','), '1,2,3,4')

__check('.flatMap', [1, 2].flatMap(function (x) { return [x, x * 10] }).join(','), '1,10,2,20')
__check('.flatMap 返回非数组会被包起来（按规范）',
        [1].flatMap(function (x) { return x + 1 }).join(','), '2')

const holes = [1, , 3]
__check('.flat 跳过空位', holes.flat().join(','), '1,3')
