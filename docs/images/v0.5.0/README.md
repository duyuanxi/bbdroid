# BBDroid v0.5.0 图片素材

## 来源

- 版本：`versionName=0.5.0`、`versionCode=6`；对应源码提交 `6aca75c`。
- 正式 APK 的 SHA-256：`4af5cce281da13c466c7f5a4711700f77979ac7b771ba640c87d2d2d0c014269`，与 GitHub v0.5.0 Release 附件一致。
- `download.png`、`toolbox.png`、`history.png`、`dark.png`、`settings.png`、`audio-conversion.png`、`score-input.png`：本次通过 ADB 从上述正式发布包的真实运行界面重新采集，环境为 Android 14 / API 34 x86_64 模拟器，原始尺寸 1080 × 2400。未修改截图内容。
- `audio-conversion.png` 展示 MP3 输出与音频参数配置，不是转换成功结果截图。
- `score-preview.png`、`score-layout.png`：保留并归档此前 v0.5.0 的原生界面测试截图；源于同一版的 `ScoreUiTest`。PDF 为自绘合成测试谱面，不是实际歌曲或在线服务处理效果样例。见 [v0.5.0 验证记录](../../verification-v0.5.0.md)。
- `bbdroid-poster.png`：本次使用内置 imagegen 工具生成，合成素材为上述正式版下载页、转换页及鼓谱入口截图；明确展示 v0.5.0 与在线鼓谱 PDF 功能。
- README 使用通过 GitHub 编辑器实际上传得到的附件链接，以避免此前仓库原始图片链接加载超时。原始图片同时保存在此目录。

## 海报生成提示词

```text
Use case: ads-marketing.
Asset type: finished landscape GitHub README hero poster, 16:9, for BBDroid v0.5.0.
Primary request: Update the promotional poster to the actual latest released Android app version, prominently featuring the new online drum-score PDF capability together with Bilibili downloading and audio/video conversion.
Input images: Image 1 is the actual v0.5.0 release APK download screen. Image 2 is the actual v0.5.0 release APK audio/video conversion toolbox screen. Image 3 is the actual v0.5.0 release APK drum-score PDF screen. All three are authentic screenshots for compositing, not mockup design inspiration.
Style: premium restrained Chinese software product advertising. Pale ice blue, silver white and glass textures from the supplied real app UI; navy typography, vivid blue accents, very subtle studio shadows, spacious editorial design. Maintain the visual identity of the supplied app.
Composition: left approximately 40% large product typography and product copy; right approximately 60% three slender elegant Android phone frames showing the three exact provided screens. Make the drum-score PDF phone slightly more prominent. Phones mostly frontal, restrained small overlaps without obscuring the new screen's title and form. Screens stay sharp and recognizable with faithful original UI. No invented charts, tasks, output PDF, video content, added controls or redesigns.
Exact external poster text:
"BBDroid"
"v0.5.0"
"下载、转码、鼓谱"
"一站完成。"
"B 站视频下载 · 音视频转换 · 在线鼓谱 PDF"
"PDF 预览 · 打印排版 · 本地保存"
"Android 7.0+"
"github.com/duyuanxi/bbdroid"
Use large legible bold Chinese sans-serif typography and strong hierarchy. Main headline may split over two lines exactly as provided. Version should be an obvious compact blue badge beside the product name. The smaller feature line may wrap gracefully. Repository address at bottom left.
Preserve screenshot wording, proportions, navigation, and all interface elements. For the drum score screenshot especially keep "鼓谱 PDF" and "把鼓谱带到谱架上" legible and the real form visible. Never fabricate successful processing or any music score.
Avoid QR codes, invented ratings, guarantees, pricing, unrelated symbols, watermarks or clutter. Render one cohesive polished poster.
```
