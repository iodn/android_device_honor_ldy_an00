// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.service.vr.IVrManager;
import android.view.Display;
import com.android.server.display.plugin.Plugin;
import com.android.server.display.plugin.PluginStorage;
import com.android.server.display.plugin.PluginType;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;

/** Existing plugin worker owns all file I/O, sampling and stock thermal history. */
public final class ThermalPolicyPlugin implements Plugin {
    private static final Path THERMAL = Path.of("/system_ext/etc/honor/thermal-display-policy.json");
    private static final Path PANEL = Path.of("/system_ext/etc/honor/stock-lcd-panel.json");
    // Cheap raw polling covers sampler phase drift; no age/TTL or wake lock.
    private static final long POLL_MS = 1000;
    private final Context mContext;
    private final PluginStorage mStorage;
    private ThermalPolicyPublisher mPublisher;
    private StockAplRuntime mAplRuntime;
    private SelectedManualLuxSource mManualLux;
    private StockFullscreenProducer mFullscreenProducer;
    private com.android.server.display.plugin.types.BrightnessThermalPolicy.CompositionSource
            mAplCompositionSource; // Existing worker owns source identity and retirement ordering.

    private final Runnable mPoll = this::poll;
    private HandlerThread mThread;
    private Handler mHandler;
    private ThermalDisplayBatch mLastSample;
    private boolean mContextDirty = true;
    private String mBlockedBatch;
    private DisplayManager mDisplays;
    private ActivityManager mActivities;
    private BatteryManager mBattery;
    private StockLcdPanel mPanel;
    private ThermalPolicyCoordinator mCoordinator;
    private String mPreviousRawScene = "10000"; // Y0 port-owned constructor state, not active config.
    private int mLastDefaultDisplayState = -1;
    private Boolean mPcCast;
    private StockLcdPolicy.HdrSceneState mHdrSceneState;
    // Stock controller clinit defaults; changed only by coherent, source-qualified intents.
    private boolean mHdrRestricted;
    private volatile String mDump = "not started";

    public ThermalPolicyPlugin(Context context, PluginStorage storage) {
        mContext = context; mStorage = storage;
    }
    @Override public void onBootCompleted() {
        if (mHandler != null) return;
        // Plugin has process lifetime and no stop callback. Do not start unless selected.
        mThread = new HandlerThread("LdyLcdThermal");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mHandler.post(() -> {
            try {
                // Both typed loaders pin bytes/source identity; never DPC-thread file I/O.
                var metadata = ThermalMetadataLoader.load(Files.readAllBytes(THERMAL));
                mPanel = StockLcdPanelLoader.load(Files.readAllBytes(PANEL));
                mCoordinator = new ThermalPolicyCoordinator(metadata);
                mPcCast = pcCast(); // Bootclasspath authority presence cannot change in this process.
                mHdrSceneState = baselineHdrAuthority();
                mPublisher = new ThermalPolicyPublisher(mPanel,(id,policy) ->
                        mStorage.updateValue(PluginType.BRIGHTNESS_THERMAL_POLICY,id,policy));
                try {
                    var manualProfile=StockManualLuxProfile.load(Files.readAllBytes(
                            Path.of("/system_ext/etc/honor/stock-manual-lux.json")));
                    var sensors=mContext.getSystemService(android.hardware.SensorManager.class);
                    if(sensors!=null) mManualLux=new SelectedManualLuxSource(
                            sensors,mHandler,manualProfile,
                            lux -> mPublisher.manualHistoryLux(lux<0f?null:lux));
                } catch(Exception unavailable) {
                    // Optional sensor/profile failure must not disable ordinary thermal limits.
                    mPublisher.manualHistoryLux(null);
                }
                mAplRuntime=new StockAplRuntime(mHandler,mPublisher,() -> {
                    if(mFullscreenProducer!=null)mFullscreenProducer.invalidateContext();
                });
                boolean exclusiveApl=StockAplDeployment.qualifyAndClaim(
                        SystemProperties.getBoolean("ro.display.apl_bridge",false),
                        mHdrSceneState==StockLcdPolicy.HdrSceneState.BASELINE,
                        StockAplDeployment.COHORT.keySet().stream().collect(
                                java.util.stream.Collectors.toMap(key -> key,
                                        key -> SystemProperties.get(key,""))));
                if(exclusiveApl && !mAplRuntime.bind(true))mPublisher.invalidateApl();
                if(mAplRuntime.ready()) {
                    try {
                        var fullscreen=StockFullscreenProfile.load(Files.readAllBytes(
                                Path.of("/system_ext/etc/honor/stock-aps-fullscreen.json")));
                        mFullscreenProducer=new StockFullscreenProducer(mHandler,fullscreen,
                                value -> mAplRuntime.ownedFullscreen(
                                        mAplRuntime.ready()?value:null));
                    } catch(Exception unavailable) {
                        mAplRuntime.ownedFullscreen(null);
                    }
                }
                mPublisher.aplControlOwner(new StockLcdPolicy.AplControlOwner() {
                    public boolean canDispatchFullScene(){return mAplRuntime.ready();}
                    public void requestFullScene(boolean enabled){
                        mHandler.post(() -> mAplRuntime.fullScene(enabled));
                    }
                    public void setManualSensorActive(boolean enabled){
                        mHandler.post(() -> {
                            if(mManualLux!=null)mManualLux.setActive(enabled);
                            else mPublisher.manualHistoryLux(null);
                        });
                    }
                    public void onDisplayPower(
                            com.android.server.display.plugin.types.BrightnessThermalPolicy
                                    .CompositionSource source,int state,int policy){
                        mHandler.post(() -> {
                            if(!updateCompositionSource(source,true))return;
                            mAplRuntime.displayPower(state);
                            if(mFullscreenProducer!=null) {
                                if(mAplRuntime.ready())mFullscreenProducer.displayOn(state==2);
                                else mFullscreenProducer.unavailable();
                            }
                        });
                    }
                    public void observeNativeHdr(Boolean hdr) {
                        mHandler.post(() -> mAplRuntime.hdr(hdr));
                    }
                    public void onCompositionSource(
                            com.android.server.display.plugin.types.BrightnessThermalPolicy
                                    .CompositionSource source,boolean active) {
                        mHandler.post(() -> {
                            updateCompositionSource(source,active);
                        });
                    }
                });
                mContext.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context context,Intent intent) {
                        if(Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction()))
                            mPublisher.aplBatteryLevel(intent.getIntExtra("level",0));
                        else if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())
                                && mFullscreenProducer!=null)mFullscreenProducer.screenOff();
                    }
                },aplSourceIntents(),null,mHandler);
                mDisplays = mContext.getSystemService(DisplayManager.class);
                mActivities = mContext.getSystemService(ActivityManager.class);
                mBattery = mContext.getSystemService(BatteryManager.class);
                if (mDisplays == null || mActivities == null || mBattery == null) {
                    mDump = "not-ready: required framework service"; return;
                }
                mDisplays.registerDisplayListener(new DisplayManager.DisplayListener() {
                    @Override public void onDisplayAdded(int id) { schedule(); }
                    @Override public void onDisplayRemoved(int id) { schedule(); }
                    @Override public void onDisplayChanged(int id) {
                        if (id != Display.DEFAULT_DISPLAY) return;
                        if(mFullscreenProducer!=null)mFullscreenProducer.changed();
                        Display d = mDisplays.getDisplay(id);
                        int state = d == null ? -1 : d.getState();
                        if (state != mLastDefaultDisplayState) schedule();
                    }
                },mHandler);
                try {
                    ActivityTaskManager.getService().registerTaskStackListener(new TaskStackListener() {
                        @Override public void onTaskStackChanged() {
                            mHandler.post(() -> {
                                if(mFullscreenProducer!=null)mFullscreenProducer.changed();
                                schedule();
                            });
                        }
                        @Override public void onTaskFocusChanged(int taskId,boolean focused) {
                            onTaskStackChanged();
                        }
                        @Override public void onTaskDisplayChanged(int taskId,int displayId) {
                            onTaskStackChanged();
                        }
                    });
                    if(mFullscreenProducer!=null)mFullscreenProducer.eventAuthority(true);
                } catch (RemoteException unavailable) {
                    if(mFullscreenProducer!=null)mFullscreenProducer.eventAuthority(false);
                    // No qualified fullscreen producer without focus-generation notifications.
                    // Ordinary thermal poll remains available.
                }
                poll();
            } catch (IOException | org.json.JSONException | RuntimeException failure) {
                mDump = "not-ready: fixed metadata/service initialization";
            }
        });
    }
    private static IntentFilter aplSourceIntents() {
        var filter=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        return filter;
    }

    private void schedule() {
        mContextDirty = true; mHandler.removeCallbacks(mPoll); mHandler.post(mPoll);
    }

    private boolean updateCompositionSource(
            com.android.server.display.plugin.types.BrightnessThermalPolicy.CompositionSource source,
            boolean active) {
        if(active && (source==null || !source.isCurrent()))return false;
        if(!active && mAplCompositionSource!=source)return false;
        if(active && mAplCompositionSource==source)return true;
        if(mAplCompositionSource!=null) {
            mAplRuntime.retireOwner();
            if(mFullscreenProducer!=null)mFullscreenProducer.displayOn(false);
        }
        mAplCompositionSource=active?source:null;
        if(mFullscreenProducer!=null)mFullscreenProducer.source(source,active);
        return true;
    }

    private void poll() {
        mHandler.removeCallbacks(mPoll);
        try {
            if(mAplRuntime!=null && mAplRuntime.ready())mAplRuntime.refreshBacklight();
            if(mFullscreenProducer!=null) {
                if(mAplRuntime.ready())mFullscreenProducer.poll();
                else mFullscreenProducer.unavailable();
            }
            ThermalDisplayBatch batch = null;
            String encoded = SystemProperties.get(ThermalPolicyBatch.PROPERTY,"");
            if (!"running".equals(SystemProperties.get(
                    "init.svc.ldy-an00-shell-temperature",""))) {
                mBlockedBatch = encoded;
                mLastSample = null;
                mCoordinator.observe(null,null,null,false);
                mDump = "not-ready: sampler not running";
                return; // Previously accepted limits remain; no release/context publication.
            }
            try {
                String shell=SystemProperties.get(ThermalDisplayBatch.SHELL_PROPERTY,"");
                String after=SystemProperties.get(ThermalPolicyBatch.PROPERTY,"");
                if (!encoded.equals(mBlockedBatch)) batch=ThermalDisplayBatch.read(
                        encoded,shell,after,SystemClock.elapsedRealtime());
            } catch (RuntimeException unavailable) { /* Missing sample resets its epoch. */ }
            if (batch == null) {
                mLastSample = null;
                mCoordinator.observe(null,null,null,false);
                mDump = "not-ready: coherent sensor batch";
                return;
            }
            mBlockedBatch = null;
            // Avoid Binder/PackageManager/Settings work on ordinary raw duplicates.
            if (batch.equals(mLastSample) && !mContextDirty) return;
            mLastSample = batch;
            mContextDirty = false;
            // Context retrieval failure must never prevent complete physical cycles advancing.
            Integer percent = null;
            try {
                int value = mBattery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (value >= 0 && value <= 100) percent = value;
            } catch (RuntimeException unavailable) { /* Explicit unavailable context. */ }
            ThermalSceneResolver.Snapshot snapshot = null;
            try { snapshot = scene(); }
            catch (RuntimeException unavailable) { /* Explicit unavailable context. */ }
            boolean secondary = false;
            try { secondary = secondaryEmpty(); }
            catch (RuntimeException unavailable) { /* Absence shortcut not qualified. */ }
            // No TTL: complete sequenced batches define continuity; repeats are deduplicated.
            var result = mCoordinator.observe(batch,snapshot,percent,secondary);
            if (result.scene() != null && result.scene().rawScene() != null) {
                mPreviousRawScene = result.scene().rawScene();
            }
            if (result.status() == ThermalPolicyCoordinator.Status.READY) {
                Integer outdoor = null;
                for (var control : result.controls()) {
                    if ("hdr_ctrl".equals(control.channel())) {
                        mHdrRestricted = control.value() == 1; // Exact T->J0 receiver.
                    } else if ("outbrightness_ctrl".equals(control.channel())
                            && (control.value() == 0 || control.value() == 1)) {
                        outdoor = control.value(); // Other positive values are receiver NOOP.
                    }
                }
                var stockContext = currentStockContext(); // Always a nonnull typed snapshot.
                try { publish(result.dispatch(),stockContext,outdoor); }
                catch (RuntimeException publicationFailure) {
                    // Retry the same pending factory; never reset valid physical history here.
                    mContextDirty = true;
                    mDump = "not-ready: policy registration pending"; return;
                }
                mDump = "ordinary-ready epoch=" + result.epoch() + " logicalDispatch="
                        + (result.dispatch()!=null) + " hdrAuthority=" + mHdrSceneState
                        + " pending=" + mPublisher.pendingRegistrations();
            } else mDump = result.status().name() + " epoch=" + result.epoch();
        } catch (RuntimeException failure) {
            // No manufactured release; preserve previously accepted policy/commands.
            mLastSample = null; mContextDirty = true;
            mCoordinator.observe(null,null,null,false);
            mDump = "not-ready: framework input retrieval";
        } finally {
            mHandler.postDelayed(mPoll,POLL_MS);
        }
    }
    private ThermalSceneResolver.Snapshot scene() {
        var tasks = mActivities.getRunningTasks(1);
        if (tasks == null) return null;
        String pkg = tasks.isEmpty() || tasks.get(0).topActivity == null ? null
                : tasks.get(0).topActivity.getPackageName();
        Integer cms = ServiceManager.checkService("IAwareCMSService") == null ? -1 : null;
        Boolean highSpeed = boosterAbsent() ? false : null;
        Display display = mDisplays.getDisplay(Display.DEFAULT_DISPLAY);
        mLastDefaultDisplayState = display == null ? -1 : display.getState();
        Boolean screen = display == null ? null
                : ThermalSceneResolver.initialScreenOn(mLastDefaultDisplayState);
        String demo = Settings.Secure.getString(mContext.getContentResolver(),"DemoVersion");
        var flags = new ThermalSceneResolver.Flags(vr(),highSpeed,screen,mPcCast,true,demo,
                false,false,false,2);
        return new ThermalSceneResolver.Snapshot(
                new ThermalSceneResolver.Foreground(pkg,cms,matchedPid(pkg),mPreviousRawScene),
                flags,Map.of());
    }
    private int matchedPid(String pkg) {
        if (pkg == null) return -1;
        var processes = mActivities.getRunningAppProcesses();
        if (processes != null) for (var process : processes) {
            if (process == null || process.pkgList == null) continue;
            for (String member : process.pkgList) if (pkg.equals(member)) return process.pid;
        }
        return -1;
    }
    private StockLcdPolicy.HdrSceneState baselineHdrAuthority() {
        return FrameworkAuthority.allAbsent(Plugin.class.getClassLoader(),
                "com.android.server.display.HwBrightnessSceneController",
                "com.android.server.display.HwBrightnessSceneRecognition",
                "com.android.server.display.DisplayEngineService",
                "com.android.server.display.DisplayEngineService$HdrListener")
                ? StockLcdPolicy.HdrSceneState.BASELINE : StockLcdPolicy.HdrSceneState.NOT_READY;
    }
    private Boolean pcCast() {
        // Honor cast authority, not AOSP desktop. Probe once via services parent + boot delegation.
        return FrameworkAuthority.allAbsent(Plugin.class.getClassLoader(),
                "android.common.HwFrameworkFactory","android.util.HwPCUtils")
                ? Boolean.FALSE : null;
    }
    private Boolean vr() {
        var binder = ServiceManager.checkService(Context.VR_SERVICE);
        if (binder == null) {
            // Stock null VR authority leaves its constructor-false state untouched.
            // Require both platform feature declarations absent before using that port state.
            var packages = mContext.getPackageManager();
            return !packages.hasSystemFeature("android.hardware.vr.high_performance")
                    && !packages.hasSystemFeature("android.software.vr.mode")
                    ? Boolean.FALSE : null;
        }
        try { return IVrManager.Stub.asInterface(binder).getVrModeState(); }
        catch (RemoteException failure) { return null; }
    }
    private boolean boosterAbsent() {
        try {
            mContext.getPackageManager().getPackageInfo("com.hihonor.systemserver",0);
            return false;
        } catch (PackageManager.NameNotFoundException absent) { return true; }
    }
    private boolean secondaryEmpty() {
        // Only exact empty strings qualify; nonempty "0"/whitespace is foldable in stock.
        return SystemProperties.get("msc.config.fold_disp","").isEmpty()
                && SystemProperties.get("persist.sys.fold.disp.size","").isEmpty();
        // Pinned ThermalMetadataLoader hash proves 23 duoStrict defaults1/no duo-opt.
    }
    private StockLcdPolicy.StockContext currentStockContext() {
        // Hash-pinned selected XML omits LowBattery, and stock113 Data default disables its
        // early-reboot brightness guard. This is a qualified profile fact, not battery inference.
        // Battery restriction is source-disabled by the selected profile's absent Qinghai tag.
        // Actual HDR group comes from the SAME framework frame; compatibility scene is unused.
        // Primary manual input is normalized [0,1], so it cannot exceed the source front-camera
        // raw maximum (102 or255). Genuine APL context remains unknown until its owner is bound.
        var hdr = new StockLcdCapController.HdrContext(null,mHdrRestricted,Boolean.FALSE,
                mPanel.hdrNoLimit(),null,null,null,null,Boolean.TRUE);
        return new StockLcdPolicy.StockContext(Boolean.TRUE,hdr,null,null,mHdrSceneState);
    }
    private void publish(StockLcdCapAggregator.Dispatch dispatch,StockLcdPolicy.StockContext context,
            Integer outdoorCommand) {
        var present = new HashSet<String>();
        Display display = mDisplays.getDisplay(Display.DEFAULT_DISPLAY);
        if (display != null && display.getType()==Display.TYPE_INTERNAL) {
            present.add(display.getUniqueId()); // Coefficients/front-camera proof target primary panel.
        }
        mPublisher.offer(dispatch,context,outdoorCommand,present);
    }
    @Override public void dump(PrintWriter pw) { pw.println("LDY-AN00 LCD policy: "+mDump); }
}
