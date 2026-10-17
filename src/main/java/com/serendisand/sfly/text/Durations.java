package com.serendisand.sfly.text;

import java.util.Locale;

/**
 * 时长解析 / 格式化。支持 {@code 90}、{@code 90s}、{@code 1d2h30m}、{@code 1天2小时30分} 及混合写法。
 */
public final class Durations {
    public static final long SECOND = 1000L;
    public static final long MINUTE = 60 * SECOND;
    public static final long HOUR = 60 * MINUTE;
    public static final long DAY = 24 * HOUR;
    public static final long WEEK = 7 * DAY;

    private Durations() {
    }

    public static long parseMillis(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("时间不能为空");
        }
        String s = input.trim().toLowerCase(Locale.ROOT);

        long total = 0L;
        int index = 0;
        int segments = 0;
        while (index < s.length()) {
            while (index < s.length() && Character.isWhitespace(s.charAt(index))) {
                index++;
            }
            if (index >= s.length()) {
                break;
            }

            int numberStart = index;
            boolean dotSeen = false;
            while (index < s.length()) {
                char c = s.charAt(index);
                if (Character.isDigit(c)) {
                    index++;
                } else if (c == '.' && !dotSeen) {
                    dotSeen = true;
                    index++;
                } else {
                    break;
                }
            }
            if (index == numberStart) {
                throw new IllegalArgumentException("看不懂的时间格式：" + input);
            }
            double number = Double.parseDouble(s.substring(numberStart, index));

            // 单位一直读到下一个数字为止，这样中文单位（天/小时）也能吃下
            int unitStart = index;
            while (index < s.length() && !Character.isDigit(s.charAt(index))) {
                index++;
            }
            String unit = s.substring(unitStart, index).trim();

            total += Math.round(number * factorOf(unit, input));
            segments++;
        }

        if (segments == 0 || total <= 0L) {
            throw new IllegalArgumentException("时间必须大于 0：" + input);
        }
        return total;
    }

    private static long factorOf(String unit, String raw) {
        return switch (unit) {
            case "", "s", "sec", "secs", "second", "seconds", "秒" -> SECOND;
            case "m", "min", "mins", "minute", "minutes", "分", "分钟" -> MINUTE;
            case "h", "hr", "hrs", "hour", "hours", "时", "小时" -> HOUR;
            case "d", "day", "days", "天", "日" -> DAY;
            case "w", "week", "weeks", "周", "星期" -> WEEK;
            default -> throw new IllegalArgumentException("未知的时间单位「" + unit + "」（" + raw + "）");
        };
    }

    /** 中文可读格式：{@code 1天2小时3分4秒}。不足 1 秒按 1 秒显示，免得看到「0秒」却还能飞。 */
    public static String format(long millis) {
        long seconds = Math.max(0L, millis) / SECOND;
        if (seconds <= 0L && millis > 0L) {
            return "1秒";
        }
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append('天');
        }
        if (hours > 0) {
            out.append(hours).append("小时");
        }
        if (minutes > 0) {
            out.append(minutes).append('分');
        }
        if (seconds > 0) {
            out.append(seconds).append('秒');
        }
        return out.isEmpty() ? "0秒" : out.toString();
    }

    /** 紧凑格式，给 actionbar 用：{@code 1d2h3m4s} */
    public static String formatShort(long millis) {
        long seconds = Math.max(0L, millis) / SECOND;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append('d');
        }
        if (hours > 0) {
            out.append(hours).append('h');
        }
        if (minutes > 0) {
            out.append(minutes).append('m');
        }
        if (seconds > 0 || out.isEmpty()) {
            out.append(seconds).append('s');
        }
        return out.toString();
    }
}
