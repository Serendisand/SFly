package com.serendisand.sfly.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.serendisand.sfly.text.MessageChannel;
import com.serendisand.sfly.text.MessageSpec;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 让一条消息既能写成字符串、字符串数组，也能写成带 channel 的对象。
 * 写配置的人不该为了改一行文案去数括号。
 */
public class MessageSpecAdapter implements JsonSerializer<MessageSpec>, JsonDeserializer<MessageSpec> {
    @Override
    public MessageSpec deserialize(JsonElement json, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return new MessageSpec(MessageChannel.NONE);
        }
        if (json.isJsonPrimitive()) {
            return new MessageSpec(MessageChannel.CHAT, json.getAsString());
        }
        if (json.isJsonArray()) {
            return new MessageSpec(MessageChannel.CHAT, readArray(json.getAsJsonArray()));
        }

        JsonObject object = json.getAsJsonObject();
        MessageSpec spec = new MessageSpec();
        spec.setChannel(readChannel(object.get("channel")));

        List<String> lines = new ArrayList<>();
        JsonElement linesElement = object.get("lines");
        if (linesElement != null && linesElement.isJsonArray()) {
            lines.addAll(List.of(readArray(linesElement.getAsJsonArray())));
        }
        if (lines.isEmpty() && object.has("text")) {
            lines.add(object.get("text").getAsString());
        }
        spec.setLines(lines);
        return spec;
    }

    @Override
    public JsonElement serialize(MessageSpec spec, Type type, JsonSerializationContext context) {
        JsonObject object = new JsonObject();
        object.addProperty("channel", spec.getChannel().name());
        JsonArray lines = new JsonArray();
        spec.getLines().forEach(lines::add);
        object.add("lines", lines);
        return object;
    }

    private static MessageChannel readChannel(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return MessageChannel.CHAT;
        }
        try {
            return MessageChannel.valueOf(element.getAsString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 写错了不该让整份配置读不出来，退回聊天栏并在文件里保留原值
            return MessageChannel.CHAT;
        }
    }

    private static String[] readArray(JsonArray array) {
        List<String> lines = new ArrayList<>(array.size());
        array.forEach(element -> lines.add(element.getAsString()));
        return lines.toArray(new String[0]);
    }
}
