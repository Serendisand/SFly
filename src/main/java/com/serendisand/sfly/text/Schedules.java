package com.serendisand.sfly.text;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 绝对时间点的解析，用来给活动排期。
 *
 * <p>支持三种写法：
 * <ul>
 *   <li>{@code 2026-10-01T00:00} —— 完整日期时间</li>
 *   <li>{@code 10-01T00:00} —— 省略年份，按当前年算（已经过了就顺延到明年）</li>
 *   <li>{@code 2026-10-01} —— 只写日期，按当天 00:00</li>
 *   <li>{@code 20:00} —— 只写时间，按今天；已经过了就顺延到明天</li>
 * </ul>
 */
public final class Schedules {
    private Schedules() {
    }

    /**
     * @param now 当前时刻，用来补全省略的年份、判断「今天这个点过了没」
     * @return 目标时刻的 epoch millis
     */
    public static long resolve(String raw, long now) {
        try {
            return doResolve(raw, now);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            // java.time 对非法日期（13 月、45 号）抛的是 DateTimeException，不是 IllegalArgumentException，
            // 不在这里收掉的话异常会从命令里逃出去，管理员只看到一个莫名其妙的报错
            throw new IllegalArgumentException("看不懂的时间点：" + raw);
        }
    }

    private static long doResolve(String raw, long now) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("时间不能为空");
        }
        String text = raw.trim();
        ZoneId zone = Zones.get();
        ZonedDateTime nowTime = Instant.ofEpochMilli(now).atZone(zone);

        // 只写 HH:mm —— 今天该点，过了就明天
        if (text.indexOf('T') < 0 && text.indexOf('-') < 0) {
            LocalTime time = parseTime(text);
            ZonedDateTime candidate = nowTime.toLocalDate().atTime(time).atZone(zone);
            if (candidate.toInstant().toEpochMilli() <= now) {
                candidate = candidate.plusDays(1);
            }
            return candidate.toInstant().toEpochMilli();
        }

        String datePart = text;
        LocalTime time = LocalTime.MIDNIGHT;
        int separator = text.indexOf('T');
        if (separator >= 0) {
            datePart = text.substring(0, separator);
            time = parseTime(text.substring(separator + 1));
        }

        LocalDate date = parseDate(datePart, nowTime);
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli();
    }

    private static LocalDate parseDate(String datePart, ZonedDateTime nowTime) {
        if (datePart.isEmpty()) {
            return nowTime.toLocalDate();
        }
        String[] parts = datePart.split("-");
        if (parts.length == 2) {
            LocalDate date = LocalDate.of(nowTime.getYear(),
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            // 没写年份时，日子已经过了就往明年顺延。
            // 这样「12-30 到 01-02」这种跨年排期也能一次写对，不用手动补年份。
            if (date.isBefore(nowTime.toLocalDate())) {
                date = date.plusYears(1);
            }
            return date;
        }
        if (parts.length == 3) {
            return LocalDate.of(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        throw new IllegalArgumentException("日期要写成 2026-10-01、10-01 或 2026-10-01T00:00：" + datePart);
    }

    private static LocalTime parseTime(String text) {
        try {
            String normalized = text.trim();
            if (normalized.indexOf(':') < 0 && normalized.length() == 4) {
                // 兼容 2000 这种写法
                normalized = normalized.substring(0, 2) + ":" + normalized.substring(2);
            }
            if (normalized.indexOf(':') > 0 && normalized.indexOf(':') < 2 && normalized.charAt(0) != '0') {
                // 兼容 8:00 这种没补零的写法
                normalized = "0" + normalized;
            }
            return LocalTime.parse(normalized);
        } catch (Exception e) {
            throw new IllegalArgumentException("看不懂的时间点：" + text + "（应该像 20:00 这样）");
        }
    }

    /** 给回显用的可读格式。 */
    public static String describe(long epochMillis) {
        ZonedDateTime time = Instant.ofEpochMilli(epochMillis).atZone(Zones.get());
        return String.format("%04d-%02d-%02d %02d:%02d",
                time.getYear(), time.getMonthValue(), time.getDayOfMonth(),
                time.getHour(), time.getMinute());
    }
}
