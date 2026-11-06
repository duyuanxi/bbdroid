# v0.4.0 验证记录

## 环境

- Android 14（API 34）x86_64 独立模拟器。
- JDK 21、仓库 Gradle Wrapper、Android SDK 34。
- 应用：`com.bbdroid.app`，`versionName=0.4.0`，`versionCode=5`。
- Release ABI：`arm64-v8a`、`x86_64`。

## 问题复现

修改前的 MP3 转换测试失败：音频容器沿用了 AAC 与视频编码参数。保存测试也失败：MP3 被标记为 `video/mp4`。

修复后，两项测试通过。15 种输出格式均使用应用内置的 FFmpeg 实际转换，再通过 ffprobe 检查轨道；六种音频输出仅包含音频轨。

## 自动化验证

| 范围 | 数量 | 结果 |
| --- | ---: | --- |
| `FfmpegOptionsTest` | 8 | 全部通过：音频编码、格式默认值、带引号路径、复制流、参数验证、文件名和 MIME |
| `DownloadTest` | 3 | 全部通过：并行分片字节一致且进度单调、错误 Range 回退、取消连接及残留清理 |
| `ConversionTest` | 7 | 全部通过：引擎加载、15 格式转换、剪辑和尺寸帧率、中文引号路径、音频保存、重复导入、取消后再次调用 |
| `NavigationTest` | 3 | 全部通过：页面切换保留输入、UI 发起 MP3 转换后切页继续执行、深色模式 |
| `DownloadSessionTest` | 1 | 通过：取消后等待旧任务清理，结束后才解锁新下载并清空旧状态 |

实际转换的输出格式：MP4、MKV、MOV、AVI、FLV、WMV、WebM、MPEG-TS、MP3、AAC、FLAC、WAV、OGG、M4A、GIF。

验证命令：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.bbdroid.app.test/androidx.test.runner.AndroidJUnitRunner
.\gradlew.bat :app:assembleRelease
```

JVM：11 项通过。Android：`OK (11 tests)`。Debug Lint：0 错误、26 警告；包含旧目标 SDK、依赖升级、图标建议，以及部分依赖的 Lint 检查与现有 AGP API 不兼容，故未宣称所有依赖自带检查均已执行。Release 构建及 Lint Vital 通过。

## 发布包与升级

- `apksigner verify --print-certs` 通过。
- 新包与 GitHub 已发布 v0.3.0 使用相同证书，SHA-256：`22f0ce717388beff4fa86f1c0b08aac4eb6df615b2a83e50176d427c31377726`。
- 在独立模拟器安装 GitHub v0.3.0 APK，再通过 `adb install -r` 安装新 Release，返回 `Success`；安装后版本为 `0.4.0` / `5`。
- 正式包冷启动成功。发布附件提供 APK 与 `SHA256SUMS.txt`。

## 视觉检查与范围限制

- 已检查浅色下载页、工具箱、历史、设置、深色下载页及实际 MP3 转换完成页面的截图，见 [图片目录](images/README.md)。
- 本轮未使用真实 B 站账号执行线上下载；下载传输通过 MockWebServer 验证，不能据此保证所有账号权限、地区、CDN 或内容接口均可用。
- ATD 模拟器没有系统文件选择器；UI 转换测试直接注入测试源文件，内容 URI 导入复制单独测试通过。真实设备系统文件选择器、第三方文档提供方及自定义 SAF 文件夹尚未端到端验证。
- 原生执行验证运行在 x86_64 模拟器；ARM64 真机、Android 7–13、硬件编码器和厂商后台限制尚未逐一实测。低于 Android 12 的系统不执行平台模糊，仍显示透明叠层和边缘高光。
- 测试不代表全部格式与手动编码参数的任意组合都兼容；不兼容组合会显示 FFmpeg 错误。硬件编码取决于设备能力。
