# 1.1.0 验证记录

2026-11-01。应用源码 `d5e7904`，截图提交 `5c41c79`；后续签名门禁脚本单独验证。当前仍为开发候选，不将未签名产物称为稳定发行版。设置文件损坏用例已独立、全套及 CI 运行通过，最终为 45 项 JVM 测试。

## 实际结果

[GitHub Actions 成功运行](https://github.com/J-Ruan/ShiYi/actions/runs/41278695985)：build 和 device 两个任务均成功。

| 检查 | 结果 |
|---|---|
| clean / testDebugUnitTest | 45 项通过，0 失败、0 跳过 |
| lint | 0 错误；4 项非阻断建议（依赖更新、可用空间及 KTX） |
| assembleDebug / assembleRelease / assembleDebugAndroidTest | 成功；Release 当前未签名 |
| API 30 connectedDebugAndroidTest | 20 项通过 |
| 主要页面截图 | 18 张合成数据截图已生成、提交并逐页检查 |
| Debug APK 签名、安装启动 | 签名有效，独立模拟器安装 / 启动通过；仅工程测试用途 |
| 正式 Release 签名 / 安装 | 未完成，依赖可用正式签名配置 |
| 发布门禁反例 | 真实 Debug APK（即使指纹匹配）和未签名 APK 均被拒绝 |

CI 实际执行 `./gradlew --no-daemon clean testDebugUnitTest lint assembleDebug assembleRelease assembleDebugAndroidTest`，另在全新硬件加速 API 30 模拟器执行完整设备套件。没有将 JVM fixture 当作真实 SQLCipher 证明。

## 覆盖范围

JVM：业务 / 搜索 / 日期 / 统计 / 快照校验 19；备份密码 / PIN / 篡改 / 截断 7；图片认证分块 3；数据层 14；schema 1→3 迁移 1；真实损坏设置文件的明确重置及回忆保留 1。数据层包括 3,000 条合成记录、索引日期窗口、事务回滚、共用图片、回收站备份、本机设置保留、清理确认后新增引用的保护，以及最近导入文件和实际可释放空间的区分。

设备 20 项：

- PlatformTest 8：三张原图 SHA-256、真实分享 MIME、中文长文 PDF、长截图首尾像素 / 多页 / 可读宽度、坏备份保护现有记录、权限与自动备份、通用通知、彻底删除与空安装保护。
- SecurityUpgradeTest 5：真实 SQLCipher / Keystore / 图片认证；旧 schema 2 全关系加密迁移；提交前 / 提交后中断重试；缺图失败保留旧数据库与原图；旧备份在另一组随机本地密钥下恢复并保留本机安全配置；分享导入中断恢复。
- AppSmokeTest 6：四个主标签、增改删与回收站恢复、旋转保留表单、深色模式、PIN 后台锁及错误密码、多图滑动、真实系统图片选择器取消、ACTION_SEND_MULTIPLE 接收及重复 URI 去重。
- VisualTest 1：18 个页面 / 主题截图；使用合成文字和生成 PNG。详见 [截图索引与检查](screenshots/1.1.0/README.md)。

系统分享支持 ACTION_SEND 和 ACTION_SEND_MULTIPLE；本轮端到端设备用例使用多图分享，单图路径共用 URI 校验与复制入口，未另做第三方应用单图分享的人工真机验收。

本地无 /dev/kvm，独立软件模拟器曾发生系统服务尚未就绪、安装失败，待服务就绪后安装成功。真实加密设备用例初轮 4/5 通过；发现并修复新加密附件数据库不能创建的问题后，完整迁移用例实际重跑 `OK (1 test)`。最终完整 20 项以以上成功 CI 为准，不混入早期失败当作通过。

## 数据与视觉检查

旧明文库 / 原图均为独立目录的合成样本。迁移后检查记录与关系、原图哈希、密文文件头、再次打开、旧文件最终清理。没有读取或迁移私人日常手机数据。

截图检查后修复了重复页边距、冗余提醒说明、无意义档案建立日期、空首页重复模块，以及搜索 / 档案筛选默认展开造成的拥挤；第二轮全套设备测试通过并重新生成截图。图中页面由实际 Compose 页面及同一主题渲染，不是设计稿；主导航、选择器、应用锁及旋转由 AppSmokeTest 另外验证。测试专用宿主允许捕获合成页面，正式 MainActivity 的 FLAG_SECURE 保留。

## 未执行与限制

没有在荣耀实机、生物识别硬件、Android 8 / Android 13+ 权限流程或超大媒体 / 低空间压力环境做人工验收。跨密钥恢复使用同一模拟器的隔离存储上下文，不冒称两台实体手机。正式签名 APK 尚待配置后由发布工作流验证和安装；本轮未生成 AAB。

[实际正式发布尝试](https://github.com/J-Ruan/ShiYi/actions/runs/41277945397) 的 verify / test 通过，release 步骤停止于 `Missing required signing configuration: KEYSTORE_BASE64`。该变量来自 `ANDROID_KEYSTORE_BASE64` Secret，当前不可用；没有创建正式 APK、Tag 或 Release。

设备测试只允许全新模拟器或专用空安装。TestDeviceGuard 拒绝已有资料、媒体或个人设置，不能移除保护来通过测试。1.0.0 历史结果不作为本轮结果。
