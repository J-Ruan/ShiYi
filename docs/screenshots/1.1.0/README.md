# 1.1.0 合成数据视觉检查

这些图片由 API 30 上的真实 Compose 页面组件自动生成，采用应用相同的 ColorScheme / Typography 和背景。测试样本只有生成图形与合成文字，没有私人照片、聊天或用户资料。截图不包含主导航宿主；完整主导航与系统交互另由 AppSmokeTest 验证。源码 d5e7904，截图提交 5c41c79。

| 页面 | 图片 | 检查结果 |
|---|---|---|
| 首页 | [home](home.png) | 图片、标题、日期清晰；隐藏无内容的重复模块 |
| 回忆列表 | [memories](memories.png) | 高级筛选折叠；卡片不堆叠描述 / 标签 |
| 详情 | [detail](detail.png) | 操作与元信息清晰，多图可继续滚动 |
| 快速编辑 | [editor](editor.png) | 标题、图片、描述、日期；更多细节折叠 |
| TA 的档案 | [profile](profile.png) | 去除建立日期，筛选折叠，记录可直接看到 |
| 日历 | [calendar](calendar.png) | 仅选中日期强调，月份操作及中文日期清晰 |
| 合集 | [collections](collections.png) | 标题和数量，统一页边距 |
| 统计 | [statistics](statistics.png) | 全部 / 月 / 年入口明确，数值对齐 |
| 设置 | [settings](settings.png) | 名称、状态、入口；提醒说明简短 |
| 备份 | [backup](backup.png) | 保留密码与覆盖风险说明，去除格式实现细节 |
| PDF | [pdf](pdf.png) | 导出选项统一，选择数量和动作清晰 |
| 心愿 | [wishes](wishes.png) | 标题、分类及单一状态 |
| 纪念日 | [anniversaries](anniversaries.png) | 日期和管理提醒入口，无大段说明 |
| 搜索 | [search](search.png) | 先显示搜索结果，日期 / 分类筛选折叠 |
| 存储 | [storage](storage.png) | 数量与空间对齐，保留安全清理规则 |
| 回收站 | [trash](trash.png) | 简短空状态，清空动作禁用状态明确 |
| 深色首页 | [dark](dark.png) | 背景、卡片、文字和强调色可区分 |
| 深色编辑 | [dark-editor](dark-editor.png) | 输入边界与主要动作清晰，中文未截断 |

全部 18 张逐页检查：无标题解释性副文案；没有装饰性彩色徽章堆积；主要字号为 26 / 20 / 17 / 14–16sp，未靠缩小字号压缩信息；保留需要滚动的长页面内容。图片为自动化验收样本，不会预装进入用户数据库。
