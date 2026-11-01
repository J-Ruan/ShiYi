# 1.1.0 验证记录

2026-11-01，当前为开发候选，未正式发布。

## 已实际执行

- `clean testDebugUnitTest lint assembleDebug assembleRelease assembleDebugAndroidTest`：构建成功。
- JVM 44 项通过、0 失败：业务逻辑 19、备份 / PIN 加密 7、图片分块加密 3、数据层 14、schema 1→3 迁移 1。
- 包括 3,000 条合成记录的日期窗口、回收站恢复 / 共用图片、回收站备份、本机设置保留及清理前引用复核。
- JVM 数据层使用隔离明文 SQLite fixture 与测试密钥，**不代表真实 SQLCipher / Keystore 已通过设备验证**。
- Lint 完成，无构建阻断错误；详细建议以实际报告为准。
- Debug、未签名 Release 和 androidTest APK 已生成。未签名 Release 不可作为正式发行 APK。

## 设备验证

设备测试已扩展：PlatformTest（原图、MIME、PDF 中文 / 长截图、备份损坏、隐私权限、提醒、擦除与空安装保护）；SecurityUpgradeTest（真实 SQLCipher / Keystore、旧数据加密迁移、中断重试、缺图失败、图片篡改、共用引用）；AppSmokeTest（CRUD / 回收站、锁、旋转、多图、系统分享、选择器取消）；VisualTest（18 页合成数据截图）。

当前云环境没有 /dev/kvm。独立 API 30 软件模拟器首次启动后系统服务未就绪，已重启诊断；本轮设备结果及截图尚未完成，**不能声称上述测试已经通过**。GitHub Actions 使用全新硬件加速 API 30 模拟器补充执行。后续结果追加到此文档。

没有接触日常手机或真实照片。每次设备测试先检查空安装；数据层测试使用独立数据库、目录和合成样本。不得删除已有真实数据来通过测试。

## 发布门槛

本轮真实加密迁移、核心 UI、截图检查与 GitHub Actions 仍需实际通过。当前环境未提供正式签名密钥；读取仓库 Secrets 的 API 返回 403，无法证明其配置状态。正式签名校验和正式 APK 安装尚未执行。

没有创建 v1.1.0 Tag / 稳定 Release，没有上传假冒正式 APK。签名配置见 [RELEASE.md](RELEASE.md)。生物识别硬件、荣耀实机、Android 13+ 授权、低空间 / 大规模媒体压力尚未实机执行，不以推测代替结果。

1.0.0 历史测试结果不作为 1.1.0 的验收结果。
