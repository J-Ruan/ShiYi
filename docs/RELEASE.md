# 1.1.0 正式签名与发布

正式签名是发布硬条件。仓库从未提交 keystore；当前云环境未提供正式密钥，GitHub Secrets 读取接口返回 403，不能据此断言仓库没有 Secrets。没有可用正式签名时只构建 unsigned Release，绝不创建稳定版 Tag / Release，也不将 debug 附件冒充正式版。

实际发布尝试：[Signed release 41277945397](https://github.com/J-Ruan/ShiYi/actions/runs/41277945397)。发布前测试通过，签名步骤明确停止于 `Missing required signing configuration: KEYSTORE_BASE64`，即工作流读取不到 `ANDROID_KEYSTORE_BASE64` Secret。未创建 Tag / Release。配置完成后重新运行此工作流即可继续，无需改用 Debug 签名。

签名门禁已用真实 Debug APK 和未签名 APK 做反例验证，均拒绝；即使把 Debug 证书指纹配置成预期指纹，也不会将它认作正式证书。

## 一次性准备（在自己可信的电脑上）

1. 如果已有发行密钥，继续使用它，跳过生成步骤。新项目只生成一次：

```bash
keytool -genkeypair -v -keystore shiyi-release.jks -alias shiyi \
  -keyalg RSA -keysize 3072 -validity 10000
```

按提示设置强密码。将密钥、别名和密码分别长期安全保存；至少保留两份加密离线副本。不要将文件复制进仓库，不要每次构建重新生成。丢失后无法继续同签名升级。

2. 打开 [仓库 Actions Secrets](https://github.com/J-Ruan/ShiYi/settings/secrets/actions)，配置：

| Secret | 内容 |
|---|---|
| ANDROID_KEYSTORE_BASE64 | keystore 文件的 Base64（整份文件） |
| ANDROID_KEYSTORE_PASSWORD | keystore 密码 |
| ANDROID_KEY_ALIAS | 密钥别名，例如 shiyi |
| ANDROID_KEY_PASSWORD | 对应密钥密码 |

可以用 GitHub CLI 直接从文件写入，避免把 Base64 打印到终端：

```bash
base64 -w 0 shiyi-release.jks | gh secret set ANDROID_KEYSTORE_BASE64 --repo J-Ruan/ShiYi
gh secret set ANDROID_KEYSTORE_PASSWORD --repo J-Ruan/ShiYi
gh secret set ANDROID_KEY_ALIAS --repo J-Ruan/ShiYi
gh secret set ANDROID_KEY_PASSWORD --repo J-Ruan/ShiYi
```

macOS 使用 `base64 < shiyi-release.jks`。后三条命令在提示中输入值，不在命令参数里放密码。

3. 使用 `keytool -list -v -keystore shiyi-release.jks -alias shiyi` 核对证书 SHA-256。在 [Actions Variables](https://github.com/J-Ruan/ShiYi/settings/variables/actions) 添加 `ANDROID_CERT_SHA256`，填证书指纹（允许冒号）。它是公开指纹，用于防止误用其他证书。

4. 在 [Environments](https://github.com/J-Ruan/ShiYi/settings/environments) 创建 `production`，按自己的维护策略设置发布保护。确保 Actions 允许该仓库工作流写入 Contents。

## 执行发布

在 [Signed release 工作流](https://github.com/J-Ruan/ShiYi/actions/workflows/release.yml) 点击 Run workflow，选择已审核的 main 提交，version 填 `1.1.0`。该流程先在全新模拟器运行完整设备测试，再运行 JVM / Lint、正式签名构建、apksigner 验证、指纹比对、版本与非调试检查，并在全新模拟器安装启动正式 APK。全部通过才创建 `v1.1.0` 和 Release，上传 `ShiYi-1.1.0-release.apk` 与 `SHA256SUMS.txt`。已有 Tag 或 Release 会被拒绝，不覆盖旧版本。密钥放在 Runner 临时目录中，步骤退出时删除。

本地已有正式密钥时，也可以仅通过环境变量 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 构建；不要提交带密码的 shell 配置或 Gradle properties。

```bash
./gradlew clean testDebugUnitTest lint assembleDebug assembleRelease
# 验证真正的 app-release.apk；没有签名配置时产物是 app-release-unsigned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

## 从旧 Debug 版迁移

Debug 与正式签名不能覆盖安装，applicationId 仍为 `com.shiyi.memory`。先在旧版导出 `.shiyi`，用原密码执行“选择备份并校验”，确认文件完整且复制到可靠位置；再卸载旧版、安装正式版、恢复备份。卸载会删除内部数据，切勿省略备份。新手机会生成自己的本地加密密钥；不会复用原设备 Keystore，PIN、通知等安全设置需在新手机自行配置。
