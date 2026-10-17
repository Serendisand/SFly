package com.serendisand.sfly.text;

/** 一条消息的投递方式。TITLE/SUBTITLE 共用同一次标题动画。 */
public enum MessageChannel {
    /** 聊天栏 */
    CHAT,
    /** 动作栏（物品栏上方） */
    ACTIONBAR,
    /** 屏幕中央大标题 */
    TITLE,
    /** 大标题下方的小标题 */
    SUBTITLE,
    /** 全服广播。带前缀，玩家离线时也能收进日志 */
    BROADCAST,
    /** 屏幕顶部的 BossBar。进度条按剩余额度算 */
    BOSSBAR,
    /** 静默：只写日志，不打扰玩家。方便临时关掉某条提示 */
    NONE
}
