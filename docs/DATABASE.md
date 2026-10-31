# 本地数据库与文件一致性

数据库：Room / SQLite，当前 schema version 2。发生日期使用 ISO `LocalDate` 文本，不转换 UTC；创建、更新与心愿实现时间用 epoch 毫秒，统计时转换设备本地时区。Room DAO 均为 suspend，Repository 与 ViewModel 在后台线程处理读写。UI 通过失效通知与 Flow 订阅事务快照。

| 实体 | 关系 / 删除规则 |
|---|---|
| PartnerProfile | 唯一 id=1，保存双方昵称、生日、相识日及头像相对路径 |
| Memory | 独立 UUID 主键；封面引用本卡片的一张图片 |
| MemoryMedia | memoryId 外键 CASCADE；(memoryId, position) 唯一，保留原图相对路径与大小 |
| ProfileEntry | 独立档案细节，分类字符串允许自定义 |
| Wish | memoryId 可空，回忆删除时 SET NULL；独立记录实现时间 |
| Anniversary | memoryId 可空，回忆删除时 SET NULL |
| MemoryCollection | 不复制回忆或图片 |
| CollectionMemoryCrossRef | 复合主键；任一端删除时 CASCADE，只删除关联 |
| Tag | name 唯一 |
| MemoryTagCrossRef | 复合主键；两端 CASCADE |
| ProfileMemoryCrossRef | 复合主键，双向查询；两端 CASCADE |
| MemoryLink | 回忆之间的双向展示关联；两端 CASCADE |
| Category | (scope, name) 唯一，自定义分类选项 |

所有外键索引均已声明。未使用 destructive migration。`MIGRATION_1_2` 为 Wish 增加 nullable completedAt；历史未记录实现时间的心愿不虚构实现时间。版本 1、2 的 Room schema 随代码保存。

图片原文件在 `files/media/UUID.img`，扩展名与文件内容解耦，不重编码。Coil 按文件内容解码。导入失败清除半写文件。删除与编辑保留其他记录引用的原图；未使用的导入文件可在设置中清理（保护近 24 小时导入文件）。删除全部数据也清除媒体与缓存，并执行 SQLite checkpoint/VACUUM/日志截断，成功后重新建立界面任务以清除草稿状态。闪存磨损均衡意味着软件层删除不构成物理介质取证级擦除。

备份先持有 Repository 写锁并获取一致快照，记录每个原始图片的 SHA-256。恢复先解密到私有临时目录，拒绝目录穿越、重复文件、未知格式、无效日期、孤儿关系、缺图或校验错误。验证后展示数量，必须由用户确认覆盖。新图片使用全新随机文件名，全部复制成功后才通过 Room 单个事务替换所有表；事务失败回滚记录并清除新图片。旧图片不会在提交前移除；中断最多留下可清理的无引用文件，不会让旧记录指向被覆盖的文件。设置导入保留本设备应用锁与通知授权；数据库提交失败时恢复原设置。

SQLite 本身未额外加密，应用锁不是数据库加密。备份文件始终加密。
