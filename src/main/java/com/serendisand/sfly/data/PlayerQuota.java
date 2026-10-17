package com.serendisand.sfly.data;

/**
 * 一个玩家的额度档案。
 *
 * <p>{@code lastSettleAt} 是「上次结算时刻」，不是「上次上线时刻」——
 * 离线期间不可暂停额度照样在跑，靠时间差一次性补算，服务器停机也一并算进去。
 */
public class PlayerQuota {
    private long pausableMillis;
    private long fixedMillis;
    /** 手动暂停开关，只影响可暂停额度 */
    private boolean paused;
    private long lastSettleAt;
    /** 我们是否给这个玩家授过飞行权限。只有 true 时才会去收回，避免误伤别的插件 */
    private boolean flightGranted;
    /**
     * 管理员用 /sf forcefly 强开的飞行。
     * 标记上就跳过额度/活动检查，不然下一 tick 就被「你没额度」给收回了。
     */
    private boolean flightForced;
    /** 飞行会话里「没在飞但也没落地」的起始时刻，-1 表示不在这种状态 */
    private long airborneSince = -1L;
    private long lastWarnAt;
    private String lastName = "";
    /**
     * 玩家自己把额度 BossBar 关了（/sf bossbar）。
     * 是显示偏好不是额度，所以 {@link #clear()} 不碰它。
     */
    private boolean bossBarHidden;

    public long balanceOf(QuotaType type) {
        return type == QuotaType.PAUSABLE ? pausableMillis : fixedMillis;
    }

    public void setBalance(QuotaType type, long millis) {
        long safe = Math.max(0L, millis);
        if (type == QuotaType.PAUSABLE) {
            pausableMillis = safe;
        } else {
            fixedMillis = safe;
        }
    }

    public void addBalance(QuotaType type, long delta) {
        setBalance(type, balanceOf(type) + delta);
    }

    public long total() {
        return pausableMillis + fixedMillis;
    }

    public boolean hasAny() {
        return total() > 0L;
    }

    public void clear() {
        pausableMillis = 0L;
        fixedMillis = 0L;
        paused = false;
        flightGranted = false;
        flightForced = false;
        airborneSince = -1L;
    }

    public long getPausableMillis() {
        return pausableMillis;
    }

    public long getFixedMillis() {
        return fixedMillis;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public long getLastSettleAt() {
        return lastSettleAt;
    }

    public void setLastSettleAt(long lastSettleAt) {
        this.lastSettleAt = lastSettleAt;
    }

    public boolean isFlightGranted() {
        return flightGranted;
    }

    public void setFlightGranted(boolean flightGranted) {
        this.flightGranted = flightGranted;
    }

    public boolean isFlightForced() {
        return flightForced;
    }

    public void setFlightForced(boolean flightForced) {
        this.flightForced = flightForced;
    }

    public long getAirborneSince() {
        return airborneSince;
    }

    public void setAirborneSince(long airborneSince) {
        this.airborneSince = airborneSince;
    }

    public long getLastWarnAt() {
        return lastWarnAt;
    }

    public void setLastWarnAt(long lastWarnAt) {
        this.lastWarnAt = lastWarnAt;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName == null ? "" : lastName;
    }

    public boolean isBossBarHidden() {
        return bossBarHidden;
    }

    public void setBossBarHidden(boolean bossBarHidden) {
        this.bossBarHidden = bossBarHidden;
    }
}
