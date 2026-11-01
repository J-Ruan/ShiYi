# 数据存储与迁移

Room schema 3，applicationId `com.shiyi.memory` 不变。1→2 为 Wish 添加 nullable completedAt；2→3 为 Memory 添加 nullable deletedAt 与索引。无 destructive migration。软删除保留全部媒体、标签、合集、档案及心愿关联；永久删除才触发 CASCADE / SET NULL。回收站包含在备份快照中，正常浏览 / 搜索 / 统计 / PDF 排除软删除记录。

数据库 `memory-secure.db` 使用 SQLCipher 4.10.0（支持 API 26 和 16 KB 页设备）；每个安装的随机数据库口令与图片密钥由 Android Keystore AES-GCM 包装保存在 noBackup 目录。PIN 不参与密钥生成。后台提醒只显示通用内容。密钥缺失时保留数据并报错，不静默生成新密钥替代旧数据。

历史 `memory.db` 存在时：检查原图清单 → 复制并加密全部原图到 secure-media → 验证原图 SHA-256 → SQLCipher export 到 .next 暂存数据库 → integrity_check、foreign_key_check、所有表数量校验 → 重命名提交 → Room 显式迁移与快照验证 → 最后清理旧数据库 / 明文图片。失败或进程中断时旧文件保持可恢复；提交前的部分副本可重建，提交后的副本在下次启动重新核验。不得在失败分支调用清理。

ImageStorage 统一读写原始编码字节，外部临时 URI 不作为永久引用。加密文件带 SHYIMG01 版本头、随机 96-bit 基础 nonce、1 MiB 认证帧与认证结束帧；AAD 绑定头、顺序和长度，拒绝篡改、重排、截断或追加。图片展示按需完整校验后解码，Coil 不使用磁盘或内存图片缓存，关闭解码源时删除明文临时文件。PDF 保持原比例、采样 / 分区读取长图；原始文件不会重编码。

Repository 对修改、备份与清理串行化；事务快照经 conflate / distinctUntilChanged 合并通知，标签关系按快照建立映射，提供基于索引的日期窗口分页接口。图片清理预览包含大小 / 修改时间；确认时重新读取全部引用（含回收站与分享草稿）再删除，保护最近 24 小时导入的文件。

恢复先验证密码、清单版本、路径、外键和所有图片哈希。新随机文件名加密写入完毕后，以 Room 单事务替换数据。失败不删除旧图片；进程中断最多留下无引用密文，可预览清理。删除全部清空表、媒体、分享草稿、设置与缓存，并 checkpoint / VACUUM / 截断 WAL。不能保证闪存物理取证级擦除。
