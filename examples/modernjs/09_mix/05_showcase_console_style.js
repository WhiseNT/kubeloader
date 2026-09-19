// ============================================
// 09-05 · 示例脚本的样子（大量日志 + 少量断言）
// ============================================
// requires-transform
//
// 前面那些文件更偏「测试」，这个更像一份日常脚本：
// 主体是 console 日志，只在关键处断言。
// 运行器会收集 console 输出，加 -Dmodernjs.verbose=true 就能看到。

class ModDataService {
    static TABLE = {}

    static register(modId, payload) {
        const { version = '1.0', features = [] } = payload || {}
        ModDataService.TABLE[modId] = { version: version, features: features }
        console.log('registered', modId, version, features.length)
        return ModDataService.TABLE[modId]
    }

    static summary() {
        const ids = Object.keys(ModDataService.TABLE)
        return ids.map(id => id + '@' + ModDataService.TABLE[id].version).join(', ')
    }
}

ModDataService.register('kubeloader', { version: '1.2.0', features: ['modernjs', 'klm'] })
ModDataService.register('set_effects', { features: ['tooltip'] })

const summary = ModDataService.summary()
console.log('summary =', summary)

__check('注册了两条记录', Object.keys(ModDataService.TABLE).length, 2)
__check('缺省版本号', ModDataService.TABLE.set_effects.version, '1.0')
__check('显式给了 features', ModDataService.TABLE.set_effects.features.length, 1)
__check('summary 拼接', summary, 'kubeloader@1.2.0, set_effects@1.0')
__check('注册时没传 payload 也不炸', ModDataService.register('bare') !== null, 'true')
