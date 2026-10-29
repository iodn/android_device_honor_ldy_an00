/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: cmd not shown due to `--omit_invocation`
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package vendor.honor.hardware.hwdisplay.displayengine;
public interface IDisplayEngineCallback extends android.os.IInterface
{
  /**
   * The version of this interface that the caller is built against.
   * This might be different from what {@link #getInterfaceVersion()
   * getInterfaceVersion} returns as that is the version of the interface
   * that the remote object is implementing.
   */
  public static final int VERSION = 1;
  public static final String HASH = "1b12fa2f43d0511e7bd32246d36bddfe533ae419";
  /** Default implementation for IDisplayEngineCallback. */
  public static class Default implements vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback
  {
    @Override public void onEvent(int event, int extra) throws android.os.RemoteException
    {
    }
    @Override public void onEventWithData(int event, byte[] data) throws android.os.RemoteException
    {
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
  public static abstract class Stub extends android.os.Binder implements vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.markVintfStability();
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback interface,
     * generating a proxy if needed.
     */
    public static vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback))) {
        return ((vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback)iin);
      }
      return new vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onEvent:
        {
          int _arg0;
          _arg0 = data.readInt();
          int _arg1;
          _arg1 = data.readInt();
          this.onEvent(_arg0, _arg1);
          break;
        }
        case TRANSACTION_onEventWithData:
        {
          int _arg0;
          _arg0 = data.readInt();
          byte[] _arg1;
          _arg1 = data.createByteArray();
          this.onEventWithData(_arg0, _arg1);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback
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
      @Override public void onEvent(int event, int extra) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(event);
          _data.writeInt(extra);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onEvent, _data, null, android.os.IBinder.FLAG_ONEWAY);
          if (!_status) {
            throw new android.os.RemoteException("Method onEvent is unimplemented.");
          }
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onEventWithData(int event, byte[] data) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(event);
          _data.writeByteArray(data);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onEventWithData, _data, null, android.os.IBinder.FLAG_ONEWAY);
          if (!_status) {
            throw new android.os.RemoteException("Method onEventWithData is unimplemented.");
          }
        }
        finally {
          _data.recycle();
        }
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
    static final int TRANSACTION_onEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onEventWithData = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getInterfaceVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777214);
    static final int TRANSACTION_getInterfaceHash = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777213);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "vendor$honor$hardware$hwdisplay$displayengine$IDisplayEngineCallback".replace('$', '.');
  public void onEvent(int event, int extra) throws android.os.RemoteException;
  public void onEventWithData(int event, byte[] data) throws android.os.RemoteException;
  public int getInterfaceVersion() throws android.os.RemoteException;
  public String getInterfaceHash() throws android.os.RemoteException;
}
