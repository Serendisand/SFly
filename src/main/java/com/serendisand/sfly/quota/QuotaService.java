package com.serendisand.sfly.quota;

import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.data.PlayerQuota;
import com.serendisand.sfly.data.QuotaType;
import com.serendisand.sfly.text.Durations;

import java.util.UUID;

/**
 * 额度的结算与发放。
 *
 * <p>核心模型是「实时流逝」：额度按墙上时间扣，不管玩家在不在飞。
 * 唯一能停住它的是「计时开关」——
 * <ul>
 *   <li>可暂停额度：玩家在线 且 没手动暂停时才走表（离线自然就停了）</li>
 *   <li>不可暂停额度：按配置决定离线是否继续走表，手动暂停管不着它</li>
 * </ul>
 */
public class QuotaService {
    private final DataService dataService;
    private final ConfigService configService;

    public QuotaService(DataService dataService, ConfigService configService) {
        this.dataService = dataService;
        this.configService = configService;
    }

    public PlayerQuota get(UUID uuid) {
        return uuid == null ? null : dataService.data().players.get(uuid.toString());
    }

    public PlayerQuota getOrCreate(UUID uuid, String name) {
        if (uuid == null) {
            return new PlayerQuota();
        }
        PlayerQuota quota = dataService.data().players.computeIfAbsent(uuid.toString(), key -> {
            PlayerQuota fresh = new PlayerQuota();
            // 新建的档案不能背一屁股历史时间，否则登录瞬间就被扣光
            fresh.setLastSettleAt(System.currentTimeMillis());
            return fresh;
        });
        if (name != null && !name.isEmpty()) {
            quota.setLastName(name);
        }
        return quota;
    }

    /**
     * 结算到 now 为止流逝掉的额度。
     *
     * @param online 玩家此刻是否在线。离线调用只会扣「不可暂停」那部分
     * @return 本次扣掉的毫秒数
     */
    public long settle(PlayerQuota quota, boolean online, long now) {
        if (quota == null) {
            return 0L;
        }
        long last = quota.getLastSettleAt();
        if (last <= 0L) {
            quota.setLastSettleAt(now);
            return 0L;
        }
        long elapsed = now - last;
        // 时钟回拨（NTP 校时等）时直接重置基准，否则会倒着扣
        if (elapsed <= 0L) {
            quota.setLastSettleAt(now);
            return 0L;
        }
        quota.setLastSettleAt(now);
        return drain(quota, elapsed, online);
    }

    /**
     * 限免期间用：把基准拨到「现在」但不扣任何额度，等于这段流逝的时间白送。
     */
    public void freeze(PlayerQuota quota, long now) {
        if (quota != null) {
            quota.setLastSettleAt(now);
        }
    }

    /** 玩家上线时补算离线期间跑掉的额度。 */
    public long catchUpOffline(PlayerQuota quota, long now) {
        if (quota == null) {
            return 0L;
        }
        long last = quota.getLastSettleAt();
        if (last <= 0L) {
            quota.setLastSettleAt(now);
            return 0L;
        }
        long limit = offlineDrainLimitMillis();
        if (limit > 0L) {
            long earliest = now - limit;
            if (last < earliest) {
                // 服务器停机太久，超出上限的部分不认账
                quota.setLastSettleAt(earliest);
            }
        }
        return settle(quota, false, now);
    }

    private long drain(PlayerQuota quota, long elapsed, boolean online) {
        long remaining = elapsed;
        long consumed = 0L;
        // 一种额度烧完可能接到另一种，所以得循环；上限 4 次纯属保险
        for (int guard = 0; guard < 4 && remaining > 0L; guard++) {
            QuotaType active = pickActive(quota, online);
            if (active == null) {
                break;
            }
            long balance = quota.balanceOf(active);
            long used = Math.min(balance, remaining);
            if (used <= 0L) {
                break;
            }
            quota.setBalance(active, balance - used);
            consumed += used;
            remaining -= used;
        }
        return consumed;
    }

    private QuotaType pickActive(PlayerQuota quota, boolean online) {
        boolean fixedUsable = quota.getFixedMillis() > 0L
                && (online || configService.get().quota.fixedDrainsOffline);
        boolean pausableUsable = quota.getPausableMillis() > 0L
                && online
                && !quota.isPaused();

        if (pausableFirst()) {
            if (pausableUsable) {
                return QuotaType.PAUSABLE;
            }
            return fixedUsable ? QuotaType.FIXED : null;
        }
        if (fixedUsable) {
            return QuotaType.FIXED;
        }
        return pausableUsable ? QuotaType.PAUSABLE : null;
    }

    private boolean pausableFirst() {
        String order = configService.get().quota.consumeOrder;
        return order != null && order.trim().equalsIgnoreCase("PAUSABLE_FIRST");
    }

    private long offlineDrainLimitMillis() {
        String raw = configService.get().quota.offlineDrainLimit;
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        try {
            return Durations.parseMillis(raw);
        } catch (IllegalArgumentException e) {
            return -1L;
        }
    }

    public void grant(UUID uuid, String name, QuotaType type, long millis) {
        if (millis <= 0L) {
            return;
        }
        getOrCreate(uuid, name).addBalance(type, millis);
    }

    public void take(UUID uuid, String name, QuotaType type, long millis) {
        if (millis <= 0L) {
            return;
        }
        getOrCreate(uuid, name).addBalance(type, -millis);
    }

    public void set(UUID uuid, String name, QuotaType type, long millis) {
        getOrCreate(uuid, name).setBalance(type, millis);
    }

    public void reset(UUID uuid) {
        PlayerQuota quota = get(uuid);
        if (quota != null) {
            quota.clear();
        }
    }

    public int resetAll() {
        int count = dataService.data().players.size();
        dataService.data().players.values().forEach(PlayerQuota::clear);
        return count;
    }
}
