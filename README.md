# Latest Test Update - KubeLoader 0.0.8-es (ModernJS)
current version:v0.0.8-es2

ModernJS module uses source code conversion to let you write TypeScript and ES6+ syntax in KubeJS scripts — all running on original Rhino.

现代吉斯模块使用“源代码转换”来让你能够在编写KubeJS脚本时使用TypeScript和ES6语法。————脚本仍然运行在原始的Rhino上。

## 语法支持：

### TypeScript:

类型注解（参数、返回值、变量） / Type Annotations (Parameters, Return Types, Variables)  
```typescript
function greet(name: string): void {}
const id: number = 123;
```
接口声明 / Interface Declaration  
```typescript
interface Player {
  name: string;
  health: number;
}
```
类型别名 / Type Alias  
```typescript
type ID = string | number;
type Callback<T> = (item: T) => void;
```
泛型函数与箭头函数 / Generic Functions and Arrow Functions  
```typescript
function identity<T>(arg: T): T { return arg; }
const mapper = <T>(x: T): string => String(x);
```
类中的类型注解 / Type Annotations in Classes  
```typescript
class Service {
  name: string = "default";
  log(msg: string): void {}
}
```
可选属性与可选参数 / Optional Properties and Parameters  
```typescript
interface Config { timeout?: number; }
function init(options?: Config) {}
```
对象字面量中的字面值（自动保留） / Object Literal Values (Preserved Automatically)  
```typescript
const cfg = { port: 8080, ssl: true, tags: ["a", "b"] };
```
类实现子句（`implements`） / Class Implements Clause  
```typescript
class MyLogger implements Logger<Player> {}
```


### JavaScript:

类声明 / Class Declaration
```javascript
class Util { constructor() {} }
```

类继承 / super
```javascript
class Logger extends Util {
  constructor(id) {
    super(id);
  }
}
```

实例字段 / (Class Property Initialization)
```javascript
class Dog {
  name = "unknown";
}
```

静态字段 / Static Fields
```javascript
class Util {
  static version = "1.0";
}
```
类中箭头函数 / Arrow Function Fields in Class
```javascript
class Logger {
  cache = [];
  push = (msg) => this.cache.push(msg);
}
```
默认参数 / Default Parameters
```javascript
function createLogger(id = 0, level = "debug") { ... }
```
对象属性简写 / Object Property Shorthand
```javascript
const x = 1, y = 2;
return { x, y };
```

# Important Info
English version: https://github.com/WhiseNT/kubeloader_wiki-en

If you have any questions with this mod,please send an issue.

# KubeLoader 概述

**KubeLoader** 不只是一个 KubeJS 附属模组，它更是一个面向未来的**KubeJS模块化开发平台**。

**KubeLoader** 通过引入了一套名为内容包（ContentPack）的系统来重新定义了KubeJS脚本的传播形式，并让脚本能如同模组一样加载。

本页面旨在概述 KubeLoader 的整体架构、核心组件及其对 KubeJS 框架的扩展机制。

如需了解内容包的具体结构与创建方法，请参阅 内容包。

## 什么是 KubeLoader？

KubeLoader 是 KubeJS 的下一代开发框架。它提供了一套完整的模块化系统——内容包（ContentPack）——让开发者能够以“模组化”的方式构建、集成和发布 KubeJS 功能，彻底告别传统脚本的扁平化与耦合问题。

* 注：内容包不仅是脚本包，它同时能携带 assets 与 data 内容，是资源包+数据包的超集。

通过 KubeLoader，开发者不再只是编写脚本，而是开发可复用的功能模块。你可以：
* 像开发模组一样开发脚本：拥有独立的依赖管理、命名空间和生命周期。
* 像安装模组一样加载功能：轻松集成他人开发的内容包，快速构建复杂项目。
* 像发布模组一样分享成果：一键导出为 .jar 文件，直接作为模组发布到 CurseForge 或 Modrinth。

KubeLoader 的目标是成为 KubeJS 生态的 “脚本操作系统” —— 管理模块、协调依赖、扩展能力，并为整个社区提供统一的开发范式。


借助 KubeLoader，你可以以模块化的思路来开发脚本，并以“开发模组”的思维来开发和发布内容包。

## 核心特性
KubeLoader 提供了一系列强大的功能，旨在提升 KubeJS 脚本开发体验。以下是其主要特性：
* 读取文件夹、ZIP和JAR格式的内容包。
* 快速创建内容包项目，并能迅速导出为Jar Mod。
* 为KubeJS添加新的事件、工具类来提升脚本开发体验。
* 支持内容包通过API来进行安全的数据通信和共享
