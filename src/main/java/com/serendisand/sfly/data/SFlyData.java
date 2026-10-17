package com.serendisand.sfly.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** data.json 的根结构。 */
public class SFlyData {
    /** key = 玩家 UUID 字符串 */
    public Map<String, PlayerQuota> players = new LinkedHashMap<>();
    /** 一次性活动 */
    public List<FlyEvent> events = new ArrayList<>();
    /** 每天/每周定时开放的循环活动 */
    public List<RecurringEvent> recurring = new ArrayList<>();
}
