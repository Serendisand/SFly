package com.serendisand.sfly.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.serendisand.sfly.SFlyConstants;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** data.json 的读写。写盘走单独线程，免得磁盘 IO 卡住主线程的 tick。 */
public class DataService {
    private final Path file;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SFly-DataWriter");
        thread.setDaemon(true);
        return thread;
    });

    private SFlyData data = new SFlyData();
    private long lastAutoSaveAt = System.currentTimeMillis();

    public DataService(Path dataDir) {
        this.file = dataDir.resolve("data.json");
    }

    public SFlyData data() {
        return data;
    }

    public void load() {
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                SFlyConstants.LOGGER.info("未找到数据文件，将新建：{}", file);
                return;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                SFlyData loaded = gson.fromJson(reader, SFlyData.class);
                if (loaded == null) {
                    loaded = new SFlyData();
                }
                if (loaded.players == null) {
                    loaded.players = new java.util.LinkedHashMap<>();
                }
                if (loaded.events == null) {
                    loaded.events = new java.util.ArrayList<>();
                }
                if (loaded.recurring == null) {
                    loaded.recurring = new java.util.ArrayList<>();
                }
                // 清掉读盘时反序列化出来的 null 值，省得后面到处判空
                loaded.players.values().removeIf(java.util.Objects::isNull);
                loaded.events.removeIf(java.util.Objects::isNull);
                loaded.recurring.removeIf(java.util.Objects::isNull);
                data = loaded;
                SFlyConstants.LOGGER.info("已载入 {} 条玩家额度、{} 个活动", data.players.size(), data.events.size());
            }
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("读取数据文件失败，改用空数据（原文件不会被覆盖，请手动检查）：{}", file, e);
            data = new SFlyData();
        }
    }

    /** 在主线程序列化拿快照，再丢给写线程 —— 避免写盘过程中数据被 tick 改掉。 */
    public void saveAsync() {
        String json;
        try {
            json = gson.toJson(data);
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("序列化数据失败，本次不存盘", e);
            return;
        }
        writer.execute(() -> writeToDisk(json));
    }

    public void tickAutoSave(int minutes) {
        if (minutes <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoSaveAt >= minutes * 60_000L) {
            lastAutoSaveAt = now;
            saveAsync();
        }
    }

    /** 关服时把队列排空再落盘，别丢最后几秒的数据。 */
    public void shutdown() {
        try {
            String json = gson.toJson(data);
            writer.execute(() -> writeToDisk(json));
            writer.shutdown();
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                SFlyConstants.LOGGER.warn("存盘线程超时，改为同步写入");
                writeToDisk(json);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SFlyConstants.LOGGER.warn("等待存盘被中断，数据可能不完整");
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("关服存盘失败", e);
        }
    }

    private void writeToDisk(String json) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                out.write(json);
            }
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("写入数据文件失败：{}", file, e);
        }
    }
}
