# 参与开发

使用 JDK 21、Android SDK 35 / Build Tools 35.0.0，以及仓库 Gradle Wrapper。执行 `./gradlew clean testDebugUnitTest lint assembleDebug assembleRelease`。

设备测试只允许使用全新模拟器或专用空安装。TestDeviceGuard 拒绝已有资料、图片和个人设置的设备；不得移除保护来通过测试。JVM 数据层测试使用独立明文 SQLite fixture 与测试密钥，真实 SQLCipher / Keystore / 旧数据加密迁移由 Android SecurityUpgradeTest 验证。

保持 API 26、原 applicationId、完全离线、无 INTERNET、无 AI / OCR / 广告 / 追踪。UI 不直接访问 DAO。新增字段需提交显式迁移和 schema，不能 destructive migration。涉及图片必须经过 ImageStorage；不可长期保留解密副本、记录用户内容或私密路径。

提交前核对差异，不提交 keystore、令牌、真实照片、数据库、私人备份、APK / AAB。发行安装包使用 Release 附件，维护原 MIT 许可证。
