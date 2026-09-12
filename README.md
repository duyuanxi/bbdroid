# BBDroid

单机 Android 应用：**B 站视频下载器 + FFmpeg 工具箱**（个人自用，无后端，无任何 API 密钥）。

## 功能

- **B 站下载**
  - 单个视频 / 分P 批量 / 合集·系列·番剧·收藏夹·UP 投稿批量下载
  - 清晰度与编码选择（8K / HDR / 4K / 1080P60 等，AVC/HEVC/AV1）
  - 音视频流自动合并（FFmpeg -c copy 无损封装）
  - 附赠内容可选：封面 / 弹幕(XML) / 字幕(SRT)
  - 登录（网页 / 扫码）解锁大会员内容
  - 分段并行下载 + 备用 CDN 自动切换 + 断点重试
  - 下载进度（含批量总进度与单条进度）、下载历史、分享/打开/重新下载
- **FFmpeg 工具箱**
  - 转码（H.264/H.265/AV1，CRF/码率/分辨率/帧率/音频参数）
  - 封装/分离、剪辑、滤镜、压缩，ffprobe 元信息
  - 单文件 / 批量 / 文件夹导入
- **主题**：普通主题 / 液态玻璃主题（可调折光强度）

## 技术栈

Kotlin · Jetpack Compose (Material3) · ffmpegkit-maintained（FFmpeg AAR）· OkHttp · ZXing

## 构建

要求：JDK 17+、Android SDK（compileSdk 34 / build-tools）。

```bash
# 未签名 Debug（16KB 页面对齐用于较新设备）
./gradlew assembleDebug

# Release（需先配置签名，见下）
./gradlew assembleRelease
```

> 依赖本地 FFmpeg AAR：app/libs/ffmpeg-kit-full-gpl-8.1.7.aar（约 35MB，随仓库分发）。
> 若需要其它 FFmpeg 变体，请从 ffmpegkit-maintained 下载对应 AAR 放入 app/libs/ 并修改 app/build.gradle.kts。

### Release 签名

签名信息从不入库的 keystore.properties 读取。在项目根目录新建该文件：

```properties
storeFile=release.keystore
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

把对应的 .keystore 放到 app/ 下即可（两个文件都在 .gitignore 中，不会被提交）。

## 目录结构

```
bbdroid/
├── app/
│   ├── build.gradle.kts
│   ├── libs/ffmpeg-kit-full-gpl-8.1.7.aar
│   └── src/main/java/com/bbdroid/app/
│       ├── MainActivity.kt          # 底部导航：下载 / 工具箱 / 历史
│       ├── DownloadTab.kt           # B 站解析与下载
│       ├── ToolboxTab.kt            # FFmpeg 工具箱
│       ├── HistoryTab.kt            # 下载历史
│       ├── Glass.kt                 # 液态玻璃组件
│       ├── DownloadSession.kt       # 下载会话（后台存活）
│       ├── Storage.kt               # MediaStore 存储
│       └── bilibili/                # B 站接口 / WBI 签名 / 解析
└── build.gradle.kts
```

## License

MIT（见 LICENSE）。FFmpegKit 及其内置编解码器遵循各自开源协议（full-gpl 版本为 GPL）。
