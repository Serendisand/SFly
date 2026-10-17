package com.serendisand.sfly.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.data.ActiveWindow;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.data.FlyEvent;
import com.serendisand.sfly.data.RecurringEvent;
import com.serendisand.sfly.event.EventService;
import com.serendisand.sfly.flight.FlightService;
import com.serendisand.sfly.quota.QuotaService;
import com.serendisand.sfly.task.FlyTicker;
import com.serendisand.sfly.text.Durations;
import com.serendisand.sfly.text.Msg;
import com.serendisand.sfly.text.MessageService;
import com.serendisand.sfly.text.Schedules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 活动命令：一次性活动 {@code event} + 循环活动 {@code repeat}。 */
final class EventCommands extends CommandSupport {
    /** 活动列表最多显示多少条（按开始时间倒序，新的在前） */
    private static final int EVENT_LIST_LIMIT = 15;
    private static final String[] WEEKDAY_NAMES = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    EventCommands(ConfigService configService, DataService dataService, QuotaService quotaService,
                  EventService eventService, FlightService flightService, MessageService messages) {
        super(configService, dataService, quotaService, eventService, flightService, messages);
    }

    // ---- 一次性活动 ----

    LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("event").requires(ADMIN)
                .then(Commands.literal("create").then(buildCreateArgs()))
                .then(Commands.literal("extend")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(eventNameSuggestions())
                                .then(Commands.argument("duration", StringArgumentType.string())
                                        .executes(this::extendEvent))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(eventNameSuggestions())
                                .executes(this::stopEvent)))
                .then(Commands.literal("list").executes(this::listEvents))
                .then(Commands.literal("info").executes(this::listActiveWindows))
                .then(Commands.literal("purge").executes(this::purgeEvents));
    }

    /** 手动清掉已结束的活动，不用等保留期到点。 */
    private int purgeEvents(CommandContext<CommandSourceStack> ctx) {
        int removed = eventService.purgeFinished(System.currentTimeMillis());
        dataService.saveAsync();
        messages.send(ctx.getSource(), Msg.ADMIN_EVENT_PURGED, "count", removed);
        return removed;
    }

    /**
     * create 的子树。五种写法都落在同一批 handler 上：
     * <pre>
     *   create &lt;名称&gt; &lt;时长&gt;                          现在开始
     *   create &lt;名称&gt; &lt;时长&gt; after &lt;延迟&gt;               延迟后开始
     *   create &lt;名称&gt; &lt;时长&gt; at &lt;开始&gt;                  指定开始
     *   create &lt;名称&gt; &lt;时长&gt; at &lt;开始&gt; until &lt;结束&gt;      起止都指定（时长被忽略）
     *   create &lt;名称&gt; at &lt;开始&gt; until &lt;结束&gt;              省掉时长的绝对起止
     * </pre>
     * 末尾都可以再加 {@code free} 变限免。
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> buildCreateArgs() {
        RequiredArgumentBuilder<CommandSourceStack, String> atStart =
                Commands.argument("start", StringArgumentType.string())
                        .suggests(timeSuggestions())
                        .executes(ctx -> createEvent(ctx, false))
                        .then(Commands.literal("until")
                                .then(Commands.argument("end", StringArgumentType.string())
                                        .suggests(timeSuggestions())
                                        .executes(ctx -> createEvent(ctx, false))
                                        .then(Commands.literal("free")
                                                .executes(ctx -> createEvent(ctx, true)))));

        RequiredArgumentBuilder<CommandSourceStack, String> duration =
                Commands.argument("duration", StringArgumentType.string())
                        .executes(ctx -> createEvent(ctx, false));
        duration.then(Commands.literal("free").executes(ctx -> createEvent(ctx, true)));
        duration.then(Commands.literal("after")
                .then(Commands.argument("delay", StringArgumentType.string())
                        .suggests(durationSuggestions())
                        .executes(ctx -> createEvent(ctx, false))
                        .then(Commands.literal("free").executes(ctx -> createEvent(ctx, true)))));
        duration.then(Commands.literal("at").then(atStart));

        // 「at」字面量和 duration 是兄弟节点。brigadier 的 CommandNode#getRelevantNodes
        // 先拿「读到空格为止的整段 token」在 literals 里做全等匹配，命中就只走那条；
        // 没命中才退回 arguments。所以 "create X at 20:00" 命中的是字面量，
        // "create X 1d" 里 "1d" 不是字面量，走 duration，两者不会打架。
        LiteralArgumentBuilder<CommandSourceStack> absolute = Commands.literal("at")
                .then(Commands.argument("start", StringArgumentType.string())
                        .suggests(timeSuggestions())
                        .then(Commands.literal("until")
                                .then(Commands.argument("end", StringArgumentType.string())
                                        .suggests(timeSuggestions())
                                        .executes(ctx -> createEvent(ctx, false))
                                        .then(Commands.literal("free")
                                                .executes(ctx -> createEvent(ctx, true))))));

        RequiredArgumentBuilder<CommandSourceStack, String> name =
                Commands.argument("name", StringArgumentType.string());
        name.then(duration);
        name.then(absolute);
        return name;
    }

    private int createEvent(CommandContext<CommandSourceStack> ctx, boolean free) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        long now = System.currentTimeMillis();

        long startAt;
        String startLabel;
        String at = readArg(ctx, "start");
        if (at != null) {
            try {
                startAt = Schedules.resolve(at, now);
            } catch (IllegalArgumentException e) {
                messages.send(source, Msg.ADMIN_BAD_TIME, "error", e.getMessage());
                return 0;
            }
            startLabel = Schedules.describe(startAt);
        } else {
            long delay = parseDuration(source, readArgOrEmpty(ctx, "delay"));
            if (delay < 0L) {
                return 0;
            }
            startAt = now + delay;
            startLabel = delay <= 0L ? "立即" : Durations.format(delay) + "后";
        }

        long endAt;
        String end = readArg(ctx, "end");
        if (end != null) {
            // 写了 until 就以绝对结束时刻为准，<时长> 让位
            try {
                endAt = Schedules.resolve(end, startAt);
            } catch (IllegalArgumentException e) {
                messages.send(source, Msg.ADMIN_BAD_TIME, "error", e.getMessage());
                return 0;
            }
            if (endAt <= startAt) {
                messages.send(source, Msg.ADMIN_BAD_TIME, "error",
                        "结束时间（" + Schedules.describe(endAt) + "）必须晚于开始时间（"
                                + Schedules.describe(startAt) + "）");
                return 0;
            }
        } else {
            long duration = parseDuration(source, StringArgumentType.getString(ctx, "duration"));
            if (duration < 0L) {
                return 0;
            }
            endAt = startAt + duration;
        }

        FlyEvent event = eventService.createAt(name, startAt, endAt, free);
        dataService.saveAsync();
        messages.send(source, Msg.ADMIN_EVENT_CREATED, FlyTicker.merge(
                FlyTicker.eventPairs(event.toWindow(), now),
                new Object[]{"start", startLabel, "count", 1}));
        return 1;
    }

    private int extendEvent(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        long extra = parseDuration(source, StringArgumentType.getString(ctx, "duration"));
        if (extra < 0L) {
            return 0;
        }
        FlyEvent event = eventService.findEvent(name);
        if (event == null || !eventService.extend(event, extra)) {
            messages.send(source, Msg.ADMIN_EVENT_NOT_FOUND, "event", name);
            return 0;
        }
        dataService.saveAsync();
        messages.send(source, Msg.ADMIN_EVENT_EXTENDED, FlyTicker.merge(
                FlyTicker.eventPairs(event.toWindow(), System.currentTimeMillis()),
                new Object[]{"amount", Durations.format(extra), "count", 1}));
        return 1;
    }

    private int stopEvent(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        long now = System.currentTimeMillis();

        if ("all".equalsIgnoreCase(name)) {
            int stopped = eventService.stopAll(now);
            int removed = eventService.removeRecurring("all");
            dataService.saveAsync();
            messages.send(source, Msg.ADMIN_EVENT_STOP_ALL, "count", stopped + removed);
            return stopped + removed;
        }

        FlyEvent event = eventService.findEvent(name);
        if (event != null) {
            event.setEndAt(now);
            eventService.invalidate();
            dataService.saveAsync();
            messages.send(source, Msg.ADMIN_EVENT_STOPPED, "event", event.getName());
            return 1;
        }
        // 循环活动也允许直接在这里停掉
        if (eventService.removeRecurring(name) > 0) {
            dataService.saveAsync();
            messages.send(source, Msg.ADMIN_REPEAT_REMOVED, "count", 1);
            return 1;
        }
        messages.send(source, Msg.ADMIN_EVENT_NOT_FOUND, "event", name);
        return 0;
    }

    private int listEvents(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long now = System.currentTimeMillis();
        List<FlyEvent> events = new ArrayList<>();
        for (FlyEvent event : eventService.allEvents()) {
            if (event != null) {
                events.add(event);
            }
        }
        if (events.isEmpty()) {
            messages.send(source, Msg.ADMIN_EVENT_LIST_HEADER, "count", 0, "shown", 0);
            messages.send(source, Msg.EVENT_NONE);
            return 0;
        }
        // 新的排前面 —— 按插入顺序的话，活动一多刚建的那个反而被刷出屏幕
        events.sort((a, b) -> Long.compare(b.getStartAt(), a.getStartAt()));
        int shown = Math.min(EVENT_LIST_LIMIT, events.size());
        messages.send(source, Msg.ADMIN_EVENT_LIST_HEADER, "count", events.size(), "shown", shown);

        for (int i = 0; i < shown; i++) {
            FlyEvent event = events.get(i);
            String status = event.isActive(now) ? "&a进行中"
                    : event.isPending(now) ? "&e等待中（" + Durations.format(event.getStartAt() - now) + "后开始）"
                    : "&8已结束";
            messages.send(source, Msg.ADMIN_EVENT_LIST_LINE, FlyTicker.merge(
                    FlyTicker.eventPairs(event.toWindow(), now),
                    new Object[]{"index", i + 1, "status", status}));
        }
        return events.size();
    }

    private int listActiveWindows(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long now = System.currentTimeMillis();
        List<ActiveWindow> windows = eventService.activeWindows(now);
        if (windows.isEmpty()) {
            messages.send(source, Msg.EVENT_NONE);
            return 0;
        }
        for (ActiveWindow window : windows) {
            messages.send(source, Msg.EVENT_ACTIVE_LINE, FlyTicker.eventPairs(window, now));
        }
        return windows.size();
    }

    // ---- 循环活动 ----

    LiteralArgumentBuilder<CommandSourceStack> buildRepeat() {
        return Commands.literal("repeat").requires(ADMIN)
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.literal("daily")
                                        .then(Commands.argument("time", StringArgumentType.string())
                                                .suggests(timeSuggestions())
                                                .then(Commands.argument("duration", StringArgumentType.string())
                                                        .suggests(durationSuggestions())
                                                        .executes(ctx -> addRepeat(ctx, "DAILY", 0, false))
                                                        .then(Commands.literal("free")
                                                                .executes(ctx -> addRepeat(ctx, "DAILY", 0, true))))))
                                .then(Commands.literal("weekly")
                                        .then(Commands.argument("day", IntegerArgumentType.integer(1, 7))
                                                .then(Commands.argument("time", StringArgumentType.string())
                                                        .suggests(timeSuggestions())
                                                        .then(Commands.argument("duration", StringArgumentType.string())
                                                                .suggests(durationSuggestions())
                                                                .executes(ctx -> addRepeat(ctx, "WEEKLY",
                                                                        IntegerArgumentType.getInteger(ctx, "day"), false))
                                                                .then(Commands.literal("free")
                                                                        .executes(ctx -> addRepeat(ctx, "WEEKLY",
                                                                                IntegerArgumentType.getInteger(ctx, "day"), true)))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(eventNameSuggestions())
                                .executes(ctx -> removeRepeat(ctx, StringArgumentType.getString(ctx, "name"))))
                        .then(Commands.literal("all").executes(ctx -> removeRepeat(ctx, "all"))))
                .then(Commands.literal("list").executes(this::listRepeats));
    }

    private int addRepeat(CommandContext<CommandSourceStack> ctx, String mode, int dayOfWeek, boolean free) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        String time = StringArgumentType.getString(ctx, "time");
        long duration = parseDuration(source, StringArgumentType.getString(ctx, "duration"));
        if (duration < 0L) {
            return 0;
        }
        if (!isValidTime(time)) {
            messages.send(source, Msg.ADMIN_BAD_TIME, "error", time);
            return 0;
        }
        // 统一补零存起来，免得 RecurringEvent 里的 LocalTime.parse 认不出「8:00」
        String normalizedTime = normalizeTime(time);
        RecurringEvent recurring = eventService.addRecurring(name, mode, normalizedTime, dayOfWeek, duration, free);
        dataService.saveAsync();
        long now = System.currentTimeMillis();
        messages.send(source, Msg.ADMIN_REPEAT_ADDED, FlyTicker.merge(
                FlyTicker.eventPairs(recurring.toWindow(now), now),
                new Object[]{"schedule", describeSchedule(recurring), "count", 1}));
        return 1;
    }

    private int removeRepeat(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        int count = eventService.removeRecurring(name);
        if (count == 0) {
            messages.send(source, Msg.ADMIN_EVENT_NOT_FOUND, "event", name);
            return 0;
        }
        dataService.saveAsync();
        messages.send(source, Msg.ADMIN_REPEAT_REMOVED, "count", count);
        return count;
    }

    private int listRepeats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long now = System.currentTimeMillis();
        List<RecurringEvent> list = eventService.allRecurring();
        messages.send(source, Msg.ADMIN_REPEAT_LIST_HEADER);
        if (list.isEmpty()) {
            messages.send(source, Msg.ADMIN_REPEAT_LIST_EMPTY);
            return 0;
        }
        int index = 1;
        for (RecurringEvent recurring : list) {
            if (recurring == null) {
                continue;
            }
            String status = recurring.isActive(now)
                    ? "&a开放中（剩 " + Durations.format(recurring.remaining(now)) + "）"
                    : "&7" + Durations.format(recurring.untilNext(now)) + "后开放";
            messages.send(source, Msg.ADMIN_REPEAT_LIST_LINE, "index", index++, "event", recurring.getName(),
                    "schedule", describeSchedule(recurring), "status", status,
                    "event_duration", Durations.format(recurring.getDurationMillis()),
                    "event_id", recurring.getId(),
                    "free", recurring.isFree() ? " &d[限免]" : "");
        }
        return list.size();
    }

    // ---- 补全 ----

    private SuggestionProvider<CommandSourceStack> eventNameSuggestions() {
        return (context, builder) -> {
            builder.suggest("all");
            for (FlyEvent event : eventService.allEvents()) {
                if (event != null) {
                    builder.suggest(quoteIfNeeded(event.getName()));
                }
            }
            for (RecurringEvent recurring : eventService.allRecurring()) {
                if (recurring != null) {
                    builder.suggest(quoteIfNeeded(recurring.getName()));
                }
            }
            return builder.buildFuture();
        };
    }

    /** 时间里的冒号不在原版 string 的字符集里，所以补全直接把引号带出去。 */
    private static SuggestionProvider<CommandSourceStack> timeSuggestions() {
        return (context, builder) -> {
            long now = System.currentTimeMillis();
            builder.suggest("\"20:00\"");
            builder.suggest("\"21:00\"");
            builder.suggest("\"" + Schedules.describe(now) + "\"");
            builder.suggest("\"明天20:00\"");
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> durationSuggestions() {
        return (context, builder) -> {
            builder.suggest("30m");
            builder.suggest("1h");
            builder.suggest("2h");
            builder.suggest("1d");
            return builder.buildFuture();
        };
    }

    // ---- 小工具 ----

    private static String describeSchedule(RecurringEvent recurring) {
        if (recurring.isWeekly()) {
            int day = Math.max(1, Math.min(7, recurring.getDayOfWeek()));
            return "每" + WEEKDAY_NAMES[day] + " " + recurring.getTime();
        }
        return "每天 " + recurring.getTime();
    }

    private static boolean isValidTime(String raw) {
        try {
            LocalTime.parse(normalizeTime(raw));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 把「8:00」补成「08:00」。LocalTime.parse 只认两位小时。 */
    private static String normalizeTime(String raw) {
        String text = raw == null ? "" : raw.trim();
        return text.indexOf(':') == 1 ? "0" + text : text;
    }
}
