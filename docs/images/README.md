# README 图片素材

- `score-input.png`：v0.5.0 正式签名包在 Android 14 模拟器运行的实际鼓谱入口截图。
- `score-preview.png`、`score-layout.png`：v0.5.0 的鼓谱原生界面截图，由 Android Compose 测试捕获。预览 PDF 为自绘合成测试谱面，只验证界面、翻页、改排与保存，不是实际曲谱质量展示。

- `download.png`、`toolbox.png`、`history.png`、`dark.png`、`settings.png`、`conversion-done.png`：v0.4.0 代码通过 Android 仪器测试从 `com.bbdroid.app` 真实运行界面截取，原始尺寸 1080 × 2400。模拟器为 Android 14 x86_64；其中转换结果为实际生成并保存的 MP3。
- `bbdroid-poster.png`：沿用上一版的宣传海报，使用内置 imagegen 工具生成，合成素材为当时的下载页与工具箱截图。当前版本界面以本目录独立截图为准。

## 海报生成提示词

```text
Use case: ads-marketing.
Asset type: a finished landscape GitHub README promotional poster for the Android app BBDroid, approximately 16:9.
Input images: Image 1 is an authentic BBDroid download screen, supporting compositing input. Image 2 is an authentic BBDroid FFmpeg toolbox screen, supporting compositing input. These are real application screenshots, not style inspiration.
Create a polished Chinese software launch poster. Pale ice blue and silver-white glass materials drawn from these actual app screenshots, navy typography, crisp electric blue accent, generous negative space, sophisticated editorial product photography composition. On the left put the product name and large Chinese headline. On the right show two beautiful slim upright Android phone frames containing the supplied screenshots, large enough to recognize their real interface. Place screens flat and sharp with very slight depth and subtle soft shadows. Preserve the screenshots' layout, wording, buttons and visual identity faithfully; do not invent or redesign interface elements. The download and toolbox screens should both be identifiable.
Exact poster copy, no additional claims:
"BBDroid"
"下载、转码，一站完成。"
"B 站视频下载器 + FFmpeg 工具箱"
"批量下载 · 原画质保存 · 音视频转换"
"Android"
"github.com/duyuanxi/bbdroid"
Set Chinese headline in clean large bold modern sans serif, product name larger, supporting copy smaller with excellent hierarchy and legibility. Put the repository address neatly at bottom left. Avoid fake QR codes, unrelated logos, fake ratings, pricing, invented version numbers, watermarks, confetti or visual clutter. This is one cohesive premium poster, not a dashboard or a grid of cards.
```
