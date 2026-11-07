# 第三方说明

## 鼓谱整理

- 项目：https://github.com/kenqia/bilibili-drum-score-to-pdf
- 作者与版权：Copyright (c) 2026 kenqia
- 在线服务：https://drum.kenqia.top/
- 接口与行为依据：上游提交 `7327301b75469f6f48c286bf381883654658dae9` 中的 `web.py`、`jobs.py`、`conversion_options.py`、`static/app.js` 和网站文档；接入时已读取线上同版 `app.js` 核对。
- BBDroid 使用 Kotlin 实现在线客户端，链接规范化参考上游 MIT 代码。保留完整 MIT 许可于 [应用资源](app/src/main/assets/licenses/drum-score-MIT.txt)，界面中提供查看入口。
- 拼接、去重、谱区检测和 PDF 排版由该第三方服务执行。BBDroid 未移植 Python/OpenCV 算法；这一接入不包含上游 CLI 的本地视频处理和 AI Agent Skill。无需也不会向该服务发送 BBDroid 的 B站登录 Cookie。
- 应用只在用户提交时发送公开链接与转换选项；之后使用任务专属访问凭据查询、取消、重试和取回结果。任务凭据存放在 Android 私有、不参与备份的目录，不进入日志和公开历史。
- 应用内渲染下载的同一份 PDF 进行分页预览；改排失败保留旧版。在线服务的可用性、处理质量、配额和 24 小时结果期限由其运营者控制。

已有 FFmpegKit 与其他依赖继续遵循各自许可，见 README 与各依赖分发包。
