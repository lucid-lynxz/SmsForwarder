# 安全加固

一批针对「远程控制面」和「凭据外泄」的收紧改动。**其中多项对老用户是破坏性变更**（见下文标注），升级前请先看「升级检查清单」。

## 1. 下载通道恢复 TLS 证书校验

移除了四处 `ignoreHttpsCert()`，改回系统默认的证书链 + 域名校验：在线更新检查、预览版更新检查、APK 下载、以及提示文案（`tips.json`）拉取。

同时**有意保留**忽略证书的地方：用户自建端点的通道（Bark、Gotify、Webhook、钉钉内部机器人、飞书应用、企业微信应用）与邮件 `STARTTLS` 信任所有主机，另外 `usesCleartextTraffic` 仍为 `true`。也就是说，加固只针对**项目自身的下载链路**。

- 影响：使用抓包代理、企业 TLS 中间人、自签证书镜像站的用户，检查更新/提示拉取/frpc 动态库下载会得到 SSL 错误。这是预期取舍，不是 bug。
- 自建通道的收发不受影响。

## 2. frpc 动态库 sha256 完整性校验

下载的 `libgojni.so` 在完成下载后、放入运行目录前，按当前 ABI 比对内置的期望 sha256（对应 `FRPC_LIB_VERSION`，见 `Constants.kt` 的 `FRPC_LIB_SHA256`）。

- 校验通过才移动到 `filesDir/libs/libgojni.so`；**不通过则删除下载文件并提示**「动态库校验失败，已丢弃下载内容。实际sha256：…（显示实际值）」，不会重试、不会弹窗重启，需要重新点内网穿透入口再下载。
- 边界（必须知道）：**只对「新下载」的库校验**。库里已存在的动态库只看「文件是否存在 + 版本字符串」，不校验 sha256。因此加固前就下载过被篡改库的设备不会被追溯发现。
- 维护约束：**重新编译/升级 `frpclib.aar` 时，必须同步更新 `FRPC_LIB_VERSION` 与四个 ABI 的 sha256**，否则所有下载都会被按设计拒绝。自行编译的 `libgojni.so` 无法通过下载方式加载。

## 3. 内置 HTTP 服务端：未配置安全措施则拒绝启动

`HttpServerUtils.securityReady()` 要求：安全措施选「校验签名 / RSA / SM4」之一（值 1/2/3）**且**对应密钥非空；未选（`0`）一律不就绪。提示文案：「请先选择安全措施（校验签名/RSA/SM4）并填写对应密钥，才能启动服务」。

该约束在四处强制，形成纵深：服务端页面的「启动/停止服务」按钮、安全措施选择器、服务自启开关（会被强制回弹），以及 `HttpServerService.onCreate` 本身——不满足就直接 `stopSelf()`，所以短信指令 `smsf#httpserver#start#`、自动任务、开机自启等所有启动路径都进不来。

- **破坏性变更**：升级后首次重启，原来无鉴权运行中的服务会停止，且在配置好安全措施 + 密钥之前无法再启动。依赖内置 HTTP API 的第三方对接、以及用「客户端 推送/拉取」连另一台无鉴权手机的用法都会失败。
- 注意：安全措施选择器允许把值存成 `0`，所以可能出现「配置界面看着正常、服务就是起不来」的状态，此时看启动按钮的报错提示即可定位。

## 4. 短信指令必须配置安全手机号

通用设置 → 短信指令，**未填写「安全手机号」时，所有 `smsf#` 指令一律不执行**（旧版空列表等于放行所有号码）。校验在 `SmsReceiver.doSmsCommand()` 入口处，位于任何命令分发之前，因此下列指令全部受影响，没有例外：

| 指令 | 作用 |
| --- | --- |
| `smsf#frpc#start/stop` | 启停内网穿透 |
| `smsf#httpserver#start/stop` | 启停内置服务端 |
| `smsf#system#reboot/shutdown` | 重启/关机（需 root） |
| `smsf#wifi#on/off` | 开关 WiFi |
| `smsf#sms#send#<JSON>` | 代为发送短信 |

- 号码匹配是**后缀匹配**（`from.endsWith(白名单项)`），分隔符 `;` `；` `，` 都会归一为逗号、各项自动去空格。因此白名单 `13800138000` 也能匹配 `+8613800138000`，但同样能匹配任何以这串数字结尾的号码（加长分机号/特服号形式）。**建议填入含国家码的完整号码**。
- 未填安全手机号时指令被丢弃，只有 logcat 一条 E 级日志，APP 内没有记录、也没有回复短信；正常的短信**转发**功能不受影响。
- 通用设置里「开关打开但手机号为空」「手机号被清空」都会被拦下并强制回弹开关；自动任务批量应用设置时同样拒绝在无手机号的情况下开启该功能。
- **破坏性变更**：以前开了短信指令却没填手机号的用户，升级后功能直接失效，而开关看起来仍是「开」的——直到你去碰一下开关或输入框才会被纠正。

## 5. 关闭系统备份并显式排除凭据与短信库

`android:allowBackup="false"`（因依赖库 `crondroid` 声明了 `true`，需要 `tools:replace`），并新增 `dataExtractionRules` 显式排除：

- 数据库 `sms_forwarder.db`（消息、日志、通道、规则、Frpc、任务、时间窗口、打码规则）
- 偏好文件 `cn.ppps.forwarder.xml`（服务端 RSA 私钥、SM4 密钥、签名密钥、安全手机号、各监听开关）

变更点：旧的 `backup_descriptor.xml` 只排除了数据库和**两个从未存在的文件名**，也就是说偏好文件（含全部密钥）此前是会进云备份的，现在这条泄露路径被关掉；`device-transfer`（换机迁移）同样排除。排除规则是针对「万一以后有人误开备份或被 OEM ROM 绕过」的兜底，实际效果是云备份与 `adb backup` 什么都不产出。

- **迁移动作**：改用侧边栏 [备份与恢复](feature-backup-restore.md)。
- **遗留风险**：升级前生成的 `.ab` 文件与历史云备份**仍然含有明文密钥和安全手机号**，建议删除这些旧备份，并重新生成服务端 RSA/SM4/签名密钥。
- 备份 JSON 自身仍是明文，见上文安全提醒。

## 6. 精简无用权限声明

移除：`READ_PRIVILEGED_PHONE_STATE`、`GET_ACCOUNTS`、`BATTERY_STATS`、`CANCEL_NOTIFICATIONS`（原声明带尾随空格、从未生效）、`READ_LOGS`、`REBOOT`；`INSTALL_PACKAGES` 改为 `tools:node="remove"`（依赖库带入的 signature 级权限，普通应用拿不到，卸载后仍靠 `REQUEST_INSTALL_PACKAGES` 完成安装）。

保留 `WRITE_SETTINGS` 并加了注释：声音警报与短信指令开关会向 XXPermissions 请求它，未声明会导致权限请求直接失败。

- 唯一需要在**真机上复核**的移除项：`READ_LOGS`。侧边栏「日志抓取」用 `logcat -d` 且只过滤本 APP 自己的 tag，现代 Android 允许应用读取自身日志，理论上不受影响，但本次未实测。

## 7. 远程查话簿：分页真正生效、单页封顶、不再逐条写日志

`PhoneUtils.getContactInfoList()` 原来只把 `limit`/`offset` 当作两个特判（`limit == 1` 提前返回、`offset` 越界提前返回），循环本身会把游标**从头到尾全部取完**。于是 `POST /contact/query` 传 `{"page_size": 100000, "phone_number": "", "name": ""}` 一次就能拉走整本通讯录；同时循环内每条联系人都会 `Log.d` 打一次姓名+号码，logcat 里等于再存一份通讯录。

现在：`moveToPosition(offset)` 定位起始行、取满 `limit` 条即停、单页最多 `MAX_CONTACT_QUERY_LIMIT = 200` 条（`page_size` 更大时按 200 截断，不报错），日志只打返回条数。

- 影响：`page_size > 200` 的分页客户端需要改小页长翻页取；短信转发里按号码反查联系人（内部 limit 99）行为不变。

## 8. CORS：默认拒绝任何跨域来源

删掉 11 个接口上的 `@CrossOrigin(methods = [RequestMethod.POST])`。AndServer 在没有该注解时不会下发任何 `Access-Control-Allow-*` 头，且预检 `OPTIONS` 直接返回 403「Invalid CORS request」，所以其它网页的 JS 既读不到响应、也发不出 JSON 请求。另外在 `LoggerInterceptor` 里加了一道来源校验：请求带 `Origin` 且不等于本次请求的 `Host`（即非同源）就 403 拒绝并记一条 E 级日志。

- 不受影响：APP 内的「主动控制·客户端」各页面、curl / 脚本 / 第三方 App 等原生 HTTP 客户端（它们不发 `Origin`）；由本机服务端自己托管的 web 客户端页面（同源）。
- **破坏性变更**：把 web 客户端页面放在**另一台机器或另一个端口**、再用浏览器 JS 跨域调用手机接口的用法会被拦掉。解决办法：把该页面放进服务端页面的「自定义 web 客户端目录」，由手机自己同源提供。

## 9. 服务端默认只监听 127.0.0.1

`HttpServerService` 建服务时显式绑定监听地址：新增开关 `enableServerLanAccess`（服务端页面「允许局域网访问」，**默认关闭**）。关闭时绑 `127.0.0.1`，局域网里其它设备/其它电脑上的浏览器再也连不上这个远程控制口；打开时绑 `0.0.0.0` 恢复旧行为。切换开关时若服务正在运行会自动重启使其生效，运行状态文字与「复制」按钮给出的地址也会跟随开关显示 `127.0.0.1` 或局域网 IP，避免复制到连不通的地址。

- 边界（必须知道）：**走 frpc 内网穿透时，公网侧的连接在手机上表现为本机 `127.0.0.1`**，所以回环绑定挡不住「通过 frpc 暴露到公网」这条路；那一路仍然只靠第 3 条的安全措施 + 密钥把关。frpc 用户不要因此以为关了局域网开关就等于公网不可达。
- 纯客户端模式、以及本机 APP 自己访问 `http://127.0.0.1:5000` 均不受影响。

## 本次未处理（已知遗留，勿当作已加固）

`MANAGE_EXTERNAL_STORAGE` 的文件访问方式、通道凭据在库中明文存储、配置导入时的 Java 原生反序列化路径（构造过的 `SmsForwarder.json` 仍是风险点）、以及全局允许明文 HTTP。

## 升级检查清单

1. 需要内置 HTTP API 的：先进服务端页面选定安全措施并填好密钥，再启动服务/开机自启。
2. 用短信指令的：先在通用设置填写含国家码的安全手机号。
3. 依赖 `adb backup`/云备份迁移的：立即用「备份与恢复」导出一份 JSON。
4. 用抓包代理或自建更新源的：预期会看到 SSL 报错。
5. 重新编译过 `frpclib.aar` 的：核对 `FRPC_LIB_VERSION` 与四个 sha256 是否同步更新。
6. 删除升级前的旧 `.ab` / 云备份，并轮换服务端密钥。
7. 从局域网其它设备/浏览器调用接口的：升级后去服务端页面勾选「允许局域网访问」（会自动重启服务）。
8. 用外部浏览器页面跨域调接口的：把页面改放到「自定义 web 客户端目录」，或改用非浏览器的 HTTP 客户端。
9. `page_size` 设得很大的远程查话簿脚本：单页最多 200 条，需要翻页取全量。

## 实现位置

- `app/src/main/kotlin/cn/ppps/forwarder/utils/update/XHttpUpdateHttpServiceImpl.kt`、`.../widget/GuideTipsDialog.kt`
- `.../activity/MainActivity.kt`（frpc 库下载与 `sha256OfFile()`）、`.../utils/Constants.kt`（`FRPC_LIB_SHA256` / `FRPC_LIB_VERSION`）
- `.../utils/HttpServerUtils.kt`（`securityReady()`、`enableServerLanAccess`）、`.../service/HttpServerService.kt`、`.../fragment/ServerFragment.kt`
- `.../receiver/SmsReceiver.kt`（安全手机号守卫）、`.../utils/SmsCommandUtils.kt`、`.../fragment/SettingsFragment.kt`、`.../workers/ActionWorker.kt`
- `app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/data_extraction_rules.xml`
- `.../utils/PhoneUtils.kt`（`getContactInfoList` 分页与上限）、`.../server/controller/*.kt`（移除 `@CrossOrigin`）、`.../server/component/LoggerInterceptor.kt`（同源校验）
