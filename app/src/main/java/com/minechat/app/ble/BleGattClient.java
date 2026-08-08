package com.minechat.app.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.UUID;

public class BleGattClient extends BluetoothGattCallback {
    private static final String TAG = "BleGattClient";

    // Must match BleGattServer UUIDs
    public static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID CHAR_UUID    = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID CCCD_UUID    = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final BluetoothDevice device;
    private final PacketReceiver receiver;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;

    public interface PacketReceiver {
        void onPacketReceived(String deviceAddress, byte[] packet);
        void onConnected(String deviceAddress);
        void onDisconnected(String deviceAddress);
    }

    public BleGattClient(Context context, BluetoothDevice device, PacketReceiver receiver) {
        this.context = context;
        this.device = device;
        this.receiver = receiver;
    }

    public void connect() {
        gatt = device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE);
    }

    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
    }

    public boolean sendPacket(byte[] data) {
        if (gatt == null || characteristic == null) return false;
        characteristic.setValue(data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return gatt.writeCharacteristic(characteristic, data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS;
        } else {
            return gatt.writeCharacteristic(characteristic);
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG, "Connected to " + device.getAddress());
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.d(TAG, "Disconnected from " + device.getAddress());
            receiver.onDisconnected(device.getAddress());
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) return;
        BluetoothGattService service = gatt.getService(SERVICE_UUID);
        if (service == null) return;
        characteristic = service.getCharacteristic(CHAR_UUID);
        if (characteristic == null) return;

        // Enable notifications
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }
        receiver.onConnected(device.getAddress());
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        if (data != null) {
            receiver.onPacketReceived(device.getAddress(), data);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic,
                                        byte[] value) {
        receiver.onPacketReceived(device.getAddress(), value);
    }
}
