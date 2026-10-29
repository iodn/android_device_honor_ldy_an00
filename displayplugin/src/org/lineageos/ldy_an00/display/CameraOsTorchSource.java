package org.lineageos.ldy_an00.display;

import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;

/** Process-owned, worker-confined projection of the stock cameraOS torch callback. */
final class CameraOsTorchSource {
    private static final String SERVICE = "vendor.honor.hardware.cameraos.ICameraOSService";
    private static final String CALLBACK = "vendor.honor.hardware.cameraos.ICameraOSCallback";
    private static final String HASH = "c20e6f32c782f06bc9784063949bd7303f19bded";
    private final Handler handler;
    private final Runnable changed;
    private IBinder registered;
    private Callback callback;
    private int attempts;
    private boolean connected;
    // Exact source initialization; becomes true only for callback data[0] == 2.
    private boolean torchOn;
    private final Runnable retry = this::connect;

    CameraOsTorchSource(Handler worker, Runnable onChanged) {
        handler = worker;
        changed = onChanged;
    }

    boolean connected() { return connected; }
    boolean torchOn() { return torchOn; }

    void ensureConnection() {
        if (connected) return;
        handler.removeCallbacks(retry);
        attempts = 0;
        connect();
    }

    private void connect() {
        if (connected || attempts >= 6) return;
        attempts++;
        IBinder remote = ServiceManager.checkService(SERVICE + "/default");
        if (remote != null) {
            Callback candidate = new Callback();
            IBinder.DeathRecipient death = () -> handler.post(() -> {
                if (registered != remote) return;
                registered = null;
                callback = null;
                disconnected();
                ensureConnection();
            });
            boolean linked = false;
            try {
                if (!SERVICE.equals(remote.getInterfaceDescriptor())
                        || !HASH.equals(interfaceHash(remote))) {
                    disconnected();
                    return;
                }
                remote.linkToDeath(death, 0);
                linked = true;
                Parcel in = Parcel.obtain(remote);
                Parcel out = Parcel.obtain();
                try {
                    in.writeInterfaceToken(SERVICE);
                    in.writeInt(2);
                    in.writeStrongBinder(candidate);
                    if (!remote.transact(1, in, out, 0)) {
                        throw new RemoteException("cameraOS registration unimplemented");
                    }
                    out.readException();
                } finally {
                    out.recycle();
                    in.recycle();
                }
                registered = remote;
                callback = candidate;
                connected = true;
                changed.run();
                return;
            } catch (RemoteException | RuntimeException exception) {
                if (linked) remote.unlinkToDeath(death, 0);
                disconnected();
            }
        }
        if (attempts < 6) handler.postDelayed(retry, 2000);
    }

    private static String interfaceHash(IBinder remote) throws RemoteException {
        Parcel in = Parcel.obtain(remote);
        Parcel out = Parcel.obtain();
        try {
            in.writeInterfaceToken(SERVICE);
            if (!remote.transact(16777214, in, out, 0)) {
                throw new RemoteException("cameraOS hash unavailable");
            }
            out.readException();
            return out.readString();
        } finally {
            out.recycle();
            in.recycle();
        }
    }

    private void disconnected() {
        if (connected) {
            connected = false;
            changed.run();
        }
    }

    /** Null means ignored type/missing payload. Does not allocate an arbitrary incoming array. */
    static Integer readTorchEvent(Parcel in) {
        if (in.readInt() == 0) return null; // readTypedObject presence marker
        int start = in.dataPosition();
        int size = in.readInt();
        if (size < 4 || start > Integer.MAX_VALUE - size
                || start + size > in.dataSize()) {
            throw new BadParcelableException("Invalid cameraOS event size");
        }
        int end = start + size;
        try {
            if (in.dataPosition() + 4 > end) return null;
            int type = in.readInt();
            if (type == 1) return null; // Stock ITorchCallback explicitly ignores type 1.
            if (in.dataPosition() + 8 > end) return null;
            in.readInt(); // Source field 'cout'; callback does not use it.
            int count = in.readInt();
            if (count < 1 || count > (end - in.dataPosition()) / 4) return null;
            return in.readInt();
        } finally {
            in.setDataPosition(end);
        }
    }

    private final class Callback extends Binder implements IInterface {
        Callback() {
            markVintfStability();
            attachInterface(this, CALLBACK);
        }

        @Override public IBinder asBinder() { return this; }

        @Override protected boolean onTransact(int code, Parcel in, Parcel out, int flags)
                throws RemoteException {
            if (code >= 1 && code <= 16777215) in.enforceInterface(CALLBACK);
            if (code == INTERFACE_TRANSACTION) {
                out.writeString(CALLBACK);
                return true;
            }
            if (code == 16777215 || code == 16777214) {
                out.writeNoException();
                // Exact values returned by the stock ITorchCallback subclass, not AIDL defaults.
                if (code == 16777215) out.writeInt(0); else out.writeString("");
                return true;
            }
            if (code == 1) {
                Integer value = readTorchEvent(in);
                in.enforceNoDataAvail();
                if (value != null) handler.post(() -> {
                    if (callback != this) return;
                    torchOn = value == 2;
                    changed.run();
                });
                out.writeNoException();
                return true;
            }
            return super.onTransact(code, in, out, flags);
        }
    }
}
