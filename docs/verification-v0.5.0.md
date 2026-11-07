# v0.5.0 验证记录

## 自动化验证

环境：Android 14 / API 34 x86_64 独立模拟器，JDK 21、仓库 Gradle Wrapper。

| 测试 | 项数 | 验证内容 |
| --- | ---: | --- |
| `FfmpegOptionsTest` | 8 | 转码参数、编码、路径、音频容器和 MIME |
| `DownloadTest` | 3 | 分片完整性、回退与取消 |
| `ConversionTest` | 7 | 内置 FFmpeg 实际转换全部 15 种格式，剪辑、尺寸帧率、文件名、保存与取消 |
| `DownloadSessionTest` | 1 | 停止后等待旧任务清理 |
| `NavigationTest` | 5 | 原页面切换与转换、鼓谱入口、页面重建后保留所选页面和编辑过的分 P 链接 |
| `ScoreApiTest` | 6 | 链接与选项契约、凭据隔离、提交／取消／重试、限流／过期、错误文件与不完整排版拒绝、请求中断 |
| `ScoreSessionTest` | 1 | 私有任务记录恢复、改排失败保留旧 PDF、实际 MediaStore PDF 保存与字节一致性 |
| `ScoreUiTest` | 1 | 原生表单提交、PDF 翻页、A4 两页改排为 A5 一页、保存及 MIME |

总计 **32 项通过**：11 项 JVM 测试、21 项 Android 测试。Android Runner 返回 `OK (21 tests)`。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.bbdroid.app.test/androidx.test.runner.AndroidJUnitRunner
```

Debug / Release 构建、Release Lint Vital 通过。Debug Lint 为 0 错误、25 警告，仍有旧 SDK、依赖升级、图标及部分依赖检查与现有 AGP API 的兼容提示；不代表所有依赖内置 Lint 检查均已运行。

## PDF 与线上服务

- 鼓谱客户端自动化测试使用 MockWebServer 和自绘合成谱面，不访问真实账号，也不凭测试替身宣称算法质量。
- Android PdfRenderer 实际打开两页 A4 与一页 A5 PDF；通过 UI 翻页、改排和保存。导出的三页均使用 Poppler 渲染并目视检查，无裁切、重叠或乱码。
- 接入时读取线上 `app.js`，核对上游真实接口路径、方法、字段和 Bearer 认证方式。
- 另用上游文档中的公开视频 `BV1rH4y1R7Rk` 做真实服务验证：提交 `[0, 60)` 秒选段，服务完成拼接与排版并返回 `complete`、1 页结果。该验证由主机 HTTP 客户端执行，与原生 UI 的替身测试分开记录；没有据此宣称原生客户端的真实网络流程已完整端到端验收。
- 真实任务的访问凭据与响应只保留在本机忽略目录，未写入源码、日志附件或截图。

## 发布与范围

- 应用包名保持 `com.bbdroid.app`，版本 `0.5.0` / `6`，ABI 为 `arm64-v8a` / `x86_64`。
- 使用原发布密钥；证书 SHA-256 为 `22f0ce717388beff4fa86f1c0b08aac4eb6df615b2a83e50176d427c31377726`。
- 在独立模拟器先安装 v0.4.0 正式包，再用 `adb install -r` 安装 v0.5.0，返回 `Success`；版本为 `0.5.0` / `6`，冷启动成功。
- 正式 APK 与 SHA-256 校验文件作为 GitHub Release 附件发布，源码不包含签名密钥、密码、本机路径或真实任务凭据。
- 本轮图像处理与排版由原项目的第三方服务完成；Android 客户端不包含其 Python/OpenCV、本地视频 CLI 或 Agent Skill。服务可用性、配额、结果期限与源视频适用性仍受上游限制。
- 真机 ARM64、Android 7–13、第三方文件提供方及系统分享接收应用未逐一实测。应用内默认目录保存已在 API 34 验证；系统文件选择器限制沿用 v0.4.0 验证记录。

原生鼓谱界面和合成 PDF 预览截图见 [图片说明](images/README.md)，功能与资源范围见 [鼓谱说明](drum-score.md)。
