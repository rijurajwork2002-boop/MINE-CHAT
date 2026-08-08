package com.minechat.app.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BleGattServer extends BluetoothGattServerCallback {
    private static final String TAG = "BleGattServer";

    public static final UUID SERVICE_UUID = BleGattClient.SERVICE_UUID;
    public static final UUID CHAR_UUID    = BleGattClient.CHAR_UUID;
    public static final UUID CCCD_UUID    = BleGattClient.CCCD_UUID;

    private final Context context;
    private final PacketReceiver receiver;
    private BluetoothGattServer server;
    private final Set<BluetoothDevice> connectedDevices = new HashSet<>();

    public interface PacketReceiver {
        void onPacketReceived(String deviceAddress, byte[] packet);
        void onDeviceConnected(BluetoothDevice device);
        void onDeviceDisconnected(BluetoothDevice device);
    }

    public BleGattServer(Context context, PacketReceiver receiver) {
        this.context = context;
        this.receiver = receiver;
    }

    public void start() {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        server = bm.openGattServer(context, this);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE |
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        BluetoothGattDescriptor cccd = new BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        characteristic.addDescriptor(cccd);
        service.addCharacteristic(characteristic);
        server.addService(service);
    }

    public void stop() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    /** Notify all connected clients of a packet. */
    public void notifyAll(byte[] data) {
        if (server == null) return;
        BluetoothGattService service = server.getService(SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHAR_UUID);
        if (characteristic == null) return;
        characteristic.setValue(data);
        synchronized (connectedDevices) {
            for (BluetoothDevice device : connectedDevices) {
                server.notifyCharacteristicChanged(device, characteristic, false);
            }
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG, "Server: device connected " + device.getAddress());
            synchronized (connectedDevices) { connectedDevices.add(device); }
            receiver.onDeviceConnected(device);
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.d(TAG, "Server: device disconnected " + device.getAddress());
            synchronized (connectedDevices) { connectedDevices.remove(device); }
            receiver.onDeviceDisconnected(device);
        }
    }

    @Override
    public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattCharacteristic characteristic,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
        if (responseNeeded) {
            server.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                    offset, value);
        }
        if (value != null && CHAR_UUID.equals(characteristic.getUuid())) {
            receiver.onPacketReceived(device.getAddress(), Arrays.copyOf(value, value.length));
        }
    }

    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                         BluetoothGattDescriptor descriptor,
                                         boolean preparedWrite, boolean responseNeeded,
                                         int offset, byte[] value) {
        if (responseNeeded) {
            server.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                    offset, value);
        }
    }
}
