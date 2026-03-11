

# Important Info
English version: https://github.com/WhiseNT/kubeloader_wiki-en

If you have any questions with this mod,please send an issue.

If you want to know sth about ".ts" and ES6,please to:(both English and Chinese)

https://github.com/WhiseNT/kubeloader/wiki/ModernJS

# KubeLoader 概述

**KubeLoader** 是 **KubeJS** 的附属模组，但旨在将更强大的功能引入KubeJS。

**KubeLoader** 通过引入了一套名为内容包（ContentPack）的系统来重新定义了KubeJS脚本的传播形式，并让脚本能如同模组一样加载。

本页面旨在概述 KubeLoader 的整体架构、核心组件及其对 KubeJS 框架的扩展机制。

如需了解内容包的具体结构与创建方法，请参阅 内容包。

## 为什么选择KubeLoader

KubeLoader 的目标是 KubeJS 的下一代开发框架。为此，KubeLoader提供了：
* 一套完整的模块化系统——内容包（ContentPack）——让开发者能够以“模组化”的方式构建、集成和发布 KubeJS 功能，彻底告别传统脚本的扁平化与耦合问题。
* 增加了对GraalJS的支持，你可以选择使用更先进的GraalJS来作为KubeJS的JS引擎，而不是Rhino。

通过 KubeLoader，开发者不再只是编写脚本，而是开发“模组”。你可以：
* 像开发模组一样开发脚本：拥有独立的依赖管理、命名空间和生命周期。
* 像安装模组一样加载功能：轻松集成他人开发的内容包，快速构建复杂项目。
* 像发布模组一样分享成果：一键导出为 .jar 文件，直接作为模组发布到 CurseForge 或 Modrinth。

借助 KubeLoader，你可以以模块化的思路来开发脚本，并以“开发模组”的思维来开发和发布内容包。

## 核心特性
KubeLoader 提供了一系列强大的功能，旨在提升 KubeJS 脚本开发体验。以下是其主要特性：
* 读取文件夹和JAR格式的内容包。
* 快速创建内容包项目，并能迅速导出为Jar Mod。
* 为KubeJS添加新的事件、工具类来提升脚本开发体验。
* 支持内容包通过API来进行安全的数据通信和共享。
* 将GraalJS引擎带入KubeJS。
