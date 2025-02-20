# ReadMe

ContentPack[内容包]是由KubeJS脚本&资源组成的文件夹或zip格式压缩包。

文件目录参考：

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

```
