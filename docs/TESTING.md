# 实际验证记录

日期：2026-10-31。环境：Linux，完整 JDK 21，Gradle Wrapper 8.13，AGP 8.9.3，Kotlin 2.1.20，SDK 35。APK 最低 API 26、目标 API 35，包含 arm64-v8a / armeabi-v7a / x86 / x86_64。签名为 Gradle 默认 debug 签名。

## 构建与 JVM 测试

最终冻结源码已重新执行 `./gradlew clean testDebugUnitTest lint assembleDebug`：58 个任务全部执行，32 秒完成，结果 `BUILD SUCCESSFUL`。设备测试 APK 另外构建并在下述分组实际运行。

| 测试组 | 数量 | 结果 |
|---|---:|---|
| LogicTest | 19 | 全部通过，无跳过 |
| CryptoTest | 7 | 全部通过，无跳过 |
| DatabaseTest | 10 | 全部通过，无跳过 |
| MigrationTest | 1 | 通过，无跳过 |
| 合计 | 37 | 0 失败，0 跳过 |

覆盖中文子字符串跨模块搜索与标签检索、日期范围、往年今日、闰日与跨年提醒、未来纪念日首次发生日期、创建/实现时间统计、AES-GCM 随机性与多块还原、错误密码、头部/密文篡改、截断和追加拒绝、PIN 哈希、数据库 CRUD、标签去重、双向关联、删除级联与 SET NULL、重启持久化、事务回滚、JSON + 原图 + 关联 + 设置的加密备份恢复，以及缺失图片时拒绝不完整备份、恢复任务取消后数据库与图片仍保持一致。

迁移测试从版本 1 的结构夹具创建数据库、插入历史心愿，再用生产的 Migration(1,2) 升级并由 Room 校验最终结构。历史数据、备注和状态保留，不虚构心愿实现时间。`schemas/1.json` 是供迁移回归使用的结构夹具，`schemas/2.json` 为 Room 当前导出的 schema。

Android Lint 没有错误；剩余两条建议为保守容量检查使用 usableSpace，以及 Canvas 使用 KTX 辅助函数，不影响构建。没有关闭规则或使用 baseline 隐藏构建错误。

## APK 与隐私检查

- APK 已生成，校验值见 `docs/APK_SHA256.txt`，机器可读结果见 `docs/verification.json`：`app/build/outputs/apk/debug/app-debug.apk`。
- apksigner 验证 v2 签名通过，1 个签名者；非正式发行签名。
- aapt 核对启动 Activity 为 `com.shiyi.memory.MainActivity`。
- 最终 Manifest 没有 INTERNET、READ_MEDIA_IMAGES、READ_EXTERNAL_STORAGE 或 SCHEDULE_EXACT_ALARM 权限。
- 关闭 allowBackup；cloud-backup 与 device-transfer 排除应用数据。
- SDK / Gradle / JDK 下载保留 TLS 与官方校验，没有绕过验证。
- `scripts/cloud-setup.sh` 已实际执行成功，能够重复准备并构建环境。

## Android 模拟器

使用 AOSP Android 11 / API 30、x86_64、320×640 软件模拟器，无 /dev/kvm。安装成功。首次启动期间系统 UI 曾发生 ANR；在系统完全启动后，独立回忆 CRUD 和四页面导航测试通过。后续分组验证见下方最终结果。

一次完整设备测试运行中，应用锁的后台重锁、错误密码拒绝与正确解锁通过，随后下一项 UI 测试遭遇系统 5 秒焦点分发超时，运行器以 keyDispatchingTimedOut 终止。该次运行仅 1 项通过、1 项失败、7 项未执行，不能将该次整套测试描述为通过。随后对最终 APK 预编译并分组重试，避免该超时阻止独立功能验证。

最终按独立运行器分组验证，以下 **11 项设备测试全部通过，0 跳过**：

| 分组 | 数量 | 结果 / 验证内容 |
|---|---:|---|
| 回忆 CRUD 与四个主页面 | 1 | 新增、查看、修改、删除、页面切换通过 |
| 多图浏览 | 1 | 原图加载、全屏进入、实际左右翻页通过 |
| 截图 / 主题 / 旋转 | 1 | 五张真实截图；深色切换、旋转后草稿保留通过 |
| 应用锁 | 1 | 已打开弹窗时后台重锁、错误 PIN、正确解锁通过 |
| 平台功能 | 7 | 原图哈希及分享 MIME、多页中文 PDF、长截图分页、损坏备份拒绝、隐私权限、本地通知、彻底删除通过 |

长截图回归曾暴露 PNG 未定义 EXIF 方向时跳过分段的问题，已修复并保持原断言；最新测试实际渲染 PDF，确认首段和末段的色块均完整，防止截断。彻底删除测试还检查媒体文件已删除、设置复位、数据库不再含测试内容、WAL 长度为 0。

**合计 48 项自动化场景最终通过（37 项 JVM + 11 项设备分组），没有将未执行、初始化失败或跳过计入通过。** 设备分组使用 AndroidJUnitRunner 的 `OK (N tests)` 结果判定，Gradle 早期失败记录保留为故障诊断历史。最终真实截图在 `docs/screenshots/`；只包含空数据和合成内容。


## 仍需真机验收

本云环境没有荣耀 90 真机、Android 13+ 通知授权设备或已录入生物识别硬件。以下仍需在相应真机实际验证：通知授权 / 拒绝、指纹或人脸对话框、荣耀电池策略、重启与时区变化下的真实闹钟投递、多款文件提供器与原生 Photo Picker、真正低存储空间、超大图库的长时间性能与电池表现。应用锁不是数据库/媒体加密；软件模拟器不构成生产性能基准。
