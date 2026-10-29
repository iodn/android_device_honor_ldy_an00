package org.lineageos.ldy_an00.display;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vendor.honor.hardware.hwdisplay.displayengine.DisplayRetData;
import vendor.honor.hardware.hwdisplay.displayengine.HwPicDisplayEngineConfig;
import vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback;
import vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper;

/** Actual owner class and Android Handler; mock Binder endpoint is lifecycle plumbing only. */
public final class StockAplBridgeTest {
    private TestLooper looper;
    private StockAplBridge bridge;
    private final List<StockAplEvent> samples = new ArrayList<>();
    private int invalidations;
    private final List<StockDisplayAuxNotice> notices = new ArrayList<>();
    private FakeService service;
    private IBinder endpoint;
    private IBinder.DeathRecipient death;

    @Before public void setUp() throws Exception {
        looper = new TestLooper();
        bridge = new StockAplBridge(new Handler(looper.getLooper()), samples::add,
                () -> invalidations++);
        service = new FakeService();
        endpoint = mock(IBinder.class);
        when(endpoint.isBinderAlive()).thenReturn(true);
        when(endpoint.queryLocalInterface(
                "vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper"))
                .thenReturn(service);
        doAnswer(call -> { death = call.getArgument(0); return null; })
                .when(endpoint).linkToDeath(any(), eq(0));
        when(endpoint.unlinkToDeath(any(), eq(0))).thenReturn(true);
    }
    @After public void tearDown() { bridge.close(); }

    private boolean bind() { return bridge.bindEndpoint(endpoint, true, true); }
    private static byte[] event() { return new byte[] {(byte)150,0,0,0,1,0,0,0}; }
    private static byte[] snapshot(int mode) {
        byte[] data = new byte[176]; data[0] = (byte)mode; return data;
    }

    @Test public void optInAndOwnershipBothRequired() {
        assertFalse(bridge.bindEndpoint(endpoint, false, true));
        assertFalse(bridge.bindEndpoint(endpoint, true, false));
        assertEquals(0, service.registrations);
    }
    @Test public void wrongVersionRefusesBeforeRegistration() {
        service.version = 2; assertFalse(bind()); assertEquals(0, service.registrations);
    }
    @Test public void wrongHashRefusesBeforeRegistration() {
        service.hash = "wrong"; assertFalse(bind()); assertEquals(0, service.registrations);
    }
    @Test public void eventDeliveredOnlyOnOwner() throws Exception {
        assertTrue(bind()); service.callback.onEventWithData(8, event());
        assertTrue(samples.isEmpty()); looper.dispatchAll();
        assertEquals(1, samples.size()); assertEquals(150, samples.get(0).rawRatio());
    }
    @Test public void deathInvalidatesBeforeQueuedEvent() throws Exception {
        assertTrue(bind()); service.callback.onEventWithData(8, event()); death.binderDied();
        looper.dispatchAll(); assertTrue(samples.isEmpty()); assertEquals(1, invalidations);
    }
    @Test public void closeDropsQueuedEvent() throws Exception {
        assertTrue(bind()); service.callback.onEventWithData(8, event()); bridge.close();
        looper.dispatchAll(); assertTrue(samples.isEmpty()); assertEquals(1, invalidations);
    }
    @Test public void rebindDropsOldCallbackAndGeneration() throws Exception {
        assertTrue(bind()); IDisplayEngineCallback old = service.callback;
        old.onEventWithData(8, event());
        assertTrue(bind()); old.onEventWithData(8, event());
        service.callback.onEventWithData(8, event()); looper.dispatchAll();
        assertEquals(1, samples.size()); assertEquals(2, samples.get(0).generation());
    }
    @Test public void malformedEventsCannotPublish() throws Exception {
        assertTrue(bind()); service.callback.onEventWithData(8, new byte[7]);
        service.callback.onEventWithData(3, event()); service.callback.onEvent(8, 406);
        looper.dispatchAll(); assertTrue(samples.isEmpty());
    }
    @Test public void setterChecksBlobAndModeBeforeCall() {
        assertTrue(bind());
        assertFalse(bridge.applyHistogram(123, new byte[175]));
        assertFalse(bridge.applyHistogram(122, snapshot(122)));
        assertFalse(bridge.applyHistogram(123, snapshot(48)));
        assertEquals(0, service.setters);
        assertTrue(bridge.applyHistogram(123, snapshot(123)));
        assertEquals(1, service.setters);
        assertEquals(29, service.feature);
        assertEquals(StockAplBridge.CollectorOutcome.REQUEST_RETURNED_OK,
                bridge.collectorOutcome());
    }
    @Test public void serviceErrorInvalidatesWithoutRetry() {
        assertTrue(bind()); service.result = -1;
        assertFalse(bridge.applyHistogram(123, snapshot(123)));
        assertFalse(bridge.applyHistogram(123, snapshot(123)));
        assertEquals(1, service.setters); assertEquals(1, invalidations);
        assertEquals(StockAplBridge.CollectorOutcome.SERVICE_ERROR_STATE_UNVERIFIED,
                bridge.collectorOutcome());
        assertFalse(bind()); assertEquals(1, service.registrations);
    }
    @Test public void transportUnknownDoesNotIssueCompensatingSetter() {
        assertTrue(bind()); service.failSetter = true;
        assertFalse(bridge.applyHistogram(49, snapshot(49)));
        assertEquals(1, service.setters); assertEquals(1, invalidations);
        assertFalse(bridge.applyHistogram(48, snapshot(48))); assertEquals(1, service.setters);
        assertEquals(StockAplBridge.CollectorOutcome.TRANSPORT_COMPLETION_UNKNOWN,
                bridge.collectorOutcome());
        assertFalse(bind()); assertEquals(1, service.registrations);
    }
    @Test public void rejectedMetadataFailureNeverRegisters() {
        service.failMetadata = true; assertFalse(bind()); assertEquals(0, service.registrations);
    }
    @Test public void callbackMetadataMatchesPinnedWireContract() throws Exception {
        assertTrue(bind()); assertEquals(1, service.callback.getInterfaceVersion());
        assertEquals(StockAplBridge.INTERFACE_HASH, service.callback.getInterfaceHash());
    }

    @Test public void normalCloseRequestsOneQualifiedStopAndRetainsOpaqueFields() {
        assertTrue(bind()); byte[] active = snapshot(48); active[100] = 73;
        assertTrue(bridge.applyHistogram(48, active)); active[100] = 99;
        bridge.close(); assertEquals(2, service.setters); assertEquals(49, service.mode);
        assertEquals(73, service.lastData[100]);
        assertEquals(StockAplBridge.CollectorOutcome.STOP_REQUEST_RETURNED_OK,
                bridge.collectorOutcome());
        bridge.close(); assertEquals(2, service.setters);
    }
    @Test public void failedNormalStopNeverRetriesOrRegistersAgain() {
        assertTrue(bind()); assertTrue(bridge.applyHistogram(48, snapshot(48)));
        service.failSetter = true; bridge.close(); assertEquals(2, service.setters);
        bridge.close(); assertEquals(2, service.setters);
        assertEquals(StockAplBridge.CollectorOutcome.TRANSPORT_COMPLETION_UNKNOWN,
                bridge.collectorOutcome());
        assertFalse(bind()); assertEquals(1, service.registrations);
    }
    @Test public void passiveCloseIssuesNoSetter() {
        assertTrue(bind()); bridge.close(); assertEquals(0, service.setters);
        assertEquals(StockAplBridge.CollectorOutcome.NEVER_REQUESTED,
                bridge.collectorOutcome());
    }
    @Test public void explicitStopResultIsOnlyARequestReceipt() {
        assertTrue(bind()); assertTrue(bridge.applyHistogram(48, snapshot(48)));
        assertTrue(bridge.applyHistogram(49, snapshot(49)));
        assertEquals(StockAplBridge.CollectorOutcome.STOP_REQUEST_RETURNED_OK,
                bridge.collectorOutcome());
        bridge.close(); assertEquals(2, service.setters);
        assertTrue(bind()); assertEquals(2, service.registrations);
    }

    @Test public void deadOwnerCannotAuthorizeStop() {
        assertTrue(bind()); assertTrue(bridge.applyHistogram(48, snapshot(48)));
        death.binderDied(); looper.dispatchAll(); assertEquals(1, service.setters);
        assertEquals(StockAplBridge.CollectorOutcome.OWNER_LOST_STATE_UNVERIFIED,
                bridge.collectorOutcome());
        assertFalse(bind()); assertEquals(1, service.registrations);
    }

    private void noticeOwner() {
        bridge.close();
        bridge = new StockAplBridge(new Handler(looper.getLooper()), samples::add,
                notices::add, () -> invalidations++);
    }
    @Test public void noticeSourceResetPrecedesOwnerDelivery() throws Exception {
        noticeOwner(); assertTrue(bind()); assertEquals(1, notices.size());
        assertTrue(notices.get(0) instanceof StockDisplayAuxNotice.SourceReset);
        service.callback.onEventWithData(3, new byte[]{1}); assertEquals(1, notices.size());
        looper.dispatchAll(); assertEquals(2, notices.size());
        assertTrue(((StockDisplayAuxNotice.Fullscreen)notices.get(1)).active());
    }
    @Test public void deathDropsQueuedAuxiliaryNotice() throws Exception {
        noticeOwner(); assertTrue(bind()); notices.clear();
        service.callback.onEventWithData(3, new byte[]{1}); death.binderDied();
        looper.dispatchAll(); assertTrue(notices.isEmpty());
    }
    @Test public void rebindDropsOldAuxiliaryGeneration() throws Exception {
        noticeOwner(); assertTrue(bind()); IDisplayEngineCallback old = service.callback;
        old.onEventWithData(3, new byte[]{1}); assertTrue(bind()); notices.clear();
        old.onEventWithData(3, new byte[]{1});
        service.callback.onEventWithData(3, new byte[]{0}); looper.dispatchAll();
        assertEquals(1, notices.size()); assertEquals(2, notices.get(0).generation());
        assertFalse(((StockDisplayAuxNotice.Fullscreen)notices.get(0)).active());
    }
    @Test public void unknownAuxiliaryFormatHasSpecificScope() throws Exception {
        noticeOwner(); assertTrue(bind()); notices.clear();
        service.callback.onEventWithData(3, new byte[0]);
        service.callback.onEventWithData(4, new byte[2]); looper.dispatchAll();
        assertEquals(2, notices.size());
        assertEquals(StockDisplayAuxNotice.Domain.FULLSCREEN,
                ((StockDisplayAuxNotice.Unknown)notices.get(0)).domain());
        assertEquals(StockDisplayAuxNotice.Domain.ENHANCEMENT,
                ((StockDisplayAuxNotice.Unknown)notices.get(1)).domain());
    }
    @Test public void unrelatedNativeEventsDoNotPoisonAuxiliaryState() throws Exception {
        noticeOwner(); assertTrue(bind()); notices.clear();
        service.callback.onEvent(3, 100);
        service.callback.onEventWithData(5, new byte[8]);
        service.callback.onEventWithData(7, new byte[8]);
        service.callback.onEventWithData(8, event()); looper.dispatchAll();
        assertTrue(notices.isEmpty()); assertEquals(1, samples.size());
    }

    @Test public void capturedAtomicLifetimeTurnsFalseBeforeWorkerInvalidationRuns() {
        assertTrue(bind());
        var alive=bridge.lifetimeGuard(1);
        assertTrue(alive.getAsBoolean());
        assertFalse(bridge.lifetimeGuard(2).getAsBoolean());
        death.binderDied();
        assertFalse(alive.getAsBoolean());
        looper.dispatchAll();
        assertFalse(alive.getAsBoolean());
    }
    @Test public void lifetimeGuardRemainsBoundToItsOriginalGeneration() {
        assertTrue(bind());
        var first=bridge.lifetimeGuard(1);
        assertTrue(bind());
        assertFalse(first.getAsBoolean());
        assertTrue(bridge.lifetimeGuard(2).getAsBoolean());
        bridge.close();
        assertFalse(bridge.lifetimeGuard(2).getAsBoolean());
    }

    private static final class FakeService extends IDisplayEngineWrapper.Stub {
        int version = 1, registrations, setters, feature, mode, result;
        byte[] lastData;
        String hash = StockAplBridge.INTERFACE_HASH;
        boolean failMetadata, failSetter;
        IDisplayEngineCallback callback;
        @Override public int getInterfaceVersion() {
            if (failMetadata) throw new SecurityException("metadata test"); return version;
        }
        @Override public String getInterfaceHash() { return hash; }
        @Override public void registerCallback(IDisplayEngineCallback callback) {
            registrations++; this.callback = callback;
        }
        @Override public int setHardwareEffect(int feature, int mode, byte[] data)
                throws RemoteException {
            setters++; this.feature = feature; this.mode = mode; lastData = data.clone();
            if (failSetter) throw new RemoteException("completion unknown plumbing");
            return result;
        }
        @Override public DisplayRetData getHardwareEffect(int a, int b, int c) {
            throw new AssertionError("unused getter");
        }
        @Override public int getSupported(int a) { throw new AssertionError("unused getter"); }
        @Override public DisplayRetData getVsyncEvent(int a) {
            throw new AssertionError("unused getter");
        }
        @Override public int setNativeHidlBuff(int a, HwPicDisplayEngineConfig b, byte[] c) {
            throw new AssertionError("unused transaction 6");
        }
    }
}
