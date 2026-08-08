package com.minechat.app.ble;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Simple wire-format for MINE-CHAT packets.
 *
 * Header (10 bytes):
 *   [0]       version  = 0x01
 *   [1]       type     = 0x01 (chat) | 0x02 (SOS) | 0x03 (announce) | 0x04 (ack)
 *   [2]       ttl      (decremented at each hop, drop at 0)
 *   [3..6]    timestamp (4 bytes, unix seconds, big-endian)
 *   [7..14]   sender-id (8 bytes, first 8 of SHA-256 of device-id string)
 *
 * Payload (variable):
 *   2-byte length prefix (big-endian), then UTF-8 json or raw bytes
 */
public class MinePacket {
    public static final byte VERSION = 0x01;
    public static final byte TYPE_CHAT     = 0x01;
    public static final byte TYPE_SOS      = 0x02;
    public static final byte TYPE_ANNOUNCE = 0x03;
    public static final byte TYPE_ACK      = 0x04;

    public static final int  DEFAULT_TTL   = 7;
    private static final int HEADER_LEN    = 15; // version(1)+type(1)+ttl(1)+timestamp(4)+senderId(8)

    public final byte   type;
    public final byte   ttl;
    public final long   timestamp;
    public final byte[] senderId;   // 8 bytes
    public final byte[] payload;

    public MinePacket(byte type, byte ttl, long timestamp, byte[] senderId, byte[] payload) {
        this.type = type;
        this.ttl = ttl;
        this.timestamp = timestamp;
        this.senderId = senderId;
        this.payload = payload;
    }

    // ---- Serialise ----

    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(VERSION);
        out.write(type);
        out.write(ttl);
        // 4-byte timestamp (unix seconds)
        int ts = (int) (timestamp / 1000L);
        out.write((ts >> 24) & 0xFF);
        out.write((ts >> 16) & 0xFF);
        out.write((ts >>  8) & 0xFF);
        out.write( ts        & 0xFF);
        // 8-byte sender id
        out.write(senderId, 0, 8);
        // 2-byte payload length + payload
        int pLen = (payload == null) ? 0 : payload.length;
        out.write((pLen >> 8) & 0xFF);
        out.write( pLen       & 0xFF);
        if (pLen > 0) {
            try { out.write(payload); } catch (IOException ignored) {}
        }
        return out.toByteArray();
    }

    // ---- Deserialise ----

    public static MinePacket fromBytes(byte[] raw) {
        if (raw == null || raw.length < HEADER_LEN + 2) return null;
        if (raw[0] != VERSION) return null;

        byte type = raw[1];
        byte ttl  = raw[2];

        int ts = ((raw[3] & 0xFF) << 24) | ((raw[4] & 0xFF) << 16) |
                 ((raw[5] & 0xFF) <<  8) |  (raw[6] & 0xFF);
        long timestamp = (long) ts * 1000L;

        byte[] senderId = new byte[8];
        System.arraycopy(raw, 7, senderId, 0, 8);

        int pLen = ((raw[15] & 0xFF) << 8) | (raw[16] & 0xFF);
        byte[] payload = null;
        if (pLen > 0 && raw.length >= HEADER_LEN + 2 + pLen) {
            payload = new byte[pLen];
            System.arraycopy(raw, HEADER_LEN + 2, payload, 0, pLen);
        }
        return new MinePacket(type, ttl, timestamp, senderId, payload);
    }

    // ---- Helpers ----

    public static byte[] makeSenderId(String deviceId) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(deviceId.getBytes(StandardCharsets.UTF_8));
            byte[] id = new byte[8];
            System.arraycopy(hash, 0, id, 0, 8);
            return id;
        } catch (Exception e) {
            return new byte[8];
        }
    }

    public MinePacket withDecrementedTtl() {
        return new MinePacket(type, (byte)(ttl - 1), timestamp, senderId, payload);
    }

    public String senderHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : senderId) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
