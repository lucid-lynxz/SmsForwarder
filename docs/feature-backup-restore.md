# 备份与恢复

侧边栏新增的一级入口，直接打开「一键换新机」的**离线模式**（只保留导出/导入，隐藏网络推送/拉取），用来在新旧手机之间迁移配置。

## 入口

侧边栏最底部 → **备份与恢复**。同一界面在别处仍有入口：通用设置标题栏的恢复图标（同样进离线模式）、以及侧边栏 → 客户端 → 一键换新机（网络模式 + 离线模式全都有）。

## 备份内容

导出为单个 JSON 文件：`内部存储/Download/SmsForwarder.json`，包含

- 全部通用设置（SharedPreferences 全量）
- 发送通道（含各通道 `feature` 配置）
- 转发规则
- Frpc 内网穿透配置
- 自动任务
- 允许转发时间窗口
- 内容打码规则

**不包含**短信/通话记录与转发日志。

## 恢复行为（重要）

- 导入方与导出方的 **App 版本必须一致**，否则报「客户端与服务端的App版本不一致」并中止。
- 导入是**覆盖式**的：先清空现有通用设置、发送通道、转发规则、Frpc、自动任务，再按备份重建；同时**删除全部消息记录与转发日志**（即使备份里并不包含它们）。
- 导入前**没有二次确认弹窗**，也没有合并选项——点下去就直接覆盖。只有成功后会提示「导入配置成功！需要重启APP以便生效！重启后请再次核对所有配置项！」。
- 旧版本备份里没有「时间窗口 / 打码规则」段落时，导入会**保留当前配置**而不是清空这两项。
- 导入不会自动申请运行时权限，按 APP 内提示需再进一次通用设置把需要的功能开关切换一遍，或到系统设置手动授权。

## 安全提醒

1. **备份文件是明文**，其中包含各发送通道的密码/token/API key、内置服务端的 RSA 私钥、SM4 密钥、签名密钥以及短信指令安全手机号。它写在公共下载目录，**不受本次备份策略排除的保护**。迁移完成后请立即删除或移出 `Download/SmsForwarder.json`，不要长期留存，也不要随手发到聊天群里。
2. 该功能需要 `所有文件访问` 授权（MANAGE_EXTERNAL_STORAGE）。被拒绝时界面显示「未授权储存权限，该功能无法使用！」，导出/导入都会失败。
3. 界面自带声明：仅限个人新旧手机切换使用；主动请求、保活措施、个性设置按文案说明不在克隆范围（实际实现会随 SharedPreferences 全量导出，以导入后的核对结果为准）。

## 为什么现在必须用它

本仓库已把 `android:allowBackup` 设为 `false`（见 [安全加固](security-hardening.md)），**云端备份与 `adb backup` 不再产出任何配置**。以前靠 Google/厂商云备份或 `adb backup -f smsf.ab cn.ppps.forwarder` 迁移的用户，换机前必须先在这里导出一次 JSON，否则恢复出厂或换新机将丢失全部配置、凭据与历史记录。

## 实现位置

- `app/src/main/kotlin/cn/ppps/forwarder/activity/MainActivity.kt`（侧边栏 `POS_BACKUP` 入口）
- `.../fragment/client/CloneFragment.kt`（离线模式判定、导出/导入、防连点倒计时）
- `.../utils/HttpServerUtils.kt`（`exportSettings()` / `restoreSettings()` / 版本校验）
- `.../entity/CloneInfo.kt`（备份文件结构）
- `app/src/main/res/values/arrays.xml`（`menu_titles` / `menu_icons`）、`res/drawable/ic_menu_backup.xml`
