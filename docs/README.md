# 本地增强功能文档

本目录记录本仓库（fork）在上游 `pppscn/SmsForwarder` 之上新增与调整的功能，供升级和排障时查阅。

- 适用版本：`3.5.0`（数据库版本 `23`）
- 上游完整使用说明见 GitHub Wiki：https://github.com/pppscn/SmsForwarder/wiki ，本目录只写本地差异部分

## 目录

| 文档 | 内容 | 类型 |
| --- | --- | --- |
| [允许转发时间窗口](feature-time-window.md) | 给转发规则限定「只在某些时段转发」 | 新增功能 |
| [内容打码规则](feature-mask-rule.md) | 转发前按正则替换/删除敏感内容 | 新增功能 |
| [通知监听去重](feature-notification-dedup.md) | 同一条通知被重复上报时只转发一次 | 新增功能 |
| [备份与恢复](feature-backup-restore.md) | 侧边栏直达配置导出/导入（离线模式） | 新增功能 |
| [安全加固](security-hardening.md) | 下载校验、服务端鉴权、短信指令、备份排除 | 行为变更（含破坏性变更） |

## 升级须知（摘要）

1. **备份方式变了**：`allowBackup=false` 后云端备份与 `adb backup` 不再产出任何配置，换机必须改用侧边栏「备份与恢复」。
2. **内置 HTTP 服务可能起不来**：未配置安全措施（签名/RSA/SM4）并填写密钥时，服务会拒绝启动，开机自启也无效。
3. **短信指令默认失效**：未填写「安全手机号」时，所有 `smsf#` 指令一律不执行。
4. **在线更新/frpc 动态库下载走证书校验**：使用抓包代理、企业 TLS 中间人或自签站点的人会看到 SSL 错误；下载的 `libgojni.so` 还要通过 sha256 校验。
5. 数据库结构自动迁移（v21 → v23），无需手动处理；旧版 Sender 上的 `time_window_id` 列保留但不再使用。
