# 发布步骤

仅在所有要求的构建与验证通过后发布。构建命令：

```bash
./gradlew clean testDebugUnitTest lint assembleDebug
./gradlew connectedDebugAndroidTest   # 有可用设备时
```

核对最终合并 Manifest 以及 APK，确保没有 INTERNET、READ_MEDIA_IMAGES、READ_EXTERNAL_STORAGE、SCHEDULE_EXACT_ALARM 权限，且 allowBackup=false。用 `apksigner verify --verbose` 验证签名，连接设备后安装并启动。

本版本没有正式签名配置，附件为默认 debug 签名 APK。不提交私钥，不将其描述为正式签名版本。

已有推送及 GitHub API 权限时：

```bash
git push origin main
gh release create v1.0.0 app/build/outputs/apk/debug/app-debug.apk \
  --repo J-Ruan/ShiYi --target main --title '拾忆 1.0.0（debug 签名）' \
  --notes-file docs/RELEASE_NOTES.md
```

禁止 force push。实际发布状态记录在最终交付报告；本说明不代表 Release 已创建。即使 Git 读取或推送可用，也不代表 GitHub API 或附件上传可用。
