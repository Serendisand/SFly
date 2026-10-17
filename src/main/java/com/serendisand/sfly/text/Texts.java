package com.serendisand.sfly.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Texts {
    /** 只认原版合法的格式码，避免玩家名里的 "&z" 之类被误吃 */
    private static final String COLOR_CHARS = "0123456789abcdefklmnorxABCDEFKLMNORX";

    private Texts() {
    }

    /**
     * & → § 。命令里让管理员手打 § 太反人类，配置和命令统一用 &。
     */
    public static String color(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length() && COLOR_CHARS.indexOf(raw.charAt(i + 1)) >= 0) {
                out.append('§').append(Character.toLowerCase(raw.charAt(i + 1)));
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * 占位符替换：{@code fill("剩余 {q}", "q", "1m")}。奇数个参数时最后一个被忽略。
     */
    public static String fill(String template, Object... pairs) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String result = template;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return result;
    }

    /** 先替换占位符再上色 —— 否则占位符里的 & 会被当成格式码。 */
    public static String of(String template, Object... pairs) {
        return color(fill(template, pairs));
    }

    public static MutableComponent comp(String alreadyColored) {
        return Component.literal(alreadyColored == null ? "" : alreadyColored);
    }
}
