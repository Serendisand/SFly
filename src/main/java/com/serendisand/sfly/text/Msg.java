package com.serendisand.sfly.text;

/** 所有可自定义的文案键。配置里改值即可，键名别动。 */
public final class Msg {
    private Msg() {
    }

    // ---- 通用 ----
    public static final String NO_PERMISSION = "no-permission";
    public static final String PLAYER_ONLY = "player-only";
    public static final String UNKNOWN_COMMAND = "unknown-command";

    // ---- 飞行 ----
    public static final String FLY_ON = "fly-on";
    public static final String FLY_OFF = "fly-off";
    public static final String FLY_BLOCKED_NO_EVENT = "fly-blocked-no-event";
    public static final String FLY_BLOCKED_NO_QUOTA = "fly-blocked-no-quota";
    public static final String FLY_BLOCKED_GAMEMODE = "fly-blocked-gamemode";
    public static final String FLY_BLOCKED_PAUSED = "fly-blocked-paused";
    public static final String FLY_BLOCKED_WORLD = "fly-blocked-world";
    public static final String FLY_BLOCKED_HEIGHT = "fly-blocked-height";
    public static final String FLY_DISABLED_BY_DAMAGE = "fly-disabled-by-damage";
    public static final String QUOTA_EXHAUSTED = "quota-exhausted";
    public static final String QUOTA_EXHAUSTED_PROTECT = "quota-exhausted-protect";
    public static final String QUOTA_WARNING = "quota-warning";
    public static final String EVENT_ENDED_KICK = "event-ended-kick";

    // ---- 暂停 ----
    public static final String PAUSE_ON = "pause-on";
    public static final String PAUSE_OFF = "pause-off";
    public static final String PAUSE_UNAVAILABLE = "pause-unavailable";
    public static final String PAUSE_ALREADY = "pause-already";
    public static final String RESUME_ALREADY = "resume-already";

    // ---- 状态 / 活动 ----
    public static final String STATUS = "status";
    public static final String STATUS_OTHER = "status-other";
    public static final String STATUS_NONE = "status-none";
    public static final String EVENT_JOIN = "event-join";
    public static final String EVENT_START = "event-start";
    public static final String EVENT_END = "event-end";
    public static final String EVENT_ACTIVE_LINE = "event-active-line";
    public static final String EVENT_NONE = "event-none";

    // ---- Actionbar / BossBar ----
    public static final String ACTIONBAR_FLYING = "actionbar-flying";
    public static final String ACTIONBAR_PAUSED = "actionbar-paused";
    public static final String ACTIONBAR_FREE = "actionbar-free";
    public static final String BOSS_BAR_FLYING = "bossbar-flying";
    public static final String BOSS_BAR_PAUSED = "bossbar-paused";
    public static final String BOSS_BAR_FREE = "bossbar-free";
    public static final String BOSS_BAR_ON = "bossbar-on";
    public static final String BOSS_BAR_OFF = "bossbar-off";

    // ---- 管理：额度 ----
    public static final String ADMIN_QUOTA_GIVE = "admin-quota-give";
    public static final String ADMIN_QUOTA_TAKE = "admin-quota-take";
    public static final String ADMIN_QUOTA_SET = "admin-quota-set";
    /** 目标是 @a 这类批量时用的汇总文案，免得刷 50 行 */
    public static final String ADMIN_QUOTA_GIVE_MANY = "admin-quota-give-many";
    public static final String ADMIN_QUOTA_TAKE_MANY = "admin-quota-take-many";
    public static final String ADMIN_QUOTA_SET_MANY = "admin-quota-set-many";
    public static final String ADMIN_QUOTA_RESET = "admin-quota-reset";
    public static final String ADMIN_QUOTA_RESET_ALL = "admin-quota-reset-all";
    public static final String ADMIN_QUOTA_CHECK_HEADER = "admin-quota-check-header";
    public static final String ADMIN_QUOTA_CHECK_LINE = "admin-quota-check-line";
    public static final String ADMIN_QUOTA_TOP_HEADER = "admin-quota-top-header";
    public static final String ADMIN_QUOTA_TOP_LINE = "admin-quota-top-line";
    public static final String ADMIN_QUOTA_TOP_EMPTY = "admin-quota-top-empty";

    // ---- 管理：活动 ----
    public static final String ADMIN_EVENT_CREATED = "admin-event-created";
    public static final String ADMIN_EVENT_EXTENDED = "admin-event-extended";
    public static final String ADMIN_EVENT_STOPPED = "admin-event-stopped";
    public static final String ADMIN_EVENT_STOP_ALL = "admin-event-stop-all";
    public static final String ADMIN_EVENT_NOT_FOUND = "admin-event-not-found";
    public static final String ADMIN_EVENT_PURGED = "admin-event-purged";
    public static final String ADMIN_EVENT_LIST_HEADER = "admin-event-list-header";
    public static final String ADMIN_EVENT_LIST_LINE = "admin-event-list-line";
    public static final String ADMIN_REPEAT_ADDED = "admin-repeat-added";
    public static final String ADMIN_REPEAT_REMOVED = "admin-repeat-removed";
    public static final String ADMIN_REPEAT_LIST_HEADER = "admin-repeat-list-header";
    public static final String ADMIN_REPEAT_LIST_LINE = "admin-repeat-list-line";
    public static final String ADMIN_REPEAT_LIST_EMPTY = "admin-repeat-list-empty";

    // ---- 管理：强制飞行 ----
    public static final String ADMIN_FORCE_FLY = "admin-force-fly";
    public static final String ADMIN_FORCE_UNFLY = "admin-force-unfly";
    public static final String FORCED_FLY_ON = "forced-fly-on";
    public static final String FORCED_FLY_OFF = "forced-fly-off";

    // ---- 管理：杂项 ----
    public static final String ADMIN_RELOADED = "admin-reloaded";
    public static final String ADMIN_BAD_DURATION = "admin-bad-duration";
    public static final String ADMIN_NO_TARGET = "admin-no-target";
    public static final String ADMIN_BAD_TIME = "admin-bad-time";
    public static final String INFO = "info";

    // ---- 给被发放额度的玩家 ----
    public static final String QUOTA_RECEIVED = "quota-received";
}
