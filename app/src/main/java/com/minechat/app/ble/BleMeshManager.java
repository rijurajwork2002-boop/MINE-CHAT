package com.minechat.app.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import com.minechat.app.model.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Central BLE mesh manager.
 * Simultaneously advertises as a GATT server and scans + connects to peers.
 * Relays packets up to TTL hops (flood + dedup).
 */
public class BleMeshManager {
    private static final String TAG = "BleMeshManager";
    private static final int SEEN_CACHE_MAX = 500;

    private final Context context;
    private final String localDeviceId;
    private final byte[] localSenderId;
    private final Listener listener;

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothLeAdvertiser advertiser;
    private BleGattServer gattServer;

    // address -> client
    private final ConcurrentHashMap<String, BleGattClient> clients = new ConcurrentHashMap<>();
    // dedup cache: "senderHex-timestamp"
    private final LinkedHashMap<String, Long> seenPackets = new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > SEEN_CACHE_MAX;
        }
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean running = false;

    public interface Listener {
        void onMessageReceived(ChatMessage message);
        void onPeerDiscovered(String address, String name);
        void onPeerLost(String address);
    }

    public BleMeshManager(Context context, String deviceId, Listener listener) {
        this.context = context;
        this.localDeviceId = deviceId;
        this.localSenderId = MinePacket.makeSenderId(deviceId);
        this.listener = listener;
    }

    // ---- Lifecycle ----

    public void start() {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.w(TAG, "Bluetooth not available/enabled");
            return;
        }
        running = true;
        startGattServer();
        startAdvertising();
        startScanning();
        Log.i(TAG, "BleMeshManager started");
    }

    public void stop() {
        running = false;
        stopAdvertising();
        stopScanning();
        for (BleGattClient c : clients.values()) c.disconnect();
        clients.clear();
        if (gattServer != null) gattServer.stop();
    }

    // ---- GATT Server ----

    private void startGattServer() {
        gattServer = new BleGattServer(context, new BleGattServer.PacketReceiver() {
            @Override
            public void onPacketReceived(String deviceAddress, byte[] packet) {
                handleIncomingPacket(deviceAddress, packet);
            }
            @Override
            public void onDeviceConnected(BluetoothDevice device) {
                Log.d(TAG, "Server: connected " + device.getAddress());
            }
            @Override
            public void onDeviceDisconnected(BluetoothDevice device) {
                Log.d(TAG, "Server: disconnected " + device.getAddress());
            }
        });
        gattServer.start();
    }

    // ---- Advertising ----

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override public void onStartSuccess(AdvertiseSettings s) { Log.i(TAG, "Advertising started"); }
        @Override public void onStartFailure(int e) { Log.e(TAG, "Advertising failed: " + e); }
    };

    private void startAdvertising() {
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) { Log.w(TAG, "No advertiser"); return; }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(new ParcelUuid(BleGattClient.SERVICE_UUID))
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private void stopAdvertising() {
        if (advertiser != null) {
            try { advertiser.stopAdvertising(advertiseCallback); } catch (Exception ignored) {}
        }
    }

    // ---- Scanning ----

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String addr = device.getAddress();
            if (!clients.containsKey(addr)) {
                Log.d(TAG, "Discovered peer: " + addr);
                connectToDevice(device);
            }
        }
    };

    private void startScanning() {
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return;

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BleGattClient.SERVICE_UUID))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        Log.i(TAG, "BLE scan started");
    }

    private void stopScanning() {
        if (scanner != null) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        }
    }

    // ---- Peer connections ----

    private void connectToDevice(BluetoothDevice device) {
        BleGattClient client = new BleGattClient(context, device, new BleGattClient.PacketReceiver() {
            @Override public void onPacketReceived(String addr, byte[] packet) {
                handleIncomingPacket(addr, packet);
            }
            @Override public void onConnected(String addr) {
                Log.i(TAG, "Client connected to " + addr);
                listener.onPeerDiscovered(addr, addr);
                // Send an announce so they know our name
                sendAnnounce();
            }
            @Override public void onDisconnected(String addr) {
                clients.remove(addr);
                listener.onPeerLost(addr);
            }
        });
        clients.put(device.getAddress(), client);
        client.connect();
    }

    // ---- Packet handling ----

    private synchronized void handleIncomingPacket(String fromAddress, byte[] raw) {
        MinePacket pkt = MinePacket.fromBytes(raw);
        if (pkt == null) return;

        // Dedup check
        String key = pkt.senderHex() + "-" + pkt.timestamp;
        synchronized (seenPackets) {
            if (seenPackets.containsKey(key)) return;
            seenPackets.put(key, System.currentTimeMillis());
        }

        // Skip own packets
        if (Arrays.equals(pkt.senderId, localSenderId)) return;

        // Process packet
        switch (pkt.type) {
            case MinePacket.TYPE_CHAT:
            case MinePacket.TYPE_SOS:
                dispatchToListener(pkt);
                break;
            case MinePacket.TYPE_ANNOUNCE:
                handleAnnounce(fromAddress, pkt);
                break;
            default:
                break;
        }

        // Relay if TTL > 1
        if (pkt.ttl > 1) {
            relay(fromAddress, pkt.withDecrementedTtl().toBytes());
        }
    }

    private void dispatchToListener(MinePacket pkt) {
        if (pkt.payload == null) return;
        try {
            String json = new String(pkt.payload, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);
            String senderName = obj.optString("name", pkt.senderHex());
            String text = obj.optString("text", "");
            ChatMessage.Type msgType = (pkt.type == MinePacket.TYPE_SOS)
                    ? ChatMessage.Type.SOS : ChatMessage.Type.TEXT;
            ChatMessage msg = new ChatMessage(
                    pkt.senderHex() + "-" + pkt.timestamp,
                    senderName, pkt.senderHex(), text,
                    pkt.timestamp, msgType, false);
            listener.onMessageReceived(msg);
        } catch (JSONException e) {
            Log.e(TAG, "Bad JSON payload", e);
        }
    }

    private void handleAnnounce(String fromAddress, MinePacket pkt) {
        if (pkt.payload == null) return;
        try {
            String json = new String(pkt.payload, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);
            String name = obj.optString("name", pkt.senderHex());
            listener.onPeerDiscovered(fromAddress, name);
        } catch (JSONException ignored) {}
    }

    /** Relay a raw packet to all connected clients except the one who sent it. */
    private void relay(String excludeAddress, byte[] raw) {
        // Notify via server to peripheral-role clients
        if (gattServer != null) gattServer.notifyAll(raw);
        // Also send via central-role connections
        for (Map.Entry<String, BleGattClient> entry : clients.entrySet()) {
            if (!entry.getKey().equals(excludeAddress)) {
                entry.getValue().sendPacket(raw);
            }
        }
    }

    // ---- Public send API ----

    private MinePacket buildPacket(byte type, String senderName, String text) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", senderName);
            obj.put("text", text);
            byte[] payload = obj.toString().getBytes(StandardCharsets.UTF_8);
            return new MinePacket(type, (byte) MinePacket.DEFAULT_TTL,
                    System.currentTimeMillis(), localSenderId, payload);
        } catch (JSONException e) {
            return null;
        }
    }

    public void sendMessage(String senderName, String text) {
        MinePacket pkt = buildPacket(MinePacket.TYPE_CHAT, senderName, text);
        if (pkt == null) return;
        byte[] raw = pkt.toBytes();
        markSeen(pkt);
        broadcast(raw);
    }

    public void sendSos(String senderName, String details) {
        MinePacket pkt = buildPacket(MinePacket.TYPE_SOS, senderName, details);
        if (pkt == null) return;
        byte[] raw = pkt.toBytes();
        markSeen(pkt);
        broadcast(raw);
    }

    private void sendAnnounce() {
        // Caller already holds a valid name via MeshService; we skip storing it here
        // and send an empty announce – MeshService will call sendAnnounce(name)
    }

    public void sendAnnounce(String senderName) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", senderName);
            byte[] payload = obj.toString().getBytes(StandardCharsets.UTF_8);
            MinePacket pkt = new MinePacket(MinePacket.TYPE_ANNOUNCE,
                    (byte) MinePacket.DEFAULT_TTL,
                    System.currentTimeMillis(), localSenderId, payload);
            markSeen(pkt);
            broadcast(pkt.toBytes());
        } catch (JSONException ignored) {}
    }

    private void markSeen(MinePacket pkt) {
        String key = pkt.senderHex() + "-" + pkt.timestamp;
        synchronized (seenPackets) { seenPackets.put(key, System.currentTimeMillis()); }
    }

    private void broadcast(byte[] raw) {
        if (gattServer != null) gattServer.notifyAll(raw);
        for (BleGattClient c : clients.values()) c.sendPacket(raw);
    }

    public int getPeerCount() { return clients.size(); }
}
