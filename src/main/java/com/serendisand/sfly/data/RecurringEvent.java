package com.serendisand.sfly.data;

import com.serendisand.sfly.text.Zones;

/**
 * 定时循环开放的活动，比如「每天 20:00-22:00」。
 *
 * <p>不落盘具体窗口，只存规则，窗口每次由 {@link #windowStart} 现算 ——
 * 这样服务器停机、跨天、跨周都不用补状态。
 */
public class RecurringEvent {
    private String id = "";
    private String name = "";
    /** DAILY 或 WEEKLY */
    private String mode = "DAILY";
    /** 本地时间 HH:mm */
    private String time = "20:00";
    /** WEEKLY 用，1=周一 … 7=周日 */
    private int dayOfWeek = 1;
    private long durationMillis = 2 * 60 * 60 * 1000L;
    private boolean enabled = true;
    /** 限免：期间不消耗额度，没有额度的玩家也能飞 */
    private boolean free;

    public RecurringEvent() {
    }

    public RecurringEvent(String id, String name, String mode, String time, int dayOfWeek, long durationMillis) {
        this(id, name, mode, time, dayOfWeek, durationMillis, false);
    }

    public RecurringEvent(String id, String name, String mode, String time, int dayOfWeek,
                          long durationMillis, boolean free) {
        this.id = id;
        this.name = name;
        this.mode = mode;
        this.time = time;
        this.dayOfWeek = dayOfWeek;
        this.durationMillis = durationMillis;
        this.free = free;
    }

    /** 当前这一轮的具体时间窗。 */
    public ActiveWindow toWindow(long now) {
        long start = windowStart(now);
        return new ActiveWindow(windowKey(now), getName(), start, start + durationMillis, free);
    }

    public boolean isWeekly() {
        return "WEEKLY".equalsIgnoreCase(mode);
    }

    /**
     * 算出 now 所属（或最近一次）窗口的起始时刻。
     * 今天的还没到就看昨天那次是否还开着；本周的还没到就看上周那次。
     */
    public long windowStart(long now) {
        java.time.ZoneId zone = Zones.get();
        java.time.ZonedDateTime nowTime = java.time.Instant.ofEpochMilli(now).atZone(zone);
        java.time.LocalTime target = parseTime();

        java.time.LocalDate date = nowTime.toLocalDate();
        if (isWeekly()) {
            int current = date.getDayOfWeek().getValue();
            int delta = current - Math.max(1, Math.min(7, dayOfWeek));
            if (delta < 0) {
                delta += 7;
            }
            date = date.minusDays(delta);
        }

        java.time.ZonedDateTime candidate = date.atTime(target).atZone(zone);
        long start = candidate.toInstant().toEpochMilli();
        if (now >= start) {
            return start;
        }

        long previous = (isWeekly() ? candidate.minusWeeks(1) : candidate.minusDays(1))
                .toInstant().toEpochMilli();
        // 上一轮的尾巴还没结束就还挂在上一轮上，否则算下一轮
        return now < previous + durationMillis ? previous : start;
    }

    public boolean isActive(long now) {
        if (!enabled) {
            return false;
        }
        long start = windowStart(now);
        return now >= start && now < start + durationMillis;
    }

    public long remaining(long now) {
        long start = windowStart(now);
        if (now < start) {
            return 0L;
        }
        return Math.max(0L, start + durationMillis - now);
    }

    /** 距离下一次窗口开始的毫秒数，正在开放中返回 0。 */
    public long untilNext(long now) {
        long start = windowStart(now);
        if (now < start) {
            return start - now;
        }
        if (now < start + durationMillis) {
            return 0L;
        }
        long next = isWeekly()
                ? java.time.Instant.ofEpochMilli(start).atZone(Zones.get())
                        .plusWeeks(1).toInstant().toEpochMilli()
                : java.time.Instant.ofEpochMilli(start).atZone(Zones.get())
                        .plusDays(1).toInstant().toEpochMilli();
        return next - now;
    }

    private java.time.LocalTime parseTime() {
        try {
            return java.time.LocalTime.parse(time == null ? "20:00" : time.trim());
        } catch (Exception e) {
            return java.time.LocalTime.of(20, 0);
        }
    }

    /** 循环活动没有固定 id 给人记，用「名字 + 窗口起点」参与活动集合比对。 */
    public String windowKey(long now) {
        return "repeat:" + id + "@" + windowStart(now);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name == null || name.isEmpty() ? id : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFree() {
        return free;
    }

    public void setFree(boolean free) {
        this.free = free;
    }
}
