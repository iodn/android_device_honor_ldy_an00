// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import android.os.BadParcelableException;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;

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

    public void request(int[] limits) throws RemoteException {
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

    private static int objectEnd(Parcel reply, int minimumSize) {
        if (reply.readInt() != 1) throw new BadParcelableException("Missing UniPerf object");
        int start = reply.dataPosition();
        int size = reply.readInt();
        if (size < minimumSize || size > reply.dataSize() - start) {
            throw new BadParcelableException("Invalid UniPerf object size");
        }
        return start + size;
    }

    public int[] readLimits() throws RemoteException {
        IBinder remote = connect();
        Parcel data = Parcel.obtain(remote);
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeIntArray(PowerPolicy.TAGS);
            if (!remote.transact(2, data, reply, 0)) {
                throw new RemoteException("Missing UniPerf getter transaction");
            }
            reply.readException();
            int end = objectEnd(reply, 8);
            int status = reply.readInt();
            reply.setDataPosition(end);
            if (status != 0) throw new IllegalStateException("UniPerf backend status " + status);
            end = objectEnd(reply, 8 + PowerPolicy.TAGS.length * 4);
            if (reply.readInt() != PowerPolicy.TAGS.length) {
                throw new BadParcelableException("Invalid UniPerf limit count");
            }
            int[] result = new int[PowerPolicy.TAGS.length];
            for (int i = 0; i < result.length; i++) result[i] = reply.readInt();
            reply.setDataPosition(end);
            reply.enforceNoDataAvail();
            if (!PowerPolicy.validReadback(result)) {
                throw new IllegalStateException("Invalid UniPerf frequency/OPP readback");
            }
            return result;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
