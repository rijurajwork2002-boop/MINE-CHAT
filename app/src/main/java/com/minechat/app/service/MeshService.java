package com.minechat.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import com.minechat.app.R;
import com.minechat.app.ble.BleMeshManager;
import com.minechat.app.model.ChatMessage;
import com.minechat.app.sos.FallDetector;
import com.minechat.app.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class MeshService extends Service {
    private static final String TAG = "MeshService";
    private static final String CHANNEL_ID = "mine_chat_mesh";
    private static final int NOTIF_ID = 1;
    private static final int NOTIF_SOS_ID = 2;

    public static final String PREF_DEVICE_ID = "device_id";
    public static final String PREF_USER_NAME = "user_name";

    private BleMeshManager mesh;
    private FallDetector fallDetector;
    private SharedPreferences prefs;
    private String userName;
    private String deviceId;

    private final List<MessageListener> listeners = new ArrayList<>();
    private final List<ChatMessage> messageHistory = new ArrayList<>();

    public interface MessageListener {
        void onMessage(ChatMessage message);
        void onPeerUpdate();
    }

    public class MeshBinder extends Binder {
        public MeshService getService() { return MeshService.this; }
    }

    private final IBinder binder = new MeshBinder();

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("minechat", MODE_PRIVATE);
        userName = prefs.getString(PREF_USER_NAME, "Miner");
        deviceId = prefs.getString(PREF_DEVICE_ID, generateDeviceId());

        createNotificationChannel();
        startForeground(NOTIF_ID, buildForegroundNotification());

        mesh = new BleMeshManager(this, deviceId, meshListener);
        mesh.start();

        fallDetector = new FallDetector(this, this::onFallDetected);
        fallDetector.start();
        Log.i(TAG, "MeshService started, user=" + userName);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "UPDATE_NAME".equals(intent.getAction())) {
            userName = prefs.getString(PREF_USER_NAME, "Miner");
            mesh.sendAnnounce(userName);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mesh != null) mesh.stop();
        if (fallDetector != null) fallDetector.stop();
        super.onDestroy();
    }

    public void sendChatMessage(String text) {
        mesh.sendMessage(userName, text);
        ChatMessage msg = new ChatMessage(
                deviceId + "-" + System.currentTimeMillis(),
                userName, deviceId, text,
                System.currentTimeMillis(), ChatMessage.Type.TEXT, true);
        addMessage(msg);
    }

    public void sendSos() {
        String detail = "\u26a0 SOS from " + userName + " \u2013 Emergency! Immediate help needed!";
        mesh.sendSos(userName, detail);
        ChatMessage msg = new ChatMessage(
                deviceId + "-" + System.currentTimeMillis(),
                userName, deviceId, detail,
                System.currentTimeMillis(), ChatMessage.Type.SOS, true);
        addMessage(msg);
        vibrateSos();
        showSosNotification(detail);
    }

    public List<ChatMessage> getMessageHistory() {
        synchronized (messageHistory) { return new ArrayList<>(messageHistory); }
    }

    public int getPeerCount() { return mesh != null ? mesh.getPeerCount() : 0; }
    public String getUserName() { return userName; }

    public void updateUserName(String name) {
        userName = name;
        prefs.edit().putString(PREF_USER_NAME, name).apply();
        mesh.sendAnnounce(userName);
    }

    public void addListener(MessageListener l) { synchronized (listeners) { listeners.add(l); } }
    public void removeListener(MessageListener l) { synchronized (listeners) { listeners.remove(l); } }

    private void addMessage(ChatMessage msg) {
        synchronized (messageHistory) { messageHistory.add(msg); }
        synchronized (listeners) { for (MessageListener l : listeners) l.onMessage(msg); }
    }

    private final BleMeshManager.Listener meshListener = new BleMeshManager.Listener() {
        @Override public void onMessageReceived(ChatMessage message) {
            addMessage(message);
            if (message.isSos()) {
                vibrateSos();
                showSosNotification("SOS from " + message.getSenderName() + ": " + message.getText());
            }
        }
        @Override public void onPeerDiscovered(String address, String name) {
            Log.i(TAG, "Peer: " + name + " @ " + address);
            synchronized (listeners) { for (MessageListener l : listeners) l.onPeerUpdate(); }
        }
        @Override public void onPeerLost(String address) {
            synchronized (listeners) { for (MessageListener l : listeners) l.onPeerUpdate(); }
        }
    };

    private void onFallDetected() {
        Log.w(TAG, "Fall detected – auto SOS");
        sendSos();
    }

    private void vibrateSos() {
        long[] pattern = {0, 300, 200, 300, 200, 300, 500, 800, 500, 800, 500, 800};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                int[] amplitudes = {0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255};
                vm.getDefaultVibrator().vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1));
            }
        } else {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(pattern, -1);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "MINE-CHAT Mesh", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("BLE mesh networking for mine communications");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private Notification buildForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("MINE-CHAT Active")
                .setContentText("BLE mesh running – tap to open")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void showSosNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notif = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("\ud83d\udea8 SOS ALERT")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentIntent(pi)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_SOS_ID, notif);
    }

    private String generateDeviceId() {
        String id = java.util.UUID.randomUUID().toString();
        prefs.edit().putString(PREF_DEVICE_ID, id).apply();
        return id;
    }
}
