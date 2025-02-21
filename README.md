# KUBELOADER

KubeLoader是一个KubeJS附属模组，在现在处于早期开发阶段，仅支持Forge 1.20.1(因为模组所涉及内容与Minecraft本体关联度不大,所以版本的迁移很容易.在进度足够时会去尽快支持其他版本。)

KubeLoader提供了一种类似于“资源包”或“数据包”的KubeJS脚本&资源集合，名为内容包（ContentPack），并支持 **从内容包文件夹**  和  **从模组资源** 两种读取方式。

从内容包文件夹的方式为：在kubejs/contentpacks/下放置文件夹或zip格式压缩包 （如果没有，可以自行创建，或等待游戏启动后由模组创建）


在写入内容后，KubeLoader会在常规脚本路径下新建contentpack_scripts文件夹来存放脚本（不要修改该文件夹下的任何内容）

同时会创建pack_resources文件夹用于存放内容包的资源（assets + data）

内容包文件结构参考：

```
文件夹类型
namespace/ (文件夹)
    └── server_scripts/
    └── client_scripts/
    └── server_scripts/
    └── assets/
    └── data/
压缩包类型
CustomName.zip (压缩包)
    └── namespace/ 
            └── server_scripts/
            └── client_scripts/
            └── server_scripts/
            └── assets/
            └── data/
打包入模组资源
Resources/ (模组资源)
    └── assets/
    └── data/
    └── contentpack/ (注意,没有s)
            └── server_scripts/
            └── client_scripts/
            └── server_scripts/
    
```

namespace在文件上不会对脚本产生约束,但是希望你使用非kubejs而是一个新的唯一的名称作为namespace（就像你在创建一个新的模组）。

注意：在构建压缩包时若在目录中带有**其他文件夹**会导致内容包加载失败。

脚本编写建议：

因为KubeJS脚本没有根据namespace隔离的功能（只有对于不同type的脚本进行隔离的）

所以如果你在编写一个内容包，请保持所有**非事件内**的变量的唯一性，避免冲突。

推荐以下写法：

```jsx
//对象写法

//创建一个对象用于存储事件外的其他变量,防止污染作用域和冲突.
const TestContentPackMod = {}
//通过给key赋值来实现原本的“变量声明”.
TestContentPackMod.MyTestTrigger = true
//在事件中调用
ItemEvents.rightClicked(event=>{
    if (TestContentPackMod.MyTestTrigger) {
        console.log("触发内容！")
} else {
        TestContentPackMod.MyTestTrigger = false
}
})

//IIEF写法

(()=>{
    //在大括号内部的变量不会影响全局作用域
    let A = true
    
    ItemEvents.rightClicked(event=>{
        event.player.tell(A)
    })
})()

```
