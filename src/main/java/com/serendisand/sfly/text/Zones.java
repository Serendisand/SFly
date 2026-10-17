package com.serendisand.sfly.text;

import java.time.ZoneId;

/**
 * 全局时区。
 *
 * <p>为什么要有这个：{@code ZoneId.systemDefault()} 读的是 JVM 时区，
 * 而很多服务器面板默认把 JVM 跑在 UTC。那样管理员在配置里写「每天 20:00」，
 * 实际开放时间是北京时间凌晨 4 点，而且从日志上完全看不出来。
 */
public final class Zones {
    private static volatile ZoneId current = ZoneId.systemDefault();

    private Zones() {
    }

    /** 加载配置后调一次。写空或写错都退回 JVM 默认值。 */
    public static void configure(String raw) {
        current = parse(raw);
    }

    public static ZoneId get() {
        return current;
    }

    public static String describe() {
        return current.getId();
    }

    private static ZoneId parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
