# 拾忆 1.1.0 交付报告

开发、主要自动化验收和源码推送已完成；**正式发布尚未完成**。唯一正式发布硬阻塞为可用的长期签名配置：实际工作流读取不到 `ANDROID_KEYSTORE_BASE64`，已在签名预检明确停止，未生成假冒正式版。

## 实现与改造

- 快速记录、更多细节折叠、继续添加；ACTION_SEND / SEND_MULTIPLE 图片导入、去重、格式 / 读取失败处理、私有复制、持久分享草稿及进程中断后的已复制部分恢复。
- 回收站保留 30 天、恢复、永久删除、清空与短时撤销。关系与原图随回收站进入备份，共用图片不会因一条回忆删除而丢失。
- 图片数量 / 空间 / 分类占用 / 最近备份 / 未使用数量 / 可释放空间；文件清单预览与确认时重新检查引用，保护分享草稿和最近导入文件。
- 全局字号、暖色、圆角及页边距统一；删去副标题、说明和卡片描述堆叠；首页空模块隐藏，搜索 / 档案 / 回忆高级筛选折叠。保留关键隐私风险说明。18 页实际截图已逐页检查。
- SQLCipher 数据库、AES-256-GCM 原图、Android Keystore 包装随机主密钥，与 PIN 独立；按需认证解密，受控分享 / PDF 临时明文及清理。
- 迁移先复制加密，核对完整性、所有表数量、外键及原图 SHA-256，再提交并清理旧文件；中断可恢复，缺图失败保留原数据。
- 备份清单 2 兼容 1.0.0 清单 1；新设备重新加密，保留当前 PIN、生物识别、通知和备份时间 / 位置。PDF 增加目录、月份分隔页与末页页码。
- 修复设置读取失败无限加载、文件选择器应用锁会话、旋转 / 冷启动恢复时机、日历跨月选择、直接 DAO 访问及重复标签扫描。
- 修复 SDK tools 安装失败；更新 GitHub Actions 的 Node 24 版本；建立正式签名、指纹、安装验证及 Release 附件流程；保留原 MIT，随 APK 打包第三方许可。

版本 `1.1.0`，versionCode `2`，applicationId `com.shiyi.memory`，数据库 schema `3`，最低 API `26`。无 INTERNET、广泛相册权限、服务端、AI / OCR、追踪或广告。

## 验证与迁移结果

[成功 CI](https://github.com/J-Ruan/ShiYi/actions/runs/41278695985) 的 build / device 均成功：45 项 JVM、20 项设备测试、Lint、Debug / Release 构建以及 18 页截图。新增设置损坏用例也已在本地独立、全套及 CI 验证通过。详细范围见 [TESTING.md](TESTING.md)。

合成旧 schema 2 的回忆、原图、标签、档案、心愿、纪念日和合集关系完成真实加密迁移；哈希一致、重复打开成功、提交前后中断可重试，验证成功后才清理旧明文。schema 1→3 的历史字段迁移另有 JVM fixture 验证。真实用户照片和手机未被读取、测试或迁移。

旧格式备份在隔离上下文的新随机本地密钥下恢复成功，当前设备安全设置保持不变；错误密码、篡改、截断与无效关系不会覆盖现有记录。跨密钥模拟不冒称两台实体设备测试。

[截图与视觉检查](screenshots/1.1.0/README.md)。[源码提交记录](https://github.com/J-Ruan/ShiYi/commits/main/)：e401e55 为升级主体，2d90c9d 修复迁移与恢复，d5e7904 完成截图反馈，549f153 加固正式签名门禁，ef9b21e 补充损坏设置恢复覆盖。

## 实际构建产物

这些是本地工程产物，不是正式发行附件。CI Debug 证书可能不同，不承诺与其二进制哈希相同。

| 产物 | 字节 | 大小 | 签名结果 |
|---|---:|---:|---|
| app-debug.apk | 40,775,574 | 38.89 MiB | 默认 Debug 签名有效，仅测试 |
| app-release-unsigned.apk | 33,523,126 | 31.97 MiB | 未签名，不可正式发行 |

Debug SHA-256：`0193d3bc695ac3f731f6da28e6d48e44f90d9dafcf6b60c6b0432281e8d8e310`

未签名 Release SHA-256：`966b5ebb240991d2a1353b905fb432a951cd762a713a01db8156cded58fc7421`

二者均确认版本 / 包名正确，无联网或广泛存储权限，并包含第三方许可。真实 Debug 及未签名产物均被正式发布门禁拒绝。正式签名 APK 大小、正式 APK SHA-256、安装验证、Release 和直接下载链接当前均未生成，不能伪造。

## 发布阻塞与下一步

[实际发布尝试](https://github.com/J-Ruan/ShiYi/actions/runs/41277945397)：发布前 verify / test 成功，签名阶段明确报告 `Missing required signing configuration: KEYSTORE_BASE64`。这个变量取自 `ANDROID_KEYSTORE_BASE64` Secret。仓库 Secrets 列表读取接口也返回 403，因此不声称已经知道其他 Secret 的配置状态。

仓库所有者在 [Actions Secrets](https://github.com/J-Ruan/ShiYi/settings/secrets/actions) 配置长期保存的正式 keystore 及四个 Secret；在 [Variables](https://github.com/J-Ruan/ShiYi/settings/variables/actions) 配置 `ANDROID_CERT_SHA256`。完整生成、备份、配置步骤见 [RELEASE.md](RELEASE.md)。不要每次创建新密钥。完成后在 [Signed release](https://github.com/J-Ruan/ShiYi/actions/workflows/release.yml) 运行版本 `1.1.0`，通过全部门禁后自动创建 v1.1.0、上传正式 APK 和 SHA256SUMS.txt。

从旧 Debug 转正式版须先导出并验证 `.shiyi`，可靠另存，再卸载旧 Debug、安装正式版并恢复。签名不同不能覆盖安装；不得在未验证备份前卸载。

## 尚未执行 / 其他限制

荣耀实机、生物识别硬件、Android 8 / Android 13+ 授权流程、超大媒体和低空间压力尚未人工验证。本轮没有 AAB。软件模拟器曾遇到系统服务未就绪，最后成功安装及完成真实迁移测试；完整套件以成功 CI 为准。

仓库 Topics 的写入被 Metadata API 以 403 拒绝，未擅自变更权限。所有者可在仓库 About 齿轮补充 android、kotlin、jetpack-compose、offline、privacy、memory、journal；它不影响应用构建或正式签名。


## 后续测试 APK 下载交付

按用户后续下载请求，已将 `ShiYi-1.1.0-debug.apk` 放入仓库 downloads 目录，并更新校验文件及 README。Release 附件上传返回 HTTP 401，故采用 Git 文件分发。公开下载的是 Debug 测试包，正式发行状态仍受长期签名配置阻塞；未把 Debug 标记为正式版。新测试包与旧 1.0.0 证书不同，须先备份再处理安装冲突。
