# 拾忆 APK 下载

[下载拾忆 1.1.0 测试 APK](https://github.com/J-Ruan/ShiYi/raw/refs/heads/main/downloads/ShiYi-1.1.0-debug.apk?download=1)（38.89 MiB / 40,775,574 字节）

**这是可安装的 Debug 签名测试版，不是正式稳定版。** Android 8.0+（API 26），包名 `com.shiyi.memory`，versionCode `2`。

该测试包与仓库旧 1.0.0 APK 的签名证书不同，不能直接覆盖旧包。请先在旧版导出并验证 `.shiyi` 加密备份，可靠另存，再卸载旧版、安装测试包并恢复。不要在未验证备份前卸载。

SHA-256：`0193d3bc695ac3f731f6da28e6d48e44f90d9dafcf6b60c6b0432281e8d8e310`。完整 [SHA256SUMS](SHA256SUMS)。

同版本代码已通过 [45 项 JVM、20 项设备测试](https://github.com/J-Ruan/ShiYi/actions/runs/41278695985)。权限、加密迁移、备份与 UI 验证范围见 [测试记录](../docs/TESTING.md)。

本次 Release 附件上传接口返回 HTTP 401，故按用户下载需求通过仓库文件分发测试包；没有创建公开的测试 Release。正式签名仍需按 [发布说明](../docs/RELEASE.md) 配置，正式 APK 使用 Release 附件分发。

历史下载：[1.0.0 Debug APK](https://github.com/J-Ruan/ShiYi/raw/refs/heads/main/downloads/ShiYi-1.0.0-debug.apk)。旧版本只作历史保留。
