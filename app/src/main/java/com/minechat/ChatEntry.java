package com.minechat;

public class ChatEntry {
    public enum EntryType {
        CHAT,
        SOS
    }

    private final EntryType type;
    private final String sender;
    private final String body;
    private final String section;
    private final String timestampLabel;

    public ChatEntry(EntryType type, String sender, String body, String section, String timestampLabel) {
        this.type = type;
        this.sender = sender;
        this.body = body;
        this.section = section;
        this.timestampLabel = timestampLabel;
    }

    public EntryType getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public String getSection() {
        return section;
    }

    public String getTimestampLabel() {
        return timestampLabel;
    }
}
