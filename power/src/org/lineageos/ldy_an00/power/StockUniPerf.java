// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;

import java.io.FileInputStream;
import java.io.IOException;

/** Wire contract from the retained V1 NDK interface; no root or policy changes. */
final class StockUniPerf implements PowerController.Backend {
    private static final String DESCRIPTOR =
            "vendor.honor.hardware.iawareperf.IUniPerfAidlInterface";
    private static final String HASH = "eb83d0791196b3bf9beb84b53a91fb3eda2e00be";
    private static final int POWERGENIE_CLIENT = 1; // Thermal control owns client 2.
    private volatile IBinder mBinder;
    private final Runnable mOnDeath;

    StockUniPerf(Runnable onDeath) {
        mOnDeath = onDeath;
    }

    private IBinder connect() throws RemoteException {
        IBinder remote = mBinder;
        if (remote != null) return remote;
        remote = ServiceManager.checkService(DESCRIPTOR + "/default");
        if (remote == null) throw new RemoteException("Stock UniPerf is unavailable");
        Parcel data = Parcel.obtain(remote);
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!remote.transact(16777215, data, reply, 0)) {
                throw new RemoteException("Missing UniPerf version transaction");
            }
            reply.readException();
            if (reply.readInt() != 1) throw new IllegalStateException("Unsupported UniPerf version");
        } finally {
            reply.recycle();
            data.recycle();
        }
        data = Parcel.obtain(remote);
        reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!remote.transact(16777214, data, reply, 0)) {
                throw new RemoteException("Missing UniPerf hash transaction");
            }
            reply.readException();
            if (!HASH.equals(reply.readString())) {
                throw new IllegalStateException("Unsupported UniPerf interface hash");
            }
        } finally {
            reply.recycle();
            data.recycle();
        }
        final IBinder connected = remote;
        remote.linkToDeath(() -> {
            if (mBinder == connected) {
                mBinder = null;
                mOnDeath.run();
            }
        }, 0);
        mBinder = remote;
        if (!remote.isBinderAlive()) {
            mBinder = null;
            throw new RemoteException("UniPerf died during connection");
        }
        return remote;
    }

    public void request(int[] limits) throws RemoteException, IOException {
        if (!PowerPolicy.validRequest(limits)) {
            throw new IllegalArgumentException("Invalid UniPerf frequency request");
        }
        if (limits[3] >= 0 && limits[3] >= gpuLevelCount()) {
            throw new IllegalArgumentException("GPU index outside driver range");
        }
        IBinder remote = connect();
        Parcel data = Parcel.obtain(remote);
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(POWERGENIE_CLIENT);
            data.writeIntArray(PowerPolicy.TAGS);
            data.writeIntArray(limits);
            // The stock setter is one-way. Delivery does not prove hardware application.
            if (!remote.transact(3, data, null, IBinder.FLAG_ONEWAY)) {
                throw new RemoteException("Missing UniPerf setter transaction");
            }
        } finally {
            data.recycle();
        }
    }

    public int gpuLevelCount() throws IOException {
        // Same GED source used by stock libpowerhal get_gpu_freq_level_count.
        try (FileInputStream input = new FileInputStream(
                "/sys/kernel/ged/hal/total_gpu_freq_level_count")) {
            return PowerPolicy.parseCpuCeiling(input.readNBytes(33));
        }
    }

    public int[] readLimits() throws RemoteException, IOException {
        connect();
        int[] policies = {0, 4, 7};
        int[] result = new int[policies.length];
        for (int i = 0; i < policies.length; i++) {
            String path = "/sys/devices/system/cpu/cpufreq/policy" + policies[i]
                    + "/scaling_max_freq";
            try (FileInputStream input = new FileInputStream(path)) {
                result[i] = PowerPolicy.parseCpuCeiling(input.readNBytes(33));
            }
        }
        if (!PowerPolicy.validReadback(result)) {
            throw new IllegalStateException("Invalid CPU ceiling readback");
        }
        return result;
    }
}
