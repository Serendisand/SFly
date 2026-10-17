package com.serendisand.sfly.event;

import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.data.ActiveWindow;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.data.FlyEvent;
import com.serendisand.sfly.data.RecurringEvent;
import com.serendisand.sfly.text.Durations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 活动的生命周期。
 *
 * <p>活动的「当前是否开放」每秒只算一次然后缓存 —— 循环活动要跑 {@code ZonedDateTime}
 * 运算，每 tick 对每个玩家算一遍太浪费。
 */
public class EventService {
    /** 活动开放状态每秒只算一次然后缓存 */
    private static final long ACTIVE_CACHE_TTL = 1000L;
    /** 配置读不出来时的兜底保留期 */
    private static final long DEFAULT_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000;
    /** 配置里写这些值表示「永不清理」 */
    private static final java.util.Set<String> NEVER = java.util.Set.of("-1", "never", "off", "false", "永不");

    private final DataService dataService;
    private final ConfigService configService;
    private List<ActiveWindow> activeCache = List.of();
    private long activeCacheAt;
    /**
     * 缓存是否有效。不能用「activeCacheAt 设成 Long.MIN_VALUE 当哨兵」那套 ——
     * {@code now - Long.MIN_VALUE} 会整数溢出成负数，判断永远不成立，缓存就再也不刷新了。
     */
    private boolean cacheValid;

    public EventService(DataService dataService, ConfigService configService) {
        this.dataService = dataService;
        this.configService = configService;
    }

    public List<FlyEvent> allEvents() {
        return dataService.data().events;
    }

    public List<RecurringEvent> allRecurring() {
        return dataService.data().recurring;
    }

    public List<ActiveWindow> activeWindows(long now) {
        // now < activeCacheAt 是时钟回拨，差值会变负，不重算就会一直发旧窗口
        if (!cacheValid || now < activeCacheAt || now - activeCacheAt >= ACTIVE_CACHE_TTL) {
            activeCache = computeActive(now);
            activeCacheAt = now;
            cacheValid = true;
        }
        return activeCache;
    }

    public boolean hasActive(long now) {
        return !activeWindows(now).isEmpty();
    }

    /** 现在是否处在「限免」窗口里。限免期间不消耗额度，没额度的玩家也能飞。 */
    public boolean hasFreeActive(long now) {
        for (ActiveWindow window : activeWindows(now)) {
            if (window.free()) {
                return true;
            }
        }
        return false;
    }

    public ActiveWindow firstActive(long now) {
        List<ActiveWindow> windows = activeWindows(now);
        return windows.isEmpty() ? null : windows.getFirst();
    }

    private List<ActiveWindow> computeActive(long now) {
        List<ActiveWindow> windows = new ArrayList<>();
        for (FlyEvent event : allEvents()) {
            if (event != null && event.isActive(now)) {
                windows.add(event.toWindow());
            }
        }
        for (RecurringEvent recurring : allRecurring()) {
            if (recurring != null && recurring.isActive(now)) {
                windows.add(recurring.toWindow(now));
            }
        }
        return List.copyOf(windows);
    }

    /** 增删活动后调一下，别让缓存拖到下一秒才生效。 */
    public void invalidate() {
        cacheValid = false;
    }

    public FlyEvent create(String name, long durationMillis, long delayMillis) {
        return create(name, durationMillis, delayMillis, false);
    }

    public FlyEvent create(String name, long durationMillis, long delayMillis, boolean free) {
        long now = System.currentTimeMillis();
        long start = now + Math.max(0L, delayMillis);
        return createAt(name, start, start + durationMillis, free);
    }

    /** 起止时刻都定死的建法，给「9月25日0点 → 9月27日23:59」这种排期用。 */
    public FlyEvent createAt(String name, long startAt, long endAt, boolean free) {
        FlyEvent event = new FlyEvent(
                UUID.randomUUID().toString().substring(0, 8),
                name,
                startAt,
                endAt,
                free);
        allEvents().add(event);
        invalidate();
        return event;
    }

    /** 延长活动。已经结束的返回 false。 */
    public boolean extend(FlyEvent event, long extraMillis) {
        if (event == null || extraMillis <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (event.isFinished(now)) {
            return false;
        }
        event.setEndAt(event.getEndAt() + extraMillis);
        invalidate();
        return true;
    }

    public FlyEvent findEvent(String idOrName) {
        return match(allEvents(), idOrName);
    }

    public RecurringEvent findRecurring(String idOrName) {
        return match(allRecurring(), idOrName);
    }

    private static <T> T match(List<T> list, String idOrName) {
        if (idOrName == null || idOrName.isBlank()) {
            return null;
        }
        String needle = idOrName.trim();
        for (T item : list) {
            if (item == null) {
                continue;
            }
            String id = item instanceof FlyEvent e ? e.getId() : ((RecurringEvent) item).getId();
            if (id.equalsIgnoreCase(needle)) {
                return item;
            }
        }
        for (T item : list) {
            if (item == null) {
                continue;
            }
            String name = item instanceof FlyEvent e ? e.getName() : ((RecurringEvent) item).getName();
            if (name.equalsIgnoreCase(needle)) {
                return item;
            }
        }
        return null;
    }

    public int stopAll(long now) {
        int count = 0;
        for (FlyEvent event : allEvents()) {
            if (event != null && !event.isFinished(now)) {
                event.setEndAt(now);
                count++;
            }
        }
        invalidate();
        return count;
    }

    public RecurringEvent addRecurring(String name, String mode, String time, int dayOfWeek, long durationMillis) {
        return addRecurring(name, mode, time, dayOfWeek, durationMillis, false);
    }

    public RecurringEvent addRecurring(String name, String mode, String time, int dayOfWeek,
                                       long durationMillis, boolean free) {
        RecurringEvent recurring = new RecurringEvent(
                UUID.randomUUID().toString().substring(0, 8), name, mode, time, dayOfWeek, durationMillis, free);
        allRecurring().add(recurring);
        invalidate();
        return recurring;
    }

    /** {@code idOrName} 传 "all" 表示全删。返回删掉的个数。 */
    public int removeRecurring(String idOrName) {
        List<RecurringEvent> list = allRecurring();
        if ("all".equalsIgnoreCase(idOrName)) {
            int count = list.size();
            list.clear();
            invalidate();
            return count;
        }
        RecurringEvent target = findRecurring(idOrName);
        if (target == null) {
            return 0;
        }
        list.remove(target);
        invalidate();
        return 1;
    }

    /**
     * 清理早就结束的一次性活动。默认保留 30 天（{@code events.keepFinishedFor}），
     * 保留期内的活动在 data.json 里存着，重启也不丢，{@code /sf event list} 还能翻到。
     *
     * @return 是否真的删掉了东西（调用方据此决定要不要落盘）
     */
    public boolean pruneFinished(long now) {
        long retention = retentionMillis();
        if (retention < 0L) {
            // 配置成永不清理
            return false;
        }
        long cutoff = now - retention;
        boolean removed = allEvents().removeIf(event -> event == null || event.getEndAt() < cutoff);
        if (removed) {
            invalidate();
        }
        return removed;
    }

    /** 手动清掉所有已结束的一次性活动，返回清掉的个数。 */
    public int purgeFinished(long now) {
        List<FlyEvent> events = allEvents();
        int before = events.size();
        events.removeIf(event -> event == null || event.isFinished(now));
        int removed = before - events.size();
        if (removed > 0) {
            invalidate();
        }
        return removed;
    }

    /** 保留期毫秒数。负数表示永不清理。 */
    public long retentionMillis() {
        String raw = configService.get().events.keepFinishedFor;
        if (raw == null || raw.isBlank()) {
            return DEFAULT_RETENTION_MILLIS;
        }
        if (NEVER.contains(raw.trim().toLowerCase(java.util.Locale.ROOT))) {
            return -1L;
        }
        try {
            return Durations.parseMillis(raw);
        } catch (IllegalArgumentException e) {
            // 配置写错了就用默认值，别让清理逻辑失灵把历史全删了
            return DEFAULT_RETENTION_MILLIS;
        }
    }
}
