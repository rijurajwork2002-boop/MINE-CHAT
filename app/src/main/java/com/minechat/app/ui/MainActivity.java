package com.minechat.app.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.minechat.app.R;
import com.minechat.app.model.ChatMessage;
import com.minechat.app.service.MeshService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_BT_ENABLE  = 1001;
    private static final int REQ_PERMS      = 1002;

    private MeshService meshService;
    private boolean bound = false;
    private ChatAdapter adapter;
    private ListView listView;
    private EditText inputField;
    private TextView peerCountView;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshService.MeshBinder b = (MeshService.MeshBinder) service;
            meshService = b.getService();
            bound = true;
            adapter.setMessages(meshService.getMessageHistory());
            scrollToBottom();
            updatePeerCount();
            meshService.addListener(messageListener);
        }
        @Override public void onServiceDisconnected(ComponentName name) { bound = false; }
    };

    private final MeshService.MessageListener messageListener = new MeshService.MessageListener() {
        @Override public void onMessage(ChatMessage message) {
            runOnUiThread(() -> { adapter.addMessage(message); scrollToBottom(); });
        }
        @Override public void onPeerUpdate() { runOnUiThread(MainActivity.this::updatePeerCount); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getActionBar().setTitle("MINE-CHAT");

        peerCountView = findViewById(R.id.text_peer_count);
        listView      = findViewById(R.id.list_chat);
        inputField    = findViewById(R.id.edit_message);
        ImageButton sendBtn = findViewById(R.id.btn_send);
        Button sosBtn       = findViewById(R.id.btn_sos);

        adapter = new ChatAdapter(this);
        listView.setAdapter(adapter);

        sendBtn.setOnClickListener(v -> sendMessage());
        sosBtn.setOnClickListener(v -> confirmSos());

        ensureBluetoothAndStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MeshService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            meshService.removeListener(messageListener);
            unbindService(connection);
            bound = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, R.id.menu_profile, 0, "My Profile");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_profile) {
            startActivity(new Intent(this, ProfileSetupActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void ensureBluetoothAndStart() {
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bm == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }
        BluetoothAdapter bt = bm.getAdapter();
        if (bt == null || !bt.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_BT_ENABLE);
        } else {
            checkAndRequestPermissions();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_BT_ENABLE) checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<String> needed = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (!needed.isEmpty()) {
                requestPermissions(needed.toArray(new String[0]), REQ_PERMS);
                return;
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERMS);
                return;
            }
        }
        startMeshService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allGranted = true;
        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
        if (allGranted) startMeshService();
        else Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show();
    }

    private void startMeshService() {
        Intent i = new Intent(this, MeshService.class);
        startForegroundService(i);
    }

    private void sendMessage() {
        if (!bound) return;
        String text = inputField.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        meshService.sendChatMessage(text);
        inputField.setText("");
    }

    private void confirmSos() {
        new AlertDialog.Builder(this)
                .setTitle("🚨 Send SOS Alert")
                .setMessage("Broadcast an emergency SOS signal to all miners on the mesh?")
                .setPositiveButton("SEND SOS", (d, w) -> { if (bound) meshService.sendSos(); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updatePeerCount() {
        int count = bound ? meshService.getPeerCount() : 0;
        peerCountView.setText(count == 0 ? "No peers" : count + (count == 1 ? " miner nearby" : " miners nearby"));
    }

    private void scrollToBottom() {
        listView.post(() -> listView.setSelection(adapter.getCount() - 1));
    }
}
