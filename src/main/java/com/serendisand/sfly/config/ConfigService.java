package com.serendisand.sfly.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.serendisand.sfly.SFlyConstants;
import com.serendisand.sfly.text.MessageSpec;
import com.serendisand.sfly.text.Zones;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigService {
    private final Path file;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            // 不关掉 HTML 转义的话 & 会被写成 &，颜色码直接报废
            .disableHtmlEscaping()
            .registerTypeAdapter(MessageSpec.class, new MessageSpecAdapter())
            .create();

    private volatile SFlyConfig config = new SFlyConfig();

    public ConfigService(Path configDir) {
        this.file = configDir.resolve("config.json");
    }

    public SFlyConfig get() {
        return config;
    }

    /** 首次运行落一份带默认值的配置；之后只读，缺的键在内存里补上。 */
    public void load() {
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                SFlyConfig fresh = new SFlyConfig();
                fresh.applyDefaults();
                config = fresh;
                write(fresh);
                SFlyConstants.LOGGER.info("已生成默认配置：{}", file);
            } else {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    SFlyConfig loaded = gson.fromJson(reader, SFlyConfig.class);
                    if (loaded == null) {
                        loaded = new SFlyConfig();
                    }
                    loaded.applyDefaults();
                    config = loaded;
                }
            }
        } catch (Exception e) {
            // 配置写坏了也不该拦着服务器启动
            SFlyConstants.LOGGER.error("读取配置失败，已回退到默认值：{}", file, e);
            SFlyConfig fallback = new SFlyConfig();
            fallback.applyDefaults();
            config = fallback;
        }
        // 放在分支外面 —— 上面任何一条路走完都得把时区定下来，活动排期全靠它
        Zones.configure(config.timezone);
        SFlyConstants.LOGGER.info("活动排期时区：{}", Zones.describe());
    }

    public boolean save() {
        return write(config);
    }

    public Path file() {
        return file;
    }

    private boolean write(SFlyConfig target) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(target, writer);
            }
            return true;
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("写入配置失败：{}", file, e);
            return false;
        }
    }
}
