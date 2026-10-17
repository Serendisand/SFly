package com.serendisand.sfly.task;

import com.serendisand.sfly.SFlyConstants;
import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.config.SFlyConfig;
import com.serendisand.sfly.data.ActiveWindow;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.data.PlayerQuota;
import com.serendisand.sfly.event.EventService;
import com.serendisand.sfly.flight.FlightService;
import com.serendisand.sfly.quota.QuotaService;
import com.serendisand.sfly.text.Durations;
import com.serendisand.sfly.text.Msg;
import com.serendisand.sfly.text.MessageService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 每 tick 的驱动：额度结算、飞行会话维持、HUD 刷新、活动起止广播。 */
public class FlyTicker {
    /** actionbar 关掉时 BossBar 仍然按这个节拍刷 */
    private static final int FALLBACK_HUD_INTERVAL = 10;

    private final ConfigService configService;
    private final DataService dataService;
    private final QuotaService quotaService;
    private final EventService eventService;
    private final FlightService flightService;
    private final MessageService messages;

    private int settleCounter;
    private int hudCounter;
    private final Map<String, String> lastActiveWindowNames = new LinkedHashMap<>();
    private boolean eventTrackingReady;

    public FlyTicker(ConfigService configService, DataService dataService, QuotaService quotaService,
                     EventService eventService, FlightService flightService, MessageService messages) {
        this.configService = configService;
        this.dataService = dataService;
        this.quotaService = quotaService;
        this.eventService = eventService;
        this.flightService = flightService;
        this.messages = messages;
    }

    public void onEndTick(MinecraftServer server) {
        try {
            tick(server);
        } catch (Exception e) {
            // tick 里漏异常会拖垮整个服务器，必须兜住
            SFlyConstants.LOGGER.error("SFly tick 异常", e);
        }
    }

    private void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        SFlyConfig config = configService.get();
        long now = System.currentTimeMillis();

        trackEvents(server, now, config);
        dataService.tickAutoSave(config.storage.autoSaveMinutes);

        boolean settleNow = ++settleCounter >= config.quota.settleIntervalTicks;
        if (settleNow) {
            settleCounter = 0;
            // 过期活动清理每秒扫一次就够，没必要每 tick 都遍历一遍列表
            if (eventService.pruneFinished(now)) {
                dataService.saveAsync();
            }
        }
        int hudInterval = config.display.actionbarIntervalTicks > 0
                ? config.display.actionbarIntervalTicks
                : FALLBACK_HUD_INTERVAL;
        boolean hudNow = ++hudCounter >= hudInterval;
        if (hudNow) {
            hudCounter = 0;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                tickPlayer(player, now, config, settleNow, hudNow);
            } catch (Exception e) {
                SFlyConstants.LOGGER.error("处理玩家 {} 的飞行状态时出错", player.getScoreboardName(), e);
            }
        }
    }

    private void tickPlayer(ServerPlayer player, long now, SFlyConfig config,
                            boolean settleNow, boolean hudNow) {
        // 创造/旁观的原版飞行跟我们没关系，一根手指都不碰
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        PlayerQuota quota = quotaService.getOrCreate(player.getUUID(), player.getScoreboardName());

        if (settleNow) {
            if (eventService.hasFreeActive(now)) {
                // 限免窗口内不扣额度，只把基准拨到当下
                quotaService.freeze(quota, now);
            } else {
                quotaService.settle(quota, true, now);
                warnIfLow(player, quota, now, config);
            }
        }

        flightService.maintain(player, quota, now);

        if (hudNow) {
            updateHud(player, quota, config);
        }
    }

    private void updateHud(ServerPlayer player, PlayerQuota quota, SFlyConfig config) {
        if (!quota.isFlightGranted()) {
            messages.hideBossBar(player);
            return;
        }
        Object[] pairs = FlightService.quotaPairs(quota);
        // 限免时余额是静止的，显示「剩余 0秒」只会让人困惑，换成限免横幅
        boolean free = eventService.hasFreeActive(System.currentTimeMillis());
        boolean paused = quota.isPaused() && !free;

        if (config.display.actionbarIntervalTicks > 0) {
            messages.send(player, hudKey(free, paused, true), pairs);
        }
        if (config.display.bossBarEnabled && !quota.isBossBarHidden()) {
            messages.sendBossBar(player, hudKey(free, paused, false),
                    free ? 1.0f : bossBarProgress(quota, config), pairs);
        } else {
            // 玩家自己关了（或服主关掉了全局开关）—— 存量也得收掉，
            // 不然 BossBar 会一直挂在屏幕顶上
            messages.hideBossBar(player);
        }
    }

    private static String hudKey(boolean free, boolean paused, boolean actionBar) {
        if (free) {
            return actionBar ? Msg.ACTIONBAR_FREE : Msg.BOSS_BAR_FREE;
        }
        if (paused) {
            return actionBar ? Msg.ACTIONBAR_PAUSED : Msg.BOSS_BAR_PAUSED;
        }
        return actionBar ? Msg.ACTIONBAR_FLYING : Msg.BOSS_BAR_FLYING;
    }

    private float bossBarProgress(PlayerQuota quota, SFlyConfig config) {
        long full = parseDuration(config.display.bossBarFullQuota);
        if (full <= 0L) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, (float) quota.total() / (float) full));
    }

    private void warnIfLow(ServerPlayer player, PlayerQuota quota, long now, SFlyConfig config) {
        // 没在飞就别提醒了，那是骚扰
        if (!quota.isFlightGranted() || !quota.hasAny()) {
            return;
        }
        long threshold = parseDuration(config.quota.warnThreshold);
        if (threshold <= 0L || quota.total() > threshold) {
            return;
        }
        long cooldown = config.quota.warnCooldownSeconds * 1000L;
        if (now - quota.getLastWarnAt() < cooldown) {
            return;
        }
        quota.setLastWarnAt(now);
        messages.send(player, Msg.QUOTA_WARNING, FlightService.quotaPairs(quota));
    }

    private static long parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        try {
            return Durations.parseMillis(raw);
        } catch (IllegalArgumentException e) {
            return -1L;
        }
    }

    /** 对比前后两 tick 的活动窗口，捡出「刚开始」和「刚结束」的。存名字是为了结束广播还能报出活动名。 */
    private void trackEvents(MinecraftServer server, long now, SFlyConfig config) {
        Map<String, String> current = new HashMap<>();
        for (ActiveWindow window : eventService.activeWindows(now)) {
            current.put(window.key(), window.name());
        }

        if (!eventTrackingReady) {
            // 服务器刚起来，本来就在跑的活动不算「刚开始」，别补一发广播
            lastActiveWindowNames.clear();
            lastActiveWindowNames.putAll(current);
            eventTrackingReady = true;
            return;
        }

        if (config.display.announceEventStart) {
            for (ActiveWindow window : eventService.activeWindows(now)) {
                if (!lastActiveWindowNames.containsKey(window.key())) {
                    messages.broadcast(server, Msg.EVENT_START, eventPairs(window, now));
                }
            }
        }
        if (config.display.announceEventEnd) {
            // 窗口已经不在活跃集合里了，只能从上一轮记录里把名字捞回来
            for (Map.Entry<String, String> entry : lastActiveWindowNames.entrySet()) {
                if (!current.containsKey(entry.getKey())) {
                    messages.broadcast(server, Msg.EVENT_END,
                            "event", entry.getValue(),
                            "event_id", entry.getKey(),
                            "event_remain", "0秒",
                            "event_remain_short", "0s",
                            "event_duration", "0秒");
                }
            }
        }

        lastActiveWindowNames.clear();
        lastActiveWindowNames.putAll(current);
    }

    public void onJoin(MinecraftServer server, ServerPlayer player) {
        try {
            if (player == null) {
                return;
            }
            long now = System.currentTimeMillis();
            PlayerQuota quota = quotaService.getOrCreate(player.getUUID(), player.getScoreboardName());

            if (eventService.hasFreeActive(now)) {
                // 限免期间连离线补算都免掉，不然刚上线就被扣一笔太膈应
                quotaService.freeze(quota, now);
            } else {
                // 离线期间不可暂停额度照样在跑，登录时一次性补算
                quotaService.catchUpOffline(quota, now);
            }
            flightService.clearSessionOnJoin(player, quota);

            if (!configService.get().display.announceEventOnJoin) {
                return;
            }
            ActiveWindow active = eventService.firstActive(now);
            if (active != null) {
                messages.send(player, Msg.EVENT_JOIN,
                        merge(eventPairs(active, now), FlightService.quotaPairs(quota)));
            }
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("处理玩家登录事件失败", e);
        }
    }

    public void onDisconnect(ServerPlayer player) {
        try {
            if (player == null) {
                return;
            }
            messages.hideBossBar(player);
            PlayerQuota quota = quotaService.get(player.getUUID());
            flightService.onDisconnect(player, quota, System.currentTimeMillis());
            dataService.saveAsync();
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("处理玩家离线事件失败", e);
        }
    }

    /** 受伤就断飞。由 ServerLivingEntityEvents.AFTER_DAMAGE 驱动。 */
    public void onDamage(ServerPlayer player) {
        try {
            PlayerQuota quota = quotaService.get(player.getUUID());
            if (quota != null) {
                flightService.onDamage(player, quota);
            }
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("处理玩家受伤事件失败", e);
        }
    }

    public static Object[] eventPairs(ActiveWindow window, long now) {
        return new Object[]{
                "event", window.name(),
                "event_id", window.key(),
                "event_remain", Durations.format(window.remaining(now)),
                "event_remain_short", Durations.formatShort(window.remaining(now)),
                "event_duration", Durations.format(window.duration()),
                "free", window.free() ? " &d[限免]" : "",
                "free_label", window.free() ? "限免" : "额度",
                "free_note", window.free() ? "\n&d✦ 限免期间不消耗额度，没有额度也能飞～" : "",
        };
    }

    public static Object[] merge(Object[] first, Object[] second) {
        Object[] merged = new Object[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }
}
