package com.serendisand.sfly.data;

/**
 * 一段「现在可以飞」的时间窗。一次性活动和循环活动都归一到这个类型，
 * 上层（tick 广播、登录提示、条件判断）就不用分两套逻辑了。
 *
 * @param free 限免窗口：不消耗额度，没有额度的玩家也能飞
 */
public record ActiveWindow(String key, String name, long startAt, long endAt, boolean free) {
    public long remaining(long now) {
        return Math.max(0L, endAt - now);
    }

    public long duration() {
        return Math.max(0L, endAt - startAt);
    }

    public boolean isActive(long now) {
        return now >= startAt && now < endAt;
    }

    /** 给文案用的标记：限免窗口会显示一个粉色小标签。 */
    public String freeTag(String coloredTag) {
        return free ? coloredTag : "";
    }
}
