# MC QQ Images

客户端 Fabric Mod，将群服互通消息里可点击的 `[图片]` / `[表情包]` 显示为图片预览，支持 GIF 动画。**同一个 JAR** 根据游戏版本加载对应的名字映射与渲染接口。

## 安装

1. 安装 Fabric Loader **0.19.3 或更新版本**。
2. 安装与你的 Minecraft 版本匹配的 Fabric API。
3. 将发布页中的 `mcqq-images-1.1.0-beta.2-universal.jar` 放进客户端 `mods` 目录并重启游戏。删除旧版 `mcqq-images` JAR，避免同一 Mod 重复安装。

只安装到客户端。Minecraft 1.21.x 使用 Java 21 或更新版本；26.x 游戏本身要求 Java 25。Mod 自身所有类编译为 Java 21 字节码。

## 兼容范围与验证状态

支持的正式版本：**1.21.4、1.21.5、1.21.6、1.21.7、1.21.8、1.21.9、1.21.10、1.21.11、26.1、26.1.1、26.1.2、26.2、26.3**。不包含快照与预发布版本。

构建会下载并校验每个版本的官方 Minecraft 客户端、映射、Fabric intermediary 与对应 Fabric API，逐个检查本 Mod 使用的公开字段、方法、纹理构造函数和事件声明。入口没有直接 Minecraft 类型引用；1.21.x 使用每版生成的 intermediary 名字，26.x 使用官方未混淆名字。兼容代码处理 1.21.4 的旧点击事件/纹理构造函数、1.21.4–1.21.5 的 Function 渲染接口，以及后续 RenderPipeline、GuiGraphicsExtractor 与 HUD 变化。

PNG 颜色、GIF 合成和播放时间、尺寸限制、URL 规则、反射参数分派和回调行为有自动测试。**当前为测试版：13 版的 API 检查不等于 13 版实际进游戏测试，HUD 位置、第三方聊天 Mod 和各渲染后端仍需客户端验收。** 初始化或渲染不兼容时记录日志并停止预览，不影响原始可点击聊天链接。

## 行为

- 只识别含 `[群内]` 的消息中带 `open_url` 动作的图片/表情标签；不扫描任意聊天里的普通网址。
- 右侧最多显示3张预览，默认显示30秒；打开聊天后可以再看缓存预览。
- PNG、JPEG、GIF 可直接预览。未安装 WebP ImageIO 解码器时，WebP 保留点击链接打开。
- 后台下载和解码，纹理上传在客户端线程执行。下载上限8 MiB；最多缓存6张图片；断开服务器时释放纹理，一小时后清理缓存。
- GIF 最多48帧、预览缩放到512像素以内，累计预览最多8百万像素，保留帧时长和 GIF disposal。
- 下载仅允许 QQ 图片域名和 `lemoe.luotiany1.top:18766/mcqq-media/<随机文件名>`，每次重定向重新验证。服务器临时链接的到期与删除由服务器负责，不由此 Mod 提供。

## 构建

安装 JDK 25、Python 3.10+，将 `JAVA_HOME` 指向 JDK 后运行：

```sh
python build.py
```

产物：`build/libs/mcqq-images-1.1.0-beta.2-universal.jar`。验证报告：`build/compatibility-report.json`。CI 同时上传它们。

`versions.lock.json` 固定官方元数据和 Fabric API 版本/摘要。构建依赖缓存在 `.cache`，不上传 Minecraft JAR、游戏素材或完整映射；发行 JAR 仅包含自己的代码和所需类的名字桥接表。首次构建需联网，下载约数百MB依赖。没有把 Fabric API、Fabric Loader 或 Minecraft 打进发行 JAR。

## 客户端验收

在目标版本的独立 Fabric 实例中安装同一个发行 JAR，进服后分别接收 PNG/JPEG、透明 PNG、GIF、纯图片和多图片消息；确认颜色、动画时间、点击链接和聊天隐藏状态。重连时旧纹理应释放；打开聊天能再看未过期的缓存。使用 Sodium/Iris 或自定义聊天 Mod 时也应单独验收。反馈问题时注明完整 Minecraft、Java、Fabric Loader/API 版本和 `latest.log` 中的 `[MCQQ Images]` 信息。

MIT License。Minecraft 属于 Mojang/Microsoft，本项目非官方。
