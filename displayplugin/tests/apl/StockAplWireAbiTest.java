package org.lineageos.ldy_an00.display;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import android.os.IBinder;
import android.os.Parcel;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData;
import vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback;
import vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper;

/** Generated Android AIDL and real Parcel checks. No stock HAL calls. */
public final class StockAplWireAbiTest {
    private static int transaction(Class<?> owner, String method) throws Exception {
        Field field = owner.getDeclaredField("TRANSACTION_" + method);
        field.setAccessible(true); return field.getInt(null);
    }
    @Test public void wrapperTransactionOrderMatchesPinnedNdk() throws Exception {
        String[] names = {"getHardwareEffect", "getSupported", "getVsyncEvent",
                "registerCallback", "setHardwareEffect", "setNativeHidlBuff"};
        for (int i = 0; i < names.length; i++)
            assertEquals(i + 1, transaction(IDisplayEngineWrapper.Stub.class, names[i]));
        assertEquals(0xffffff, transaction(IDisplayEngineWrapper.Stub.class, "getInterfaceVersion"));
        assertEquals(0xfffffe, transaction(IDisplayEngineWrapper.Stub.class, "getInterfaceHash"));
    }
    @Test public void callbackTransactionOrderMatchesPinnedNdk() throws Exception {
        assertEquals(1, transaction(IDisplayEngineCallback.Stub.class, "onEvent"));
        assertEquals(2, transaction(IDisplayEngineCallback.Stub.class, "onEventWithData"));
    }
    @Test public void generatedMetadataPinsRemoteAbiRatherThanOpaqueSourceNames() {
        assertEquals(1, IDisplayEngineWrapper.VERSION);
        assertEquals(StockAplBridge.INTERFACE_HASH, IDisplayEngineWrapper.HASH);
        assertEquals(1, IDisplayEngineCallback.VERSION);
        assertEquals(StockAplBridge.INTERFACE_HASH, IDisplayEngineCallback.HASH);
    }
    @Test public void displayRetDataIsSizedThenByteArray() {
        Parcel parcel = Parcel.obtain();
        try {
            DisplayRetData data = new DisplayRetData(); data.opaqueData = new byte[]{1,2,3,4};
            data.writeToParcel(parcel, 0);
            int end = parcel.dataPosition(); parcel.setDataPosition(0);
            assertEquals(end, parcel.readInt());
            assertArrayEquals(data.opaqueData, parcel.createByteArray());
            assertEquals(end, parcel.dataPosition()); parcel.setDataPosition(0);
            assertArrayEquals(data.opaqueData, DisplayRetData.CREATOR.createFromParcel(parcel).opaqueData);
        } finally { parcel.recycle(); }
    }
    @Test public void generatedCallbackProxyUsesOnewayAndActualStubDecode() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        IDisplayEngineCallback.Stub receiver = new IDisplayEngineCallback.Stub() {
            @Override public void onEvent(int event, int extra) {
                assertEquals(7, event); assertEquals(19, extra); calls.incrementAndGet();
            }
            @Override public void onEventWithData(int event, byte[] bytes) {
                assertEquals(8, event); assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8}, bytes);
                calls.incrementAndGet();
            }
            @Override public int getInterfaceVersion() { return 1; }
            @Override public String getInterfaceHash() { return StockAplBridge.INTERFACE_HASH; }
        };
        IBinder proxyEndpoint = mock(IBinder.class);
        when(proxyEndpoint.transact(anyInt(), any(Parcel.class), isNull(), eq(IBinder.FLAG_ONEWAY)))
                .thenAnswer(call -> receiver.asBinder().transact(call.getArgument(0),
                        call.getArgument(1), null, call.getArgument(3)));
        IDisplayEngineCallback proxy = IDisplayEngineCallback.Stub.asInterface(proxyEndpoint);
        proxy.onEvent(7, 19);
        proxy.onEventWithData(8, new byte[]{1,2,3,4,5,6,7,8});
        assertEquals(2, calls.get());
        verify(proxyEndpoint).transact(eq(1), any(Parcel.class), isNull(), eq(IBinder.FLAG_ONEWAY));
        verify(proxyEndpoint).transact(eq(2), any(Parcel.class), isNull(), eq(IBinder.FLAG_ONEWAY));
    }
}
