package com.minechat.app.model;

public class Peer {
    private final String deviceId;
    private String name;
    private long lastSeen;

    public Peer(String deviceId, String name) {
        this.deviceId = deviceId;
        this.name = name;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getDeviceId() { return deviceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}
