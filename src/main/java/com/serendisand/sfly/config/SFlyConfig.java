package com.serendisand.sfly.config;

import com.serendisand.sfly.text.DefaultMessages;
import com.serendisand.sfly.text.MessageSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** config.json 的结构。改完文件用 /sf reload 热重载。 */
public class SFlyConfig {
    /** 消息前缀。对 CHAT / BROADCAST 生效，actionbar、标题、BossBar 不加 */
    public String prefix = "&b[&fSFly&b] &r";

    /**
     * 活动排期用的时区。留空 = 跟随 JVM 默认。
     *
     * <p><b>很多服务器面板把 JVM 跑在 UTC</b>，那样「每天 20:00」实际是北京时间凌晨 4 点。
     * 国内服务器建议显式写 {@code Asia/Shanghai}。
     */
    public String timezone = "";

    public Quota quota = new Quota();
    public Flight flight = new Flight();
    public Display display = new Display();
    public Storage storage = new Storage();
    public Events events = new Events();

    /** 所有文案，键见 Msg 常量 */
    public Map<String, MessageSpec> messages = new LinkedHashMap<>();

    public List<String> helpPlayer = new ArrayList<>(List.of(
            "&b&m──────────&r &f✈ &bSFly 帮助 &b&m──────────",
            "&f/sf &7- 起飞 / 关飞（智能开关）",
            "&f/sf on &7- 只起飞",
            "&f/sf off &7- 只关飞（连飞行权限一起收回）",
            "&f/sf status &7- 查看自己的额度",
            "&f/sf bossbar &7- 开关额度 BossBar（on/off，不写=翻转）",
            "&f/sf pause &7- 暂停计时（会同时禁止飞行）",
            "&f/sf resume &7- 恢复计时",
            "&f/sf help &7- 显示这份帮助",
            "&8起飞条件：活动进行中 且 有正在计时的额度",
            "&8关闭飞行后从高处落下不会受伤，放心跳～"));

    public List<String> helpAdmin = new ArrayList<>(List.of(
            "&b&m────────&r &f✈ &bSFly 管理帮助 &b&m────────",
            "&6【活动】",
            "&f/sf event create <名称> <时长> [after <延迟>] [free] &7- 现在/延迟开始",
            "&f/sf event create <名称> <时长> at <\"开始\"> [until <\"结束\">] [free] &7- 指定开始",
            "&f/sf event create <名称> at <\"开始\"> until <\"结束\"> [free] &7- 起止都指定",
            "&8时间写法（24 小时制）：&f\"2026-09-25T00:00\"&7 / &f\"09-25T00:00\"&7 / &f2026-09-25&7 / &f\"20:00\"",
            "&8带 &f: @ 中文空格 &8的内容要加引号 —— 按 Tab 补全会自动补上",
            "&f/sf event extend <名称> <时长> &7- 延长活动",
            "&f/sf event stop <名称|all> &7- 提前结束活动",
            "&f/sf event list &7- 列出全部活动",
            "&f/sf event info &7- 查看进行中的活动",
            "&f/sf event purge &7- 立刻清掉所有已结束的活动",
            "&8已结束的活动默认保留 &fevents.keepFinishedFor&8（默认 30d）再自动清理",
            "&d末尾加 &ffree &d= 限免：期间不消耗额度，没额度也能飞",
            "&6【循环活动】",
            "&f/sf repeat add <名称> daily <\"HH:mm\"> <时长> [free] &7- 每天定时开放",
            "&f/sf repeat add <名称> weekly <星期> <\"HH:mm\"> <时长> [free] &7- 每周定时开放",
            "&f/sf repeat remove <名称|all> &7- 删除循环活动",
            "&f/sf repeat list &7- 列出循环活动",
            "&6【额度】",
            "&f/sf quota give <玩家|\"@a\"|UUID> <pausable|fixed|all> <时长> &7- 增加额度",
            "&f/sf quota take <玩家|\"@a\"|UUID> <pausable|fixed|all> <时长> &7- 扣除额度",
            "&f/sf quota set <玩家|\"@a\"|UUID> <pausable|fixed|all> <时长> &7- 设置额度",
            "&f/sf quota check [玩家|\"@a\"] &7- 查询额度",
            "&f/sf quota reset <玩家|\"@a\"|UUID> &7- 清空额度",
            "&f/sf quota resetall &7- 清空所有人的额度",
            "&f/sf quota top [数量] &7- 额度排行",
            "&6【强制】",
            "&f/sf forcefly <玩家> &7- 强制开启飞行",
            "&f/sf forceunfly <玩家|\"@a\"> &7- 强制关闭飞行",
            "&6【其他】",
            "&f/sf reload &7- 重载配置",
            "&f/sf info &7- 查看运行状态",
            "&8时长写法：&f90&8=90秒 / &f10m&8 / &f2h&8 / &f1d&8 / &f1d2h30m"));

    public SFlyConfig() {
        messages.putAll(DefaultMessages.build());
    }

    /** 补齐被手动删掉的键，避免改配置改出 NPE。 */
    public void applyDefaults() {
        if (prefix == null) {
            prefix = "&b[&fSFly&b] &r";
        }
        if (quota == null) {
            quota = new Quota();
        }
        if (flight == null) {
            flight = new Flight();
        }
        if (display == null) {
            display = new Display();
        }
        if (storage == null) {
            storage = new Storage();
        }
        if (events == null) {
            events = new Events();
        }
        Map<String, MessageSpec> defaults = DefaultMessages.build();
        if (messages == null) {
            messages = new LinkedHashMap<>(defaults);
        } else {
            defaults.forEach(messages::putIfAbsent);
        }
        if (helpPlayer == null || helpPlayer.isEmpty()) {
            helpPlayer = new SFlyConfig().helpPlayer;
        }
        if (helpAdmin == null || helpAdmin.isEmpty()) {
            helpAdmin = new SFlyConfig().helpAdmin;
        }

        quota.clamp();
        flight.clamp();
        display.clamp();
        storage.clamp();
        events.clamp();
    }

    public static class Quota {
        /** 结算间隔（tick）。20 tick = 1 秒，调大省性能但精度变粗 */
        public int settleIntervalTicks = 20;
        /** 不可暂停额度是否在离线时继续流逝（7×24 一直烧） */
        public boolean fixedDrainsOffline = true;
        /**
         * 离线补算上限，比如 "2h"。超过这个时长的离线时间不再扣额度。
         * "-1" 表示不限制 —— 服务器停机的时间也照扣。
         */
        public String offlineDrainLimit = "-1";
        /** 剩余额度低于该值时提醒玩家 */
        public String warnThreshold = "1m";
        /** 同一条警告的最小间隔秒数，防刷屏 */
        public int warnCooldownSeconds = 120;
        /**
         * 两种额度同时还有剩时先烧哪一种。
         * FIXED_FIRST = 先烧不可暂停的（离线也在掉，等于「不用就作废」，优先消耗更划算）
         * PAUSABLE_FIRST = 先烧可暂停的
         */
        public String consumeOrder = "FIXED_FIRST";

        void clamp() {
            settleIntervalTicks = Math.max(1, Math.min(200, settleIntervalTicks));
            warnCooldownSeconds = Math.max(0, warnCooldownSeconds);
        }
    }

    public static class Flight {
        /** 必须有进行中的活动才能飞 */
        public boolean requireActiveEvent = true;
        /**
         * 落地时是否立刻收回飞行权限。
         *
         * <p>false（默认）= 原版创造手感：一次 /sf 之后会话一直保留，飞到落地、再起飞都不用重打命令，
         * 直到额度耗尽 / 活动结束 / 离线才收回。<b>此时「有额度的人」在会话期间免疫摔落伤害</b>，
         * 但额度是按真实时间一直在扣的，等于一直在付费。
         *
         * <p>true = 更严格：脚一沾地就立刻收回，连无伤状态一起还回去。
         * 代价是每次起飞都要重新打 /sf。
         *
         * <p>无论哪种，<b>从没用过 /sf 的玩家永远拿不到 mayfly</b>，摔落伤害一切照旧。
         */
        public boolean revokeOnLanding = false;
        /**
         * 玩家主动关掉飞行（双击空格）后，无伤状态最多保留多久。
         * 这个值只兜住「悬空但一直不落地」的情况（泡水、蛛网、坐船）。
         */
        public int airSessionGraceSeconds = 30;
        /** 强制收回飞行时给缓降保护，免得摔死 */
        public boolean safeLanding = true;
        public int safeLandingSeconds = 5;
        public boolean safeLandingParticles = false;

        /** 禁飞世界（维度 ID），例如 ["minecraft:the_nether", "minecraft:the_end"] */
        public List<String> blockedWorlds = new ArrayList<>();
        /**
         * 受伤是否自动断飞。
         *
         * <p>默认 <b>false</b>：挨一下打就断飞太容易误伤（摔落、火焰、仙人掌、友军误伤全算），
         * 打架时被蹭一下就掉下来也很扫兴。想拿它防止打架途中逃跑的话再打开。
         */
        public boolean disableOnDamage = false;
        /** 飞行速度倍数，1.0 是原版创造飞行速度 */
        public double flySpeed = 1.0;
        /** 允许飞行的最低 Y，-1 关闭 */
        public int minY = -1;
        /** 允许飞行的最高 Y，-1 关闭 */
        public int maxY = -1;

        public boolean isWorldBlocked(String dimensionId) {
            if (blockedWorlds == null || dimensionId == null) {
                return false;
            }
            for (String world : blockedWorlds) {
                if (world != null && world.trim().equalsIgnoreCase(dimensionId)) {
                    return true;
                }
            }
            return false;
        }

        void clamp() {
            airSessionGraceSeconds = Math.max(0, airSessionGraceSeconds);
            safeLandingSeconds = Math.max(1, safeLandingSeconds);
            flySpeed = Math.max(0.05, Math.min(10.0, flySpeed));
            if (blockedWorlds == null) {
                blockedWorlds = new ArrayList<>();
            }
        }
    }

    public static class Display {
        /** actionbar 刷新间隔（tick），<= 0 关闭 */
        public int actionbarIntervalTicks = 10;
        /** 玩家登录时提示进行中的活动 */
        public boolean announceEventOnJoin = true;
        /** 活动开始时全服广播 */
        public boolean announceEventStart = true;
        /** 活动结束时全服广播 */
        public boolean announceEventEnd = true;
        /** 活动结束后强制收回飞行 */
        public boolean revokeFlightOnEventEnd = true;

        /** 用 BossBar 显示剩余额度 */
        public boolean bossBarEnabled = true;
        /** PINK / BLUE / RED / GREEN / YELLOW / PURPLE / WHITE */
        public String bossBarColor = "PINK";
        /** PROGRESS / NOTCHED_6 / NOTCHED_10 / NOTCHED_12 / NOTCHED_20 */
        public String bossBarOverlay = "PROGRESS";
        /** 进度条满格对应的额度，用来算百分比 */
        public String bossBarFullQuota = "1h";

        void clamp() {
            actionbarIntervalTicks = Math.max(0, Math.min(200, actionbarIntervalTicks));
        }
    }

    public static class Storage {
        /** 自动存盘间隔（分钟），<= 0 关闭（只在关服和关键操作时写） */
        public int autoSaveMinutes = 5;

        void clamp() {
            autoSaveMinutes = Math.max(0, autoSaveMinutes);
        }
    }

    public static class Events {
        /**
         * 已结束的一次性活动保留多久再自动清理，比如 "30d"、"7d"、"1d"。
         *
         * <p>保留期内的活动会一直留在 data.json 里，重启不丢，`/sf event list` 也还能翻到。
         * 写 "-1" / "never" / "off" 表示永不清理（历史会一直堆着）。
         */
        public String keepFinishedFor = "30d";

        void clamp() {
            if (keepFinishedFor == null || keepFinishedFor.isBlank()) {
                keepFinishedFor = "30d";
            }
        }
    }
}
