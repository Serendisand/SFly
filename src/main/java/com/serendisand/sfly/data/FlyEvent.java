package com.serendisand.sfly.data;

/** 一场限时活动。到点自动开始/结束，不需要人盯着。 */
public class FlyEvent {
    private String id = "";
    private String name = "";
    private long startAt;
    private long endAt;
    /** 限免：期间不消耗额度，没有额度的玩家也能飞 */
    private boolean free;

    public FlyEvent() {
    }

    public FlyEvent(String id, String name, long startAt, long endAt) {
        this(id, name, startAt, endAt, false);
    }

    public FlyEvent(String id, String name, long startAt, long endAt, boolean free) {
        this.id = id;
        this.name = name;
        this.startAt = startAt;
        this.endAt = endAt;
        this.free = free;
    }

    public boolean isActive(long now) {
        return now >= startAt && now < endAt;
    }

    public boolean isPending(long now) {
        return now < startAt;
    }

    /** 归一成通用时间窗，让上层的广播/提示不用区分一次性活动和循环活动。 */
    public ActiveWindow toWindow() {
        return new ActiveWindow("event:" + id, getName(), startAt, endAt, free);
    }

    public boolean isFree() {
        return free;
    }

    public void setFree(boolean free) {
        this.free = free;
    }

    public boolean isFinished(long now) {
        return now >= endAt;
    }

    public long remaining(long now) {
        return Math.max(0L, endAt - now);
    }

    public long duration() {
        return Math.max(0L, endAt - startAt);
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

    public long getStartAt() {
        return startAt;
    }

    public void setStartAt(long startAt) {
        this.startAt = startAt;
    }

    public long getEndAt() {
        return endAt;
    }

    public void setEndAt(long endAt) {
        this.endAt = endAt;
    }
}
