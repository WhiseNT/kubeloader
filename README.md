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

//闭包写法

//通过IIEF创建闭包
const MyTestContentPackMod = (function() {
    //创建私有变量
    let StringVar = '我是私有变量,无法直接从外部访问'
    let TestTrigger = true
    //私有函数
    function privateFunction() {
        console.log('我是私有函数,无法直接从外部调用');
    }
    //在此写入访问和调用上述内容的对象和函数
    return {
        getPrivate: function() {
            return StringVar
        },
        getTestTrigger: function() {
            return TestTrigger
        },
        setPrivate: function(value) {
            StringVar = value
        },
        setTestTrigger: function(value) {
            TestTrigger = value
        },
        callPrivateFunction: function() {
            privateFunction()
        }
    }
})()
console.log(MyTestContentPackMod.getPrivate())
MyTestContentPackMod.setPrivate('修改私有变量')
console.log(MyTestContentPackMod.getPrivate())
MyTestContentPackMod.callPrivateFunction()

//在事件中调用
ItemEvents.rightClicked(event=>{
    if (MyTestContentPackMod.getTestTrigger()) {
        console.log(MyTestContentPackMod.getTestTrigger())
        MyTestContentPackMod.setTestTrigger(false)
    }   else {
        console.log(MyTestContentPackMod.getTestTrigger())
        MyTestContentPackMod.setTestTrigger(true)
        
    }
})
```
