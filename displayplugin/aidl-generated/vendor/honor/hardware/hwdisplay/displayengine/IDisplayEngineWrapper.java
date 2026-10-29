/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: cmd not shown due to `--omit_invocation`
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package vendor.honor.hardware.hwdisplay.displayengine;
// Declaration order is the verified transaction order 1..6. Do not reorder.
public interface IDisplayEngineWrapper extends android.os.IInterface
{
  /**
   * The version of this interface that the caller is built against.
   * This might be different from what {@link #getInterfaceVersion()
   * getInterfaceVersion} returns as that is the version of the interface
   * that the remote object is implementing.
   */
  public static final int VERSION = 1;
  public static final String HASH = "1b12fa2f43d0511e7bd32246d36bddfe533ae419";
  /** Default implementation for IDisplayEngineWrapper. */
  public static class Default implements vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper
  {
    @Override public vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData getHardwareEffect(int feature, int mode, int length) throws android.os.RemoteException
    {
      return null;
    }
    @Override public int getSupported(int feature) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData getVsyncEvent(int mode) throws android.os.RemoteException
    {
      return null;
    }
    @Override public void registerCallback(vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback callback) throws android.os.RemoteException
    {
    }
    @Override public int setHardwareEffect(int feature, int mode, byte[] data) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public int setNativeHidlBuff(int feature, vendor.honor.hardware.hwdisplay.displayengine.HwPicDisplayEngineConfig config, byte[] data) throws android.os.RemoteException
    {
      return 0;
    }
    @Override
    public int getInterfaceVersion() {
      return 0;
    }
    @Override
    public String getInterfaceHash() {
      return "";
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.markVintfStability();
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper interface,
     * generating a proxy if needed.
     */
    public static vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper))) {
        return ((vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper)iin);
      }
      return new vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      else if (code == TRANSACTION_getInterfaceVersion) {
        reply.writeNoException();
        reply.writeInt(getInterfaceVersion());
        return true;
      }
      else if (code == TRANSACTION_getInterfaceHash) {
        reply.writeNoException();
        reply.writeString(getInterfaceHash());
        return true;
      }
      switch (code)
      {
        case TRANSACTION_getHardwareEffect:
        {
          int _arg0;
          _arg0 = data.readInt();
          int _arg1;
          _arg1 = data.readInt();
          int _arg2;
          _arg2 = data.readInt();
          vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData _result = this.getHardwareEffect(_arg0, _arg1, _arg2);
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_getSupported:
        {
          int _arg0;
          _arg0 = data.readInt();
          int _result = this.getSupported(_arg0);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_getVsyncEvent:
        {
          int _arg0;
          _arg0 = data.readInt();
          vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData _result = this.getVsyncEvent(_arg0);
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_registerCallback:
        {
          vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback _arg0;
          _arg0 = vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback.Stub.asInterface(data.readStrongBinder());
          this.registerCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setHardwareEffect:
        {
          int _arg0;
          _arg0 = data.readInt();
          int _arg1;
          _arg1 = data.readInt();
          byte[] _arg2;
          _arg2 = data.createByteArray();
          int _result = this.setHardwareEffect(_arg0, _arg1, _arg2);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_setNativeHidlBuff:
        {
          int _arg0;
          _arg0 = data.readInt();
          vendor.honor.hardware.hwdisplay.displayengine.HwPicDisplayEngineConfig _arg1;
          _arg1 = _Parcel.readTypedObject(data, vendor.honor.hardware.hwdisplay.displayengine.HwPicDisplayEngineConfig.CREATOR);
          byte[] _arg2;
          _arg2 = data.createByteArray();
          int _result = this.setNativeHidlBuff(_arg0, _arg1, _arg2);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      private int mCachedVersion = -1;
      private String mCachedHash = "-1";
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData getHardwareEffect(int feature, int mode, int length) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(feature);
          _data.writeInt(mode);
          _data.writeInt(length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getHardwareEffect, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method getHardwareEffect is unimplemented.");
          }
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int getSupported(int feature) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(feature);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getSupported, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method getSupported is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData getVsyncEvent(int mode) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(mode);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getVsyncEvent, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method getVsyncEvent is unimplemented.");
          }
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void registerCallback(vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerCallback, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method registerCallback is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public int setHardwareEffect(int feature, int mode, byte[] data) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(feature);
          _data.writeInt(mode);
          _data.writeByteArray(data);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setHardwareEffect, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method setHardwareEffect is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int setNativeHidlBuff(int feature, vendor.honor.hardware.hwdisplay.displayengine.HwPicDisplayEngineConfig config, byte[] data) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(feature);
          _Parcel.writeTypedObject(_data, config, 0);
          _data.writeByteArray(data);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setNativeHidlBuff, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method setNativeHidlBuff is unimplemented.");
          }
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override
      public int getInterfaceVersion() throws android.os.RemoteException {
        if (mCachedVersion == -1) {
          android.os.Parcel data = android.os.Parcel.obtain();
          android.os.Parcel reply = android.os.Parcel.obtain();
          try {
            data.writeInterfaceToken(DESCRIPTOR);
            boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceVersion, data, reply, 0);
            reply.readException();
            mCachedVersion = reply.readInt();
          } finally {
            reply.recycle();
            data.recycle();
          }
        }
        return mCachedVersion;
      }
      @Override
      public synchronized String getInterfaceHash() throws android.os.RemoteException {
        if ("-1".equals(mCachedHash)) {
          android.os.Parcel data = android.os.Parcel.obtain();
          android.os.Parcel reply = android.os.Parcel.obtain();
          try {
            data.writeInterfaceToken(DESCRIPTOR);
            boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceHash, data, reply, 0);
            reply.readException();
            mCachedHash = reply.readString();
          } finally {
            reply.recycle();
            data.recycle();
          }
        }
        return mCachedHash;
      }
    }
    static final int TRANSACTION_getHardwareEffect = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_getSupported = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getVsyncEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_registerCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_setHardwareEffect = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_setNativeHidlBuff = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_getInterfaceVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777214);
    static final int TRANSACTION_getInterfaceHash = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777213);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "vendor$honor$hardware$hwdisplay$displayengine$IDisplayEngineWrapper".replace('$', '.');
  public vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData getHardwareEffect(int feature, int mode, int length) throws android.os.RemoteException;
  public int getSupported(int feature) throws android.os.RemoteException;
  public vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData getVsyncEvent(int mode) throws android.os.RemoteException;
  public void registerCallback(vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback callback) throws android.os.RemoteException;
  public int setHardwareEffect(int feature, int mode, byte[] data) throws android.os.RemoteException;
  public int setNativeHidlBuff(int feature, vendor.honor.hardware.hwdisplay.displayengine.HwPicDisplayEngineConfig config, byte[] data) throws android.os.RemoteException;
  public int getInterfaceVersion() throws android.os.RemoteException;
  public String getInterfaceHash() throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
