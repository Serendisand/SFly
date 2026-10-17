package com.serendisand.sfly.text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 首次运行写进 config.json 的默认文案。全部可以在文件里改。 */
public final class DefaultMessages {
    private DefaultMessages() {
    }

    public static Map<String, MessageSpec> build() {
        Map<String, MessageSpec> m = new LinkedHashMap<>();

        m.put(Msg.NO_PERMISSION, chat("&c你没有权限执行这个命令哦～ (｡•́︿•̀｡)"));
        m.put(Msg.PLAYER_ONLY, chat("&c这个命令只能由玩家执行啦"));
        m.put(Msg.UNKNOWN_COMMAND, chat("&c未知的子命令，输入 &f/sf help &c看看用法～"));

        m.put(Msg.FLY_ON, chat(
                "&a✦ 飞行已开启！",
                "&7剩余额度：&f{total} &8(&b可暂停 {pausable} &8| &c不可暂停 {fixed}&8)",
                "&7落下时双击空格即可关闭飞行，不会受伤～"));
        m.put(Msg.FLY_OFF, chat("&e✦ 飞行已关闭～想再飞请输入 &f/sf"));
        m.put(Msg.FLY_BLOCKED_NO_EVENT, chat("&c当前没有进行中的活动，暂时不能飞行 (｡•́︿•̀｡)"));
        m.put(Msg.FLY_BLOCKED_NO_QUOTA, chat("&c你的飞行额度已经用完啦，无法起飞…"));
        m.put(Msg.FLY_BLOCKED_GAMEMODE, chat("&7创造/旁观模式自带飞行，不需要 SFly 哦～"));
        m.put(Msg.FLY_BLOCKED_PAUSED, chat("&c计时处于暂停状态，暂停期间不能飞行～ 输入 &f/sf resume &c恢复计时"));
        m.put(Msg.FLY_BLOCKED_WORLD, chat("&c这个世界禁止飞行 (´･_･`)"));
        m.put(Msg.FLY_BLOCKED_HEIGHT, chat("&c当前高度不允许飞行！（允许范围 &f{min_y} &c~ &f{max_y} &c）"));
        m.put(Msg.FLY_DISABLED_BY_DAMAGE, chat("&c⚠ 你受到了伤害，飞行已自动关闭！"));

        m.put(Msg.QUOTA_EXHAUSTED, chat("&c⚠ 哎呀，飞行额度耗尽，已自动关闭飞行！"));
        m.put(Msg.QUOTA_EXHAUSTED_PROTECT, chat("&e✦ 缓降保护已生效，慢慢落地吧～"));
        m.put(Msg.QUOTA_WARNING, chat("&e⚠ 注意！飞行额度仅剩 &f{total}&e！"));
        m.put(Msg.EVENT_ENDED_KICK, chat("&c⚠ 活动已结束，已自动关闭飞行！"));

        m.put(Msg.PAUSE_ON, chat(
                "&e⏸ 已暂停计时，额度不会再减少啦～ 剩余：&f{total}",
                "&7暂停期间不能飞行，想继续飞请输入 &f/sf resume"));
        m.put(Msg.PAUSE_OFF, chat("&a▶ 已恢复计时！剩余：&f{total}"));
        m.put(Msg.PAUSE_UNAVAILABLE, chat("&c你只有不可暂停额度，暂停不了哦 (・_・;)"));
        m.put(Msg.PAUSE_ALREADY, chat("&7计时已经是暂停状态了～"));
        m.put(Msg.RESUME_ALREADY, chat("&7计时并没有处于暂停状态～"));

        m.put(Msg.STATUS, new MessageSpec(MessageChannel.CHAT, List.of(
                "&b&m──────────&r &f✈ &b飞行状态 &b&m──────────",
                "&7玩家：&f{player}",
                "&7可暂停额度：&b{pausable}",
                "&7不可暂停额度：&c{fixed}",
                "&7剩余合计：&f{total}",
                "&7计时状态：&f{timer}",
                "&7当前活动：&f{event}",
                "&7所在世界：&f{world}",
                "&b&m─────────────────────────")));
        m.put(Msg.STATUS_OTHER, new MessageSpec(MessageChannel.CHAT, List.of(
                "&b&m──────&r &f✈ &b{player} 的飞行状态 &b&m──────",
                "&7可暂停额度：&b{pausable}",
                "&7不可暂停额度：&c{fixed}",
                "&7剩余合计：&f{total}",
                "&7计时状态：&f{timer}")));
        m.put(Msg.STATUS_NONE, chat("&7该玩家没有任何飞行额度记录～"));

        m.put(Msg.EVENT_JOIN, new MessageSpec(MessageChannel.CHAT, List.of(
                "&d&m──────────&r &d✦ &f活动进行中 &d✦ &d&m──────────",
                "&d活动名称：&f{event}{free}",
                "&d剩余时间：&f{event_remain}",
                "&d你的额度：&f{total}{free_note}",
                "&7输入 &f/sf &7即可起飞！")));
        m.put(Msg.EVENT_START, new MessageSpec(MessageChannel.BROADCAST, List.of(
                "&d&m──────────&r &d✦ &f活动开始 &d✦ &d&m──────────",
                "&d活动名称：&f{event}{free}",
                "&d持续时间：&f{event_duration}",
                "&7活动期间使用 &f/sf &7即可飞行！{free_note}")));
        m.put(Msg.EVENT_END, new MessageSpec(MessageChannel.BROADCAST, List.of(
                "&8&m──────────&r &7✦ &f活动结束 &7✦ &8&m──────────",
                "&7活动名称：&f{event}",
                "&7感谢参与，下次再见～")));
        m.put(Msg.EVENT_ACTIVE_LINE, chat("&d✦ 活动 &f{event}{free} &d进行中，剩余 &f{event_remain}"));
        m.put(Msg.EVENT_NONE, chat("&7当前没有进行中的活动～"));

        m.put(Msg.ACTIONBAR_FLYING, new MessageSpec(MessageChannel.ACTIONBAR,
                List.of("&b✈ 飞行中 &8| &f{total_short} &8| &7可暂停 &b{pausable_short} &8/ &c{fixed_short}")));
        m.put(Msg.ACTIONBAR_PAUSED, new MessageSpec(MessageChannel.ACTIONBAR,
                List.of("&e⏸ 可暂停额度已冻结 &8| &f{total_short}")));
        m.put(Msg.ACTIONBAR_FREE, new MessageSpec(MessageChannel.ACTIONBAR,
                List.of("&d✦ 限免飞行中 &8| &f不限时长")));

        // 想关掉 BossBar 就把 display.bossBarEnabled 设为 false，或把这里改成 "NONE"
        m.put(Msg.BOSS_BAR_FLYING, new MessageSpec(MessageChannel.BOSSBAR,
                List.of("&d✈ 飞行中 &8| &f剩余 {total} &8| &b可暂停 {pausable} &8| &c不可暂停 {fixed}")));
        m.put(Msg.BOSS_BAR_PAUSED, new MessageSpec(MessageChannel.BOSSBAR,
                List.of("&e⏸ 可暂停额度已冻结 &8| &f剩余 {total} &8| &c不可暂停 {fixed}")));
        m.put(Msg.BOSS_BAR_FREE, new MessageSpec(MessageChannel.BOSSBAR,
                List.of("&d✦ 限免飞行中 &8| &f活动期间不消耗额度")));
        m.put(Msg.BOSS_BAR_ON, chat("&a✦ 已开启额度 BossBar 显示～"));
        m.put(Msg.BOSS_BAR_OFF, chat("&7✦ 已关闭额度 BossBar～想再开：&f/sf bossbar on"));

        m.put(Msg.QUOTA_RECEIVED, chat("&a✦ 收到飞行额度 &f{amount}&a（&f{type}&a），当前剩余 &f{total}"));

        m.put(Msg.ADMIN_QUOTA_GIVE, chat("&a✔ 已给 &f{target} &a增加 &f{amount} &a的&f{type}&a额度，当前剩余 &f{total}"));
        m.put(Msg.ADMIN_QUOTA_TAKE, chat("&e✔ 已扣除 &f{target} &e的 &f{amount} &e的&f{type}&e额度，当前剩余 &f{total}"));
        m.put(Msg.ADMIN_QUOTA_SET, chat("&a✔ 已把 &f{target} &a的&f{type}&a额度设为 &f{amount}&a，当前剩余 &f{total}"));
        m.put(Msg.ADMIN_QUOTA_GIVE_MANY, chat("&a✔ 已给 &f{count} &a名玩家各增加 &f{amount} &a的&f{type}&a额度"));
        m.put(Msg.ADMIN_QUOTA_TAKE_MANY, chat("&e✔ 已扣除 &f{count} &e名玩家各 &f{amount} &e的&f{type}&e额度"));
        m.put(Msg.ADMIN_QUOTA_SET_MANY, chat("&a✔ 已把 &f{count} &a名玩家的&f{type}&a额度统一设为 &f{amount}"));
        m.put(Msg.ADMIN_QUOTA_RESET, chat("&e✔ 已清空 &f{target} &e的所有飞行额度（共 &f{count} &e人）"));
        m.put(Msg.ADMIN_QUOTA_RESET_ALL, chat("&e✔ 已清空全部玩家的飞行额度（共 &f{count} &e条记录）"));
        m.put(Msg.ADMIN_QUOTA_CHECK_HEADER, chat("&b&m──────&r &f✈ &b额度查询 &b&m──────"));
        m.put(Msg.ADMIN_QUOTA_CHECK_LINE, chat("&7{target} &8» &b可暂停 {pausable} &8| &c不可暂停 {fixed} &8| &f合计 {total}&8{note}"));
        m.put(Msg.ADMIN_QUOTA_TOP_HEADER, chat("&b&m──────&r &f✈ &b额度排行 Top {count} &b&m──────"));
        m.put(Msg.ADMIN_QUOTA_TOP_LINE, chat("&7{index}. &f{target} &8» &f{total} &8(&b{pausable} &8| &c{fixed}&8)"));
        m.put(Msg.ADMIN_QUOTA_TOP_EMPTY, chat("&7还没有任何玩家的额度记录～"));

        m.put(Msg.ADMIN_EVENT_CREATED, chat("&a✔ 活动 &f{event}{free} &a已创建，&f{start} &a开始，持续 &f{event_duration}"));
        m.put(Msg.ADMIN_EVENT_EXTENDED, chat("&a✔ 活动 &f{event}{free} &a已延长 &f{amount}&a，剩余 &f{event_remain}"));
        m.put(Msg.ADMIN_EVENT_STOPPED, chat("&e✔ 活动 &f{event} &e已提前结束"));
        m.put(Msg.ADMIN_EVENT_STOP_ALL, chat("&e✔ 已结束全部活动（共 &f{count} &e个）"));
        m.put(Msg.ADMIN_EVENT_NOT_FOUND, chat("&c找不到名叫 &f{event} &c的活动～"));
        m.put(Msg.ADMIN_EVENT_PURGED, chat("&e✔ 已清理 &f{count} &e个已结束的活动"));
        m.put(Msg.ADMIN_EVENT_LIST_HEADER, chat("&b&m────&r &f✈ &b活动列表 &7（共 {count} 个，显示最新 {shown} 个）&b&m────"));
        m.put(Msg.ADMIN_EVENT_LIST_LINE, chat("&7{index}. &f{event}{free} &8| &7{status} &8| &7{event_remain}"));
        m.put(Msg.ADMIN_REPEAT_ADDED, chat("&a✔ 循环活动 &f{event}{free} &a已添加：&f{schedule}&a，每次持续 &f{event_duration}"));
        m.put(Msg.ADMIN_REPEAT_REMOVED, chat("&e✔ 已删除 &f{count} &e个循环活动"));
        m.put(Msg.ADMIN_REPEAT_LIST_HEADER, chat("&b&m──────&r &f✈ &b循环活动 &b&m──────"));
        m.put(Msg.ADMIN_REPEAT_LIST_LINE, chat("&7{index}. &f{event}{free} &8| &f{schedule} &8| &7时长 {event_duration} &8| &7{status}"));
        m.put(Msg.ADMIN_REPEAT_LIST_EMPTY, chat("&7还没有配置循环活动～"));

        m.put(Msg.ADMIN_FORCE_FLY, chat("&a✔ 已强制开启 &f{count} &a名玩家的飞行"));
        m.put(Msg.ADMIN_FORCE_UNFLY, chat("&e✔ 已强制关闭 &f{count} &e名玩家的飞行"));
        m.put(Msg.FORCED_FLY_ON, chat("&a✦ 管理员为你开启了飞行～"));
        m.put(Msg.FORCED_FLY_OFF, chat("&e✦ 管理员关闭了你的飞行～"));

        m.put(Msg.ADMIN_RELOADED, chat("&a✔ 配置已重载！"));
        m.put(Msg.ADMIN_BAD_DURATION, chat("&c时间格式不对：&f{error}"));
        m.put(Msg.ADMIN_BAD_TIME, chat("&c时间格式不对，应该像 &f20:00 &c这样：&f{error}"));
        m.put(Msg.ADMIN_NO_TARGET, chat("&c没找到任何匹配的玩家：&f{target}"));
        m.put(Msg.INFO, new MessageSpec(MessageChannel.CHAT, List.of(
                "&b&m────────&r &f✈ &bSFly 运行状态 &b&m────────",
                "&7在线飞行中：&f{flying_count} &7/ 在线 &f{online}",
                "&7额度档案：&f{records} &7份",
                "&7一次性活动：&f{event_count} &7个（进行中 &f{active_count} &7个）",
                "&7循环活动：&f{repeat_count} &7个",
                "&7已结束活动保留：&f{keep_finished}",
                "&7需有活动才能飞：&f{require_event}",
                "&b&m────────────────────────")));

        return m;
    }

    private static MessageSpec chat(String... lines) {
        return new MessageSpec(MessageChannel.CHAT, lines);
    }
}
