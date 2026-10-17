package com.serendisand.sfly.text;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置里的一条消息。JSON 允许三种写法，全部等价：
 * <pre>
 *   "fly-on": "&amp;a起飞！"
 *   "fly-on": ["&amp;a起飞！", "&amp;7剩余 {quota}"]
 *   "fly-on": { "channel": "ACTIONBAR", "lines": ["&amp;a起飞！"] }
 * </pre>
 */
public class MessageSpec {
    private MessageChannel channel = MessageChannel.CHAT;
    private List<String> lines = new ArrayList<>();

    public MessageSpec() {
    }

    public MessageSpec(MessageChannel channel, String... lines) {
        this.channel = channel;
        this.lines = new ArrayList<>(List.of(lines));
    }

    public MessageSpec(MessageChannel channel, List<String> lines) {
        this.channel = channel;
        this.lines = new ArrayList<>(lines);
    }

    public MessageChannel getChannel() {
        return channel == null ? MessageChannel.CHAT : channel;
    }

    public void setChannel(MessageChannel channel) {
        this.channel = channel;
    }

    public List<String> getLines() {
        return lines == null ? List.of() : lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
    }

    public boolean isSilent() {
        return getChannel() == MessageChannel.NONE || getLines().isEmpty();
    }
}
