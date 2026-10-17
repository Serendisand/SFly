package com.serendisand.sfly.data;

import java.util.Locale;

/**
 * 额度类型。
 *
 * <p>两种额度都是「实时流逝」的：只要计时开关是开的，不管玩家在不在飞都在扣。
 * 区别只在计时开关什么时候会关上 —— 这正是「暂停」的含义。
 */
public enum QuotaType {
    /** 可暂停：玩家可以用 /sf pause 手动暂停，离线也会自动暂停 */
    PAUSABLE("可暂停"),
    /** 不可暂停：不理会手动暂停，也按配置决定离线是否继续扣 */
    FIXED("不可暂停");

    private final String displayName;

    QuotaType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static QuotaType parse(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pausable", "pause", "p", "可暂停" -> PAUSABLE;
            case "fixed", "nopause", "f", "不可暂停" -> FIXED;
            default -> null;
        };
    }
}
