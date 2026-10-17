# ✦ SFly — 活动限时飞行额度系统 ✦

**Fabric 26.2** 服务端 Mod。用「额度」管理飞行：活动期间开放，飞行时长按真实时间流逝，
可暂停 / 不可暂停两种额度分开计，全部文案与行为都能在配置里改。

> by **Serendisand**

---

## 🖱️ 不想记命令？用配置台

双击 **`web/sf-panel.html`** —— 单文件网页，不联网、不连服务器、不开端口。
填表就能拼出命令，带一键复制，还附完整指令速查表和时长换算器。

```
活动名称  [国庆限免飞行      ]
开始时间  [2026-10-01] [00:00]
结束时间  [2026-10-08] [00:00]
限免      [x]

生成的命令：
/sf event create 国庆限免飞行 at 2026-10-01T00:00 until 2026-10-08T00:00 free
                                                          [复制命令]
```

---

## 🎉 节假日限免怎么配

```
# 国庆限免：10月1日 0点 → 10月8日 0点（覆盖整个 10/1–10/7）
/sf event create 国庆限免飞行 at 2026-10-01T00:00 until 2026-10-08T00:00 free

# 中秋限免：2026 年中秋是 9月25日
/sf event create 中秋限免飞行 at 2026-09-25T00:00 until 2026-09-27T23:59 free
```

`free` = **限免**：活动期间**不消耗任何额度，没额度的玩家也能飞**，离线补算也一并免掉。
活动一结束飞行权限立刻收回（空中给缓降保护）。

> 中秋是农历、每年日期都变，所以用一次性活动逐年重建。国庆固定 10/01，也可以每年重建一次。
> 想要「每年自动」的话需要给循环活动加 YEARLY 模式，说一声就行。

---

## ✨ 一句话说明玩法

管理员开一场活动 → 给玩家发额度 → 玩家登录看到活动提示 → `/sf` 起飞 →
额度按真实时间扣，扣完 / 活动结束自动落地（带缓降保护，摔不死）。

---

## 💾 数据与持久化

所有活动和玩家额度都存在 **`config/sf/data.json`**，创建/修改时立刻落盘，关服再存一次。
**服务器重启活动不会丢** —— 包括起止时间、限免标记、循环活动的时刻表。

已结束的一次性活动**默认保留 30 天**再自动清理，保留期内 `/sf event list` 还能翻到历史。
改保留期：

```jsonc
"events": {
  "keepFinishedFor": "30d"   // 写 "7d" 缩短，"1d" 更短，"-1"/"never" 表示永久保留
}
```

想立刻清干净就用 `/sf event purge`。配置写错时会被当成默认 30 天，
**不会因为手滑把历史全删了**。

> 存盘走独立线程，不卡服务器 tick；写盘前先在主线程取快照，避免数据被 tick 改到一半。

---

## 🕹️ 玩家命令

| 命令 | 作用 |
| --- | --- |
| `/sf` | 起飞 / 降落（智能开关：飞着就降，没飞就起） |
| `/sf on` | **只起飞**，绝不把你按下来（适合绑快捷键） |
| `/sf off` | **只降落**，不会把你重新拉起来 |
| `/sf status` | 查看自己的额度与计时状态 |
| `/sf pause` | 暂停计时（**只有可暂停额度**能暂停，暂停期间不能飞行） |
| `/sf resume` | 恢复计时 |
| `/sf help` | 显示帮助 |

> 根命令只有 `/sf` 一个，**没有别名** —— 多一棵命令树就多一份维护面，而且参数类型一旦注册进
> `minecraft:command_argument_type`，没装本模组的 Fabric 客户端会在配置阶段被 registry-sync 断线
> （详见下方「为什么不用自定义参数类型」）。

`/sf` 是开关，`/sf on` / `/sf off` 是确定性的单向操作 —— 写宏或者绑键位的时候用后者。

`/sf` 在已经有 `flying` 状态时再按一次就是降落。降落时**保留 mayfly**，
所以从高处双击空格掉下来**不会受伤**——跟原版创造一模一样。

## 🛠️ 管理员命令

权限等级：**GAMEMASTERS（OP 2）**。想改成 OP 3 就改 `CommandSupport.ADMIN` 里的 `LEVEL_GAMEMASTERS`。

### ⚠️ 参数里的引号

命令参数一律用原版 `string()`，它**只认 `[0-9A-Za-z_.+-]`**。所以下面这些必须加引号：

| 想写 | 要写成 | 为什么 |
| --- | --- | --- |
| `@a` | `"@a"` | `@` 不在字符集里 |
| `20:00` | `"20:00"` | `:` 不在字符集里 |
| `2026-09-25T00:00` | `"2026-09-25T00:00"` | 同上，冒号 |
| `国庆限免` | `"国庆限免"` | 非 ASCII |
| `中秋 限免` | `"中秋 限免"` | 含空格 |
| `Steve`、`1d2h30m`、`550e8400-e29b-41d4-a716-446655440000` | 照原样写 | 全在字符集里 |

**按 Tab 不要求你记规则** —— 补全项自带引号，敲一半 Tab 一下就是合法输入。

代价是换来的：不注册任何自定义参数类型 ⇒ 任何客户端（原版 / Fabric 没装本模组）都能进服。

### 活动

```
/sf event create <名称> <时长>                              现在开始，持续 <时长>
/sf event create <名称> <时长> after <延迟>                  <延迟>后开始
/sf event create <名称> <时长> at <开始>                     <开始>时刻启动
/sf event create <名称> <时长> at <开始> until <结束>        起止都指定（此时 <时长> 被忽略）
/sf event create <名称> at <开始> until <结束>               省掉时长的绝对起止
/sf event extend <名称> <时长>                              延长
/sf event stop <名称|all>                                   提前结束
/sf event list                                              列出全部活动
/sf event info                                              查看正在进行的活动
/sf event purge                                             立刻清掉所有已结束的活动
```

上面每条末尾都可以再加一个 **`free`** 变成限免活动。

**时间写法（全部 24 小时制）**：

| 写法 | 含义 |
| --- | --- |
| `2026-09-25T00:00` | 2026年9月25日 0时0分 |
| `09-25T20:30` | 省略年份按当前年；**如果这天已经过了就顺延到明年** |
| `2026-09-25` | 只写日期，按当天 00:00 |
| `20:00` | 只写时间，按今天；已过则顺延到明天 |
| `8:00` | 没补零也认 |

顺延规则让跨年排期能一次写对：`at 12-30T00:00 until 01-02T00:00` 会自动落到次年 1 月 2 日。
想明确指定过往日期就写全 `2026-09-25T00:00`，带年份时原样采用、不顺延。

> ⚠️ **时区**：所有时间都按 `config.json` 里的 `timezone` 解析。
> **很多服务器面板把 JVM 跑在 UTC**，那样「每天 20:00」实际是北京时间凌晨 4 点。
> 国内服务器请显式写 `"timezone": "Asia/Shanghai"`。留空则跟随 JVM。

**时长写法**：`90`(=90秒) / `30s` / `10m` / `2h` / `1d` / `1w` / `1d2h30m` / `1天2小时30分`，可以混写。

### 限免活动（free）

末尾加 `free` 就是限免：**活动期间不消耗任何额度，没有额度的玩家也能飞**。
适合节假日全服放开，比如：

```
/sf event create 国庆限免飞行 at 2026-10-01T00:00 until 2026-10-08T00:00 free
/sf event create 中秋限免飞行 at 2026-09-25T00:00 until 2026-09-27T23:59 free
```

限免期间玩家看到的是「✦ 限免飞行中｜不限时长」，离线补算也会一并免掉。
活动一结束，飞行权限立刻收回（空中给缓降保护）。

**注意**：循环活动只支持「每天 / 每周」，节假日的固定日期请用上面的一次性活动逐年重建。
（国庆这种固定 10-01 的，如果想做成每年自动，可以让我加一个 YEARLY 模式。）

### 循环活动（每天 / 每周定时开放）

```
/sf repeat add <名称> daily  <HH:mm> <时长> [free]
/sf repeat add <名称> weekly <1-7> <HH:mm> <时长> [free]
/sf repeat remove <名称|all>
/sf repeat list
```

例如每天 20:00 开放 2 小时：`/sf repeat add 晚间飞行 daily 20:00 2h`
每周六 14:00 开放 3 小时：`/sf repeat add 周末飞行 weekly 6 14:00 3h`

### 额度

```
/sf quota give <目标> <pausable|fixed|all> <时长>    增加
/sf quota take <目标> <pausable|fixed|all> <时长>    扣除
/sf quota set  <目标> <pausable|fixed|all> <时长>    直接设为
/sf quota check [目标]                               查询（不写就是查自己）
/sf quota reset <目标>                               清空
/sf quota resetall                                   清空所有人
/sf quota top [数量]                                 额度排行
```

**`<目标>` 支持四种写法**：

- `@a` / `@p` / `@s` / `@r` —— 原版选择器，`@a` 就是全体在线
- 在线玩家名 —— `Serendisand`
- **离线玩家名** —— 管理员可以直接给还没上线的人预发额度
- **UUID** —— `069a79f4-44e9-4726-a5be-fca90e38aaf5`，也吃没横杠的写法

### 强制与杂项

```
/sf forcefly <目标>      强制开启飞行（跳过额度/活动检查）
/sf forceunfly <目标>    强制关闭飞行
/sf reload               热重载 config.json
/sf info                 查看运行状态
```

**时长写法**：`90`(=90秒) / `30s` / `10m` / `2h` / `1d` / `1w` / `1d2h30m` / `1天2小时30分`，可以混着写。

---

## 🎫 两种额度

| | 可暂停额度（pausable） | 不可暂停额度（fixed） |
| --- | --- | --- |
| 玩家 `/sf pause` | ✅ 能停 | ❌ 停不了 |
| 玩家离线 | ✅ 自动暂停 | 看 `fixedDrainsOffline`，默认**照扣** |
| 计时方式 | 在线且未暂停时走表 | 按配置持续走表 |
| 冻结期间能飞吗 | ❌ **不能** | — |

**两者都是「实时流逝」**：不看你在不在飞，只看计时开关开没开。
也就是说额度是一张「会员时长卡」，不是「飞行里程表」。
飞与不飞都扣，这样「暂停」才有意义。

**暂停 = 冻结额度 = 也不能飞。** 不然暂停就成了免费无限飞行。
注意区分两种「暂停」：

- **手动暂停**（`/sf pause`）—— 冻结，且**禁止飞行**
- **离线暂停**（可暂停额度自动的）—— 只是不扣时间，玩家上线后照样能飞。这才是可暂停额度的价值

同时有两种额度时，暂停只会冻住可暂停那部分；不可暂停额度还在走表，
所以此时**仍然可以飞**（HUD 会显示「可暂停额度已冻结」）。

两种额度都有剩时，默认**先烧不可暂停的**（它在离线时也在掉，等于不用就作废）。
改成先烧可暂停的：`quota.consumeOrder` 设成 `PAUSABLE_FIRST`。

额度按时间戳结算，服务器停机期间不可暂停额度也照跑。
不想让停机把额度耗光就把 `quota.offlineDrainLimit` 设成 `2h` 之类的上限。

---

## 🪂 关于摔落伤害（重要）

原版判定就一行：`Player#causeFallDamage` 开头 `if (abilities.mayfly) return false;`。
**mayfly 一挂上，这个玩家就免疫摔落伤害。** 所以 SFly 的规矩是：

- ✅ 只有玩家**真的按了 `/sf` 起飞**那一刻才置 `mayfly`
- ✅ **从没用过 `/sf` 的玩家永远拿不到 `mayfly`**，摔落伤害一切照旧
- ✅ 创造 / 旁观玩家**一根手指都不碰**，原版飞行完全不受影响
- ✅ 额度耗尽 / 活动结束 / 离线 / 重置额度 —— 立刻收回 `mayfly`

飞起来之后双击空格关掉飞行往下掉，不掉血（这就是原版创造的手感）。
落地之后怎么办由 `flight.revokeOnLanding` 决定：

- `false`（**默认**）—— 原版创造手感。一次 `/sf` 之后会话一直保留，落地再起飞不用重打命令，
  直到额度耗尽 / 活动结束 / 离线才收回。
  代价是会话期间这个人免疫摔落伤害——但他**每一秒都在烧额度**，等于一直在付费。
- `true` —— 脚一沾地就立刻收回 `mayfly`，连无伤状态一起还回去。更严格，但每次起飞都要重新打 `/sf`。

另外 `flight.airSessionGraceSeconds`（默认 30s）兜住「关了飞行但一直不落地」的情况（泡水、蛛网、坐船）。

---

## ⚙️ 配置

`config/sf/config.json`（改完 `/sf reload`）｜`config/sf/data.json`（额度与活动，自动存盘）

全部文案都在 `messages` 里，每条支持三种写法：

```jsonc
"fly-on": "&a起飞！",                                     // 单行聊天栏
"fly-on": ["&a起飞！", "&7剩余 {total}"],                  // 多行聊天栏
"fly-on": { "channel": "ACTIONBAR", "lines": ["&a起飞！"] } // 完整写法
```

`channel` 可选：`CHAT` `ACTIONBAR` `TITLE` `SUBTITLE` `BROADCAST` `BOSSBAR` `NONE`
（`NONE` = 静音这条提示；`BROADCAST` = 全服广播并写进控制台日志）

颜色用 `&` 写，例如 `&a` `&l`。占位符：

| 占位符 | 含义 |
| --- | --- |
| `{player}` | 玩家名 |
| `{pausable}` `{fixed}` `{total}` | 可暂停 / 不可暂停 / 合计额度（中文格式，如 `1小时30分`） |
| `{pausable_short}` `{fixed_short}` `{total_short}` | 同上，紧凑格式 `1h30m`（给 actionbar 用） |
| `{timer}` | `计时中` 或 `已暂停` |
| `{event}` `{event_remain}` `{event_duration}` | 活动名 / 剩余 / 总时长 |
| `{target}` `{amount}` `{type}` `{count}` | 管理命令回显 |
| `{world}` `{min_y}` `{max_y}` | 所在世界 / 高度限制 |

### 主要开关

```jsonc
"prefix": "&b[&fSFly&b] &r",
"timezone": "Asia/Shanghai",       // 空 = 跟随 JVM。见上面「时区」警告

"quota": {
  "settleIntervalTicks": 20,      // 结算间隔，20 tick = 1 秒
  "fixedDrainsOffline": true,     // 不可暂停额度离线是否继续扣
  "offlineDrainLimit": "-1",      // 离线补算上限，-1 = 不限（含服务器停机）
  "warnThreshold": "1m",          // 剩余低于这个值就提醒
  "warnCooldownSeconds": 120,     // 提醒最小间隔，防刷屏
  "consumeOrder": "FIXED_FIRST"   // 或 PAUSABLE_FIRST
},
"flight": {
  "requireActiveEvent": true,     // 必须有活动才能飞；false = 有额度就能飞
  "revokeOnLanding": false,       // 见上面「摔落伤害」一节
  "airSessionGraceSeconds": 30,
  "safeLanding": true,            // 强制断飞时给缓降保护
  "safeLandingSeconds": 5,
  "blockedWorlds": [],            // 禁飞世界，如 ["minecraft:the_nether"]
  "disableOnDamage": true,        // 受伤自动断飞（防打架逃跑）
  "flySpeed": 1.0,                // 飞行速度倍数，1.0 = 原版创造速度
  "minY": -1, "maxY": -1          // 限高，-1 = 不限
},
"display": {
  "actionbarIntervalTicks": 10,   // <= 0 关闭 actionbar
  "bossBarEnabled": true,
  "bossBarColor": "PINK",         // PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE
  "bossBarOverlay": "PROGRESS",
  "bossBarFullQuota": "1h",       // 进度条满格对应的额度
  "announceEventOnJoin": true,    // 登录提示进行中的活动
  "announceEventStart": true,     // 活动开始全服广播
  "announceEventEnd": true,
  "revokeFlightOnEventEnd": true
},
"storage": { "autoSaveMinutes": 5 },
"events": { "keepFinishedFor": "30d" }
```

---

## 🛠️ 构建

需要 **Java 25+** 和 Gradle：

```bash
gradle build
```

产物在 `build/libs/SFly-1.0.0.jar`。

## 📦 安装

1. 把 `.jar` 丢进服务端 `mods/`
2. 需要 **Fabric Loader 0.19.3+** 和 **Fabric API**
3. 启动后会生成 `config/sf/config.json`，按需改完 `/sf reload`

启动时会在控制台打印横幅和配置摘要，方便确认加载情况。

---

## 📁 代码结构

```
com.serendisand.sfly
├── SFlyMod                 装配 + 启动横幅
├── config/                 config.json 读写、消息三种写法的 Gson 适配器
├── data/                   PlayerQuota / FlyEvent / RecurringEvent、data.json 异步存盘
├── quota/                  额度结算（实时流逝 + 离线补算）
├── event/                  活动生命周期，活跃窗口带 1 秒缓存
├── flight/                 飞行授予/回收 —— 所有 mayfly 的生杀都在这里
├── text/                   文案渲染、占位符、时长解析、& → §
├── command/                命令树（根 + 四个模块）+ 目标解析（@a / 名字 / UUID / 离线）
└── task/                   每 tick 驱动：结算、HUD、活动起止广播
```

---

## ⚠️ 已知边界

- 和别的「允许飞行」类插件共存时，如果双方都改 `mayfly` 会互相覆盖。
  SFly 只在玩家用过 `/sf` 之后才去收 `mayfly`，不会主动碰别人的值守。
- `/sf event info` 只显示正在进行的活动；`list` 能看到全部（含等待中和刚结束的）。

## 🧨 为什么不用自定义参数类型

这一条踩过大坑，写下来免得以后有人又试一遍。

早期版本为了让 `@a`、中文活动名、`20:00` 不加引号就能用，自己实现了一个
`FreeStringArgumentType` 并注册进 `minecraft:command_argument_type`。后果是
**任何没装 SFly 的 Fabric 客户端都进不来**，卡在配置阶段被断线：

```
Registry entry (sfly:free_string) is missing from local registry (minecraft:command_argument_type)
```

反编译 `fabric-registry-sync-v0` 后的结论（`ClientRegistrySyncHandler.checkRemoteRemap`
→ `missingEntriesError` → `throw RemapException`）：

| 以为 | 实际 |
| --- | --- |
| 改命名空间成 `minecraft:` 就行 | 客户端按**完整 id** `containsKey` 判断，跟命名空间无关，照样断 |
| 给注册表打 `OPTIONAL` 就放行 | `OPTIONAL` 只在**整个注册表**客户端不存在时才被读；`command_argument_type` 是原版注册表，客户端一定有，这个分支永远走不到 |
| 把 `SYNCED` 摘掉 | `RegistryAttributeHolder` 只有 `addAttribute`/`hasAttribute`，**没有 remove**；而且 `FabricRegistryInit` 里 fabric-api 自己就把它标了 `SYNCED` |
| 换成原版 `string()` 只在服务端生效 | 对，服务端照旧用原版参数类型，客户端不需要认识任何新东西 |

所以现在的写法是：**零自定义参数类型**。全树只用字面量 + 原版
`string()`/`integer()`，补全项自带引号来兜用户体验。

## 💖 Made with love by Serendisand
