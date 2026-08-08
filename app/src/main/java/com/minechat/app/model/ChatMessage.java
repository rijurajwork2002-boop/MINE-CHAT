package com.minechat.app.model;

public class ChatMessage {
    public enum Type { TEXT, SOS }

    private final String id;
    private final String senderName;
    private final String senderId;
    private final String text;
    private final long timestamp;
    private final Type type;
    private final boolean isLocal;

    public ChatMessage(String id, String senderName, String senderId,
                       String text, long timestamp, Type type, boolean isLocal) {
        this.id = id;
        this.senderName = senderName;
        this.senderId = senderId;
        this.text = text;
        this.timestamp = timestamp;
        this.type = type;
        this.isLocal = isLocal;
    }

    public String getId() { return id; }
    public String getSenderName() { return senderName; }
    public String getSenderId() { return senderId; }
    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }
    public Type getType() { return type; }
    public boolean isLocal() { return isLocal; }
    public boolean isSos() { return type == Type.SOS; }
}
