package com.minechat.app.sos;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Detects a sudden free-fall / high-impact event using the accelerometer.
 *
 * Strategy:
 *   1. Free-fall phase:  total acceleration < FREE_FALL_THRESHOLD for >= FREE_FALL_DURATION_MS
 *   2. Impact phase:     after free-fall, total acceleration > IMPACT_THRESHOLD
 *
 * This mimics the pattern of a phone being dropped in a mine collapse.
 * Alternatively it fires when the device is shaken violently (SHAKE_THRESHOLD).
 */
public class FallDetector implements SensorEventListener {
    private static final String TAG = "FallDetector";

    private static final float FREE_FALL_THRESHOLD = 2.0f;   // m/s² (near 0G)
    private static final long  FREE_FALL_DURATION_MS = 300L;  // must be in free-fall for 300 ms
    private static final float IMPACT_THRESHOLD = 25.0f;      // m/s² after fall
    private static final float SHAKE_THRESHOLD  = 30.0f;      // single large jolt
    private static final long  COOLDOWN_MS = 10_000L;         // 10 s between triggers

    private final SensorManager sensorManager;
    private final Listener listener;

    private long freeFallStartMs = 0;
    private boolean inFreeFall = false;
    private long lastTriggerMs = 0;

    public interface Listener {
        void onFallDetected();
    }

    public FallDetector(Context context, Listener listener) {
        this.listener = listener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void start() {
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
            Log.i(TAG, "Fall detector started");
        } else {
            Log.w(TAG, "No accelerometer – fall detection unavailable");
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float total = (float) Math.sqrt(x * x + y * y + z * z);
        long now = System.currentTimeMillis();

        // Shake / impact detection (single large event)
        if (total > SHAKE_THRESHOLD) {
            trigger(now);
            return;
        }

        // Free-fall + impact detection
        if (total < FREE_FALL_THRESHOLD) {
            if (!inFreeFall) {
                inFreeFall = true;
                freeFallStartMs = now;
            }
        } else {
            if (inFreeFall) {
                long duration = now - freeFallStartMs;
                if (duration >= FREE_FALL_DURATION_MS && total > IMPACT_THRESHOLD) {
                    trigger(now);
                }
                inFreeFall = false;
            }
        }
    }

    private void trigger(long now) {
        if (now - lastTriggerMs < COOLDOWN_MS) return;
        lastTriggerMs = now;
        Log.i(TAG, "Fall/impact detected – firing SOS");
        listener.onFallDetected();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
