<h1 align="center">BBDroid</h1>

<p align="center"><strong>下载、转码、鼓谱，一站完成。</strong></p>

<p align="center">
  B 站视频下载 · FFmpeg 音视频工具箱 · 在线鼓谱 PDF<br>
  从收藏视频到整理鼓谱，在 Android 上完成。
</p>

<p align="center">
  <strong><a href="https://github.com/duyuanxi/bbdroid/releases/latest">下载 Android 安装包</a></strong>
  &nbsp;·&nbsp; <a href="#功能亮点">功能亮点</a>
  &nbsp;·&nbsp; <a href="#界面预览">界面预览</a>
  &nbsp;·&nbsp; <a href="#快速上手">快速上手</a>
  &nbsp;·&nbsp; <a href="CHANGELOG.md">更新记录</a>
</p>

<p align="center">
  <strong>当前发布 v0.5.3</strong> &nbsp; / &nbsp; Android 7.0+ &nbsp; / &nbsp; 64 位 ARM · x86
</p>

![BBDroid v0.5.0 宣传海报：视频下载、音视频转换与在线鼓谱 PDF](https://github.com/user-attachments/assets/178b4810-2a27-4e08-9466-5f5b6870956c)

<p align="center"><sub>海报展示 v0.5.0 功能；当前安装包请从上方下载入口获取。</sub></p>

## 功能亮点

| 收藏视频 | 处理音视频 | 整理鼓谱 |
| :--- | :--- | :--- |
| 单条、分 P 与列表批量下载，选择源画质，自动合并音视频。 | 转格式、提取音频、压缩与剪辑，下载完成后直接导入。 | 从公开滚动鼓谱视频生成 PDF，逐页预览、调整打印排版并保存。 |

### 视频下载 · 保留喜欢的内容

- **从单条到批量**：支持单视频、分 P、合集、系列、番剧、收藏夹与 UP 主投稿列表。
- **画质与编码可选**：选择视频源提供的 AVC / HEVC / AV1 编码，以及 1080P 高帧率、4K、HDR、杜比视界、8K 等档位。
- **合并无需再次压缩**：分别下载音视频流，通过 FFmpeg `-c copy` 无损封装为 MP4，保留所选流的画质。
- **配套内容按需保存**：封面、XML 弹幕与 SRT 字幕；字幕以视频源提供的内容为准。
- **任务进度清晰可见**：支持分段并行下载、备用 CDN 切换，以及单条与批量任务进度展示。

> 提供网页登录与扫码登录。高清、会员及番剧内容需要对应账号权限，实际可选画质和编码以视频源返回结果为准。

### 音视频工具箱 · 下载之后，继续处理

下载完成后选择「导入转码」，即可继续处理；已有的本地文件也可直接导入。

| 处理方式 | 可用选项 |
| --- | --- |
| 视频格式 | MP4、MKV、MOV、AVI、FLV、WMV、WebM、MPEG-TS |
| 视频编码 | H.264、H.265 / HEVC、AV1、VP9、MPEG-4，以及 H.264 / H.265 硬件编码选项 |
| 体积与画质 | CRF、视频码率、分辨率、帧率 |
| 音频处理 | MP3、AAC、FLAC、WAV、OGG、M4A；调整码率、声道、采样率与音量 |
| 封装与剪辑 | 复制音视频流、关闭视频或音频轨、设置开始时间与输出时长 |
| 批量与信息 | 单文件、多文件、文件夹导入、批量转码，以及 ffprobe 媒体信息读取 |

工具箱内置 FFmpeg 引擎。格式、编码与参数需要相互兼容，硬件编码取决于设备支持情况。

### 在线鼓谱 PDF · 把鼓谱带到谱架上

- **链接直接转入**：支持 BV 号、分享文案、b23.tv 短链接与分 P，可设置时间选段和手动谱区。
- **处理状态可追踪**：查看排队与处理阶段，支持取消、重试、最近十个任务及完整任务链接恢复。
- **先预览，再打印**：原生 PDF 分页预览、放大查看，支持 A4 / A3 / A5 纸张与每页目标行数设置。
- **结果保存到本机**：改排失败保留旧版，可恢复默认排版；PDF 保存后可打开与分享。

鼓谱整理接入 [kenqia 的公开在线服务](https://drum.kenqia.top/)，**需要联网**。应用提交公开链接与选项，不向鼓谱服务发送 BBDroid 的 B 站登录 Cookie。

> 适用于 10 分钟以内的公开白底纵向滚动鼓谱。服务结果默认保留 24 小时，已保存到本机的 PDF 独立保留。自动拼接不等于音乐校对，演奏或打印前请逐页核对。详见 [使用说明与边界](docs/drum-score.md)。

### 文件与外观 · 保存之后，随时找回

下载历史集中提供打开、分享和重新下载入口。保存位置可在设置中自定义，默认按内容分类：

| 文件类型 | 默认目录 |
| --- | --- |
| 视频 | `Download/BBDroid/视频/` |
| 音频 | `Download/BBDroid/音频/` |
| 图片 | `Download/BBDroid/图片/` |
| 鼓谱 PDF | `Download/BBDroid/鼓谱/` |
| 封面、弹幕与字幕 | Android 10 及以上：`Download/BBDroid/附赠/` |

支持浅色、深色、跟随系统与可调强度的玻璃主题。近期更新还调整了历史卡片配色、弹簧过渡与底部导航点击效果，详见 [更新记录](CHANGELOG.md)。

## 界面预览

以下为 **v0.5.0** 的界面素材，采集环境为 Android 14 模拟器；当前发布版为 **v0.5.3**。PDF 预览和排版截图使用自绘合成测试谱面。完整来源见 [素材说明](docs/images/v0.5.0/README.md)。

**视频下载与文件处理**

| 视频下载 | 音视频转换 | 下载历史 |
| :---: | :---: | :---: |
| ![v0.5.0 视频下载页面](https://github.com/user-attachments/assets/958325d6-4936-4666-90c9-1cc3be7ecdd7) | ![v0.5.0 音视频转换工具箱](https://github.com/user-attachments/assets/dba53fd8-bce0-4778-9f83-0b2c162ff593) | ![v0.5.0 下载历史](https://github.com/user-attachments/assets/d9de48d1-44a1-4f9e-b455-0ded06c31800) |

**鼓谱输入、预览与打印排版**

| 鼓谱输入 | PDF 分页预览 | A5 打印排版 |
| :---: | :---: | :---: |
| ![v0.5.0 鼓谱链接与选项输入](https://github.com/user-attachments/assets/1671e6f5-4689-4a6d-9778-c50349cf5f92) | ![v0.5.0 原生 PDF 分页预览（合成示例）](https://github.com/user-attachments/assets/860afc7c-db73-4d06-a273-c937db8e4014) | ![v0.5.0 A5 打印预览（合成示例）](https://github.com/user-attachments/assets/087fd349-a0bd-443c-91c0-00647cc19811) |

**主题与参数设置**

| 深色界面 | 应用设置 | 音频参数 |
| :---: | :---: | :---: |
| ![v0.5.0 深色玻璃界面](https://github.com/user-attachments/assets/61f23fda-2b66-4cca-87e5-291034e08b38) | ![v0.5.0 应用设置](https://github.com/user-attachments/assets/c06749f5-a3d3-4663-83c1-944a7d8dcc6d) | ![v0.5.0 MP3 输出与音频参数](https://github.com/user-attachments/assets/da4e11e9-261c-43cc-a6ea-11b8f8e822b6) |

## 快速上手

### 1. 安装应用

前往 [最新 Release](https://github.com/duyuanxi/bbdroid/releases/latest)，下载 `app-release.apk`。当前发布版本为 **v0.5.3**，支持 Android 7.0（API 24）及以上的 **64 位**设备：`arm64-v8a` / `x86_64`。

### 2. 选择你要完成的事

| 我想…… | 操作路径 |
| --- | --- |
| 保存 B 站视频 | 下载页粘贴链接、BV 号或 av 号 → 解析 → 选择分 P、画质与附赠内容 → 下载 |
| 批量保存内容 | 解析分 P 或列表 → 选择批量下载，或将视频加入批量队列 |
| 转格式或提取音频 | 工具箱 → 音视频转换 → 导入文件、多选文件或文件夹 → 设置输出 → 开始转换 |
| 接着处理刚下载的视频 | 下载完成 → 导入转码 |
| 把滚动鼓谱整理成 PDF | 工具箱 → 鼓谱 PDF → 输入链接与选项 → 整理鼓谱 → 预览、排版、保存 |

需要账号权限的内容，请先在设置中登录。下载页输入链接后，也可选择「将链接整理为鼓谱 PDF」直接进入鼓谱工具。

## 源码构建

技术栈：**Kotlin · Jetpack Compose / Material 3 · Room · DataStore · OkHttp · ZXing · FFmpegKit**。

<details>
<summary><strong>构建环境与命令</strong></summary>

需要 JDK 17 或更新版本、Android SDK 34，使用仓库自带的 Gradle Wrapper。项目的 `compileSdk` / `targetSdk` 为 `34`，`minSdk` 为 `24`。

FFmpeg 依赖已随仓库提供：[`app/libs/ffmpeg-kit-full-gpl-8.1.7.aar`](app/libs/ffmpeg-kit-full-gpl-8.1.7.aar)。其原生库仅提供 `arm64-v8a` / `x86_64`，构建已限制为这两种 ABI。

**Windows**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

**macOS / Linux**

```bash
bash ./gradlew :app:assembleDebug
```

Debug 安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。生成已签名的 Release 安装包，需要先完成下方签名配置。

</details>

<details>
<summary><strong>Release 签名配置</strong></summary>

在项目根目录新建 `keystore.properties`：

```properties
storeFile=release.keystore
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

将密钥文件放到 `app/release.keystore`。`keystore.properties` 与 `*.keystore` 已由 `.gitignore` 排除。

</details>

<details>
<summary><strong>测试命令与项目结构</strong></summary>

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug

# 连接独立测试设备或模拟器后运行
.\gradlew.bat :app:connectedDebugAndroidTest
```

既有测试覆盖 15 种输出格式转换、剪辑、文件名兼容、取消与页面切换，以及鼓谱接口、任务恢复、PDF 预览、排版和保存。验证环境与适用范围见 [v0.5.0 验证记录](docs/verification-v0.5.0.md) 和 [v0.4.0 验证记录](docs/verification-v0.4.0.md)。

```text
app/src/main/java/com/bbdroid/app/
├── MainActivity.kt       # 下载 / 工具箱 / 历史与设置入口
├── DownloadTab.kt        # 视频解析、分 P 与批量下载
├── Downloader.kt         # 音视频下载、合并与附赠内容
├── ToolboxTab.kt         # 文件导入、转码参数与媒体信息
├── ToolboxSession.kt     # 转换任务与取消控制
├── drum/                 # 在线鼓谱任务、接口、PDF 预览与保存
├── FfmpegOptions.kt      # 输出格式、编码与命令构建
├── FFmpegEngine.kt       # FFmpeg 引擎调用
├── HistoryTab.kt         # 下载历史、打开与分享
├── Storage.kt            # 文件保存与自定义目录
├── SettingsStore.kt      # DataStore 设置持久化
├── Db.kt                 # Room 数据存储
└── bilibili/             # B 站解析、接口与 WBI 签名
```

</details>

## 开源与致谢

应用代码使用 [MIT License](LICENSE)。FFmpegKit 及其内置编解码器分别遵循各自的开源协议，本项目使用的 `full-gpl` 版本包含 GPL 组件。

在线鼓谱功能基于 [kenqia/bilibili-drum-score-to-pdf](https://github.com/kenqia/bilibili-drum-score-to-pdf) 的公开服务，保留上游 MIT 许可。本版接入在线服务，未移植上游本地视频 CLI 或 AI Agent Skill。详见 [第三方说明](THIRD_PARTY_NOTICES.md)。

---

<p align="center">
  <a href="https://github.com/duyuanxi/bbdroid/releases/latest">下载安装包</a>
  &nbsp;·&nbsp; <a href="CHANGELOG.md">查看更新</a>
  &nbsp;·&nbsp; <a href="docs/drum-score.md">鼓谱使用说明</a>
  &nbsp;·&nbsp; <a href="#bbdroid">返回顶部</a>
</p>
