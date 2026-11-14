// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.util.ArraySet;
import android.util.Slog;

import com.android.server.SystemService;

/** Connect Android Battery Saver and screen state to the retained Honor performance HAL. */
public final class DevicePowerService extends SystemService {
    private static final String TAG = "LdyAn00Power";
    private final HandlerThread mThread = new HandlerThread(TAG);
    private final ArraySet<String> mOpenCameras = new ArraySet<>();
    private Handler mHandler;
    private PowerManager mPower;
    private AudioManager mAudio;
    private StockUniPerf mUniPerf;
    private PowerController mController;
    private boolean mCameraObserverReady;

    public DevicePowerService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mUniPerf = new StockUniPerf(() -> mHandler.post(() -> {
            mController.invalidate();
            if (mPower == null) mController.release();
            else update();
        }));
        mController = new PowerController(mUniPerf, mHandler::postDelayed, (message, error) -> {
            if (error == null) Slog.i(TAG, message);
            else Slog.w(TAG, message, error);
        });
        mHandler.post(() -> mController.release());
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase != PHASE_BOOT_COMPLETED) return;
        mHandler.post(() -> {
            mPower = getContext().getSystemService(PowerManager.class);
            mAudio = getContext().getSystemService(AudioManager.class);
            CameraManager camera = getContext().getSystemService(CameraManager.class);
            if (mPower == null) {
                Slog.e(TAG, "Power state unavailable; device frequency policy disabled");
                return;
            }
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            getContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    update();
                }
            }, filter, null, mHandler, Context.RECEIVER_NOT_EXPORTED);
            if (mAudio == null || camera == null) {
                Slog.e(TAG, "Missing workload observer; device frequency policy disabled");
                update(); // Screen-state admission remains independent of workload observers.
                return;
            }
            try {
                mAudio.addOnModeChangedListener(mHandler::post, mode -> update());
                if (getContext().checkSelfPermission(
                        android.Manifest.permission.CAMERA_OPEN_CLOSE_LISTENER)
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Missing camera workload permission");
                }
                camera.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
                    @Override
                    public void onCameraOpened(String cameraId, String packageId) {
                        mOpenCameras.add(cameraId);
                        update();
                    }

                    @Override
                    public void onCameraClosed(String cameraId) {
                        mOpenCameras.remove(cameraId);
                        update();
                    }
                }, mHandler);
                // Drain the initial camera-open callbacks before enabling limits.
                mHandler.post(() -> {
                    mCameraObserverReady = true;
                    update();
                });
            } catch (RuntimeException e) {
                Slog.e(TAG, "Camera observer unavailable; releasing frequency policy", e);
            }
            update();
        });
    }

    private void update() {
        if (mPower == null) return;
        final boolean interactive;
        try {
            interactive = mPower.isInteractive();
        } catch (RuntimeException e) {
            Slog.e(TAG, "Screen state unavailable; releasing frequency policy", e);
            mController.release();
            return;
        }
        boolean saver = false;
        boolean protectedWorkload = true;
        try {
            saver = mPower.isPowerSaveMode();
            protectedWorkload = mAudio == null || !mCameraObserverReady || !mOpenCameras.isEmpty()
                    || mAudio.getMode() != AudioManager.MODE_NORMAL;
        } catch (RuntimeException e) {
            Slog.e(TAG, "Workload state unavailable; releasing frequency policy", e);
        }
        mController.update(saver, interactive, protectedWorkload);
    }
}
