// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;
import android.app.ActivityTaskManager;
import android.graphics.Point;
import android.view.WindowManagerGlobal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemProperties;
import com.android.server.display.plugin.types.BrightnessThermalPolicy.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Owner state is nonblocking. Binder/ATMS/SF work is serialized on one isolated query thread. */
final class StockFullscreenProducer {
    // Port safety budget, not a stock freshness/ADC/physical-present guarantee.
    private static final long QUERY_DEADLINE_MILLIS=2000;
    record Focus(int taskId,String component,String packageName,int uid,int userId,int displayId) {}
    interface FocusReader { Focus read() throws Exception; }
    record InitialSize(int width,int height) {}
    interface SizeReader { InitialSize read() throws Exception; }
    private static final class QueryThread {
        static final HandlerThread THREAD;
        static final Handler HANDLER;
        static {
            THREAD=new HandlerThread("LdyCompositionQuery");
            THREAD.start();
            HANDLER=new Handler(THREAD.getLooper());
        }
    }
    private final Handler mOwner;
    private final StockFullscreenProfile mProfile;
    private final Consumer<Boolean> mSink;
    private final Executor mQueries;
    private final FocusReader mFocus;
    private final SizeReader mInitialSize;
    private CompositionSource mSource;
    private boolean mOn,mEvents,mInFlight,mDebounced,mNeedsQuery,mCanPoll;
    private boolean mAvailable=true;
    private boolean mEventsKnown;
    private long mRevision,mRequest;
    private final Runnable mBegin=this::begin;

    StockFullscreenProducer(Handler owner,StockFullscreenProfile profile,Consumer<Boolean> sink) {
        this(owner,profile,sink,work -> QueryThread.HANDLER.post(work),
                StockFullscreenProducer::focusedTask,StockFullscreenProducer::initialSize);
    }
    StockFullscreenProducer(Handler owner,StockFullscreenProfile profile,Consumer<Boolean> sink,
            Executor queries,FocusReader focus,SizeReader initialSize) {
        mOwner=owner;mProfile=profile;mSink=sink;mQueries=queries;mFocus=focus;
        mInitialSize=initialSize;
        // Selected enabled policy's newly constructed SYSTEM owner starts false.
        // This is logical source initialization, not an observed foreground/HAL snapshot.
        mSink.accept(false);
    }
    private void requireOwner() {
        if(Looper.myLooper()!=mOwner.getLooper())throw new IllegalStateException("Wrong fullscreen owner");
    }
    void source(CompositionSource source,boolean active) {
        requireOwner();
        if(!active && mSource!=source)return; // Old-session close must not retire a new source.
        if(active && mSource==source)return;
        boolean replacement=mSource!=null || !active;
        mSource=active?source:null;
        if(replacement)mSink.accept(null);
        changed();
    }
    void eventAuthority(boolean available) {
        requireOwner();
        if(mEventsKnown && mEvents==available)return;
        mEventsKnown=true;mEvents=available;
        if(!available)mSink.accept(null);
        changed();
    }
    void displayOn(boolean on) {
        requireOwner();if(mOn==on)return;mOn=on;
        changed(); // Readiness only; physical state is not a synthesized SCREEN_OFF action.

    }
    void screenOff() {
        requireOwner();mSink.accept(false); // Actual protected SCREEN_OFF -> selected y.j(false).
        mOn=false;changed();
    }
    void unavailable() {
        requireOwner();if(!mAvailable)return;
        mAvailable=false;invalidateContext(); // A failed transport never manufactures false.
    }
    /** Real task/display events advance a monotonic revision, including A->B->A. */
    void changed() {
        requireOwner();++mRevision;mCanPoll=false;
        mOwner.removeCallbacks(mBegin);mDebounced=false;
        if(mOn && mEvents && mAvailable && mSource!=null && mSource.isCurrent()) {
            mDebounced=true;mOwner.postDelayed(mBegin,mProfile.debounceMillis());
        }
    }
    void invalidateContext() {
        requireOwner();mSink.accept(null);changed();
    }
    /** Existing 1s worker poll: deliberate bounded port fallback for same-focus layer resizing. */
    void poll() {
        requireOwner();
        if(!mOn || !mEvents || !mAvailable || mSource==null || !mSource.isCurrent())return;
        if(mCanPoll && !mInFlight && !mDebounced)begin();
    }
    private void begin() {
        requireOwner();mDebounced=false;
        if(!mOn || !mEvents || !mAvailable || mSource==null || !mSource.isCurrent())return;
        if(mInFlight) {mNeedsQuery=true;return;}
        mInFlight=true;mNeedsQuery=false;
        long request=++mRequest,revision=mRevision;
        var source=mSource;
        final boolean[] accepted={true}; // Accessed exclusively on owner Handler.
        Runnable deadline=() -> {
            if(mInFlight && request==mRequest) {
                accepted[0]=false;mSink.accept(null);
            }
            // Never clear inFlight here: timeout cannot cancel Binder or free its thread.
        };
        mOwner.postDelayed(deadline,QUERY_DEADLINE_MILLIS);
        try {
            mQueries.execute(() -> {
                Boolean result=null;
                boolean geometryForeground=false,noEvent=false;
                try {
                    Focus before=mFocus.read();
                    if(before!=null && source.isCurrent()) {
                        if("com.hihonor.android.launcher".equals(before.packageName())) {
                            if(before.equals(mFocus.read()))result=false;
                        } else if(mProfile.packages().contains(before.packageName())) {
                            var initial=mInitialSize.read();
                            var geometry=initial!=null
                                    && initial.width()==mProfile.initialWidth()
                                    && initial.height()==mProfile.initialHeight()
                                    ?source.query(before.uid(),before.packageName()):null;
                            Focus after=mFocus.read();
                            if(before.equals(after) && source.isCurrent()) {
                                geometryForeground=true;
                                if(initial!=null && initial.width()==mProfile.initialWidth()
                                        && initial.height()==mProfile.initialHeight())
                                    result=classify(geometry,before.uid(),before.packageName(),
                                            initial.width(),initial.height());
                            }
                        } else if(before.equals(mFocus.read())) {
                            noEvent=true; // Stock unsupported-package NO_EVENT retains global history.
                        }
                    }
                } catch(Exception unavailable) { /* UNKNOWN, never fabricated false. */ }
                Boolean completed=result;
                boolean canPoll=geometryForeground,retain=noEvent;
                mOwner.post(() -> {
                    requireOwner();mOwner.removeCallbacks(deadline);
                    mInFlight=false;
                    if(request==mRequest && revision==mRevision
                            && mOn && mEvents && mAvailable && source==mSource && source.isCurrent()) {
                        // A timed-out geometry answer stays rejected. Its still-current trusted
                        // foreground may only permit a NEW bounded query after actual completion.
                        mCanPoll=canPoll;
                        if(accepted[0] && !retain)mSink.accept(completed);
                    } else if(mOn && source==mSource && !source.isCurrent()) {
                        mSink.accept(null);
                    }
                    boolean again=mNeedsQuery;
                    mNeedsQuery=false;
                    if(again && mOn && mEvents && mAvailable && mSource!=null && mSource.isCurrent() && !mDebounced) {
                        mDebounced=true;mOwner.postDelayed(mBegin,mProfile.debounceMillis());
                    }
                });
            });
        } catch(RuntimeException refused) {
            mOwner.removeCallbacks(deadline);mInFlight=false;mSink.accept(null);
        }
    }
    static Focus focusedTask() throws Exception {
        var task=ActivityTaskManager.getService().getFocusedRootTaskInfo();
        if(task==null || !task.isFocused || !task.isVisible || task.displayId!=0
                || task.topActivity==null || task.topActivityInfo==null
                || task.topActivityInfo.applicationInfo==null)return null;
        String pkg=task.topActivity.getPackageName();
        if(!pkg.equals(task.topActivityInfo.packageName))return null;
        int uid=task.topActivityInfo.applicationInfo.uid;
        if(uid<0)return null;
        return new Focus(task.taskId,task.topActivity.flattenToString(),pkg,uid,
                task.userId,task.displayId);
    }
    static InitialSize initialSize() throws Exception {
        Point size=new Point();
        WindowManagerGlobal.getWindowManagerService().getInitialDisplaySize(0,size);
        return size.x>0 && size.y>0?new InitialSize(size.x,size.y):null;
    }
    static Boolean classify(CompositionGeometry geometry,int uid,String pkg,
            int initialWidth,int initialHeight) {
        if(initialWidth<=0 || initialHeight<=0 || geometry==null || !geometry.complete() || geometry.physicalWidth()<=0
                || geometry.physicalHeight()<=0
                || geometry.outputWidth()!=geometry.physicalWidth()
                || geometry.outputHeight()!=geometry.physicalHeight()
                || geometry.rotation()<0 || geometry.rotation()>3)return null;
        // Exact selected default-phone classifier; optional custom-ratio branch unsupported.
        if(!SystemProperties.get("sys.aps.full_video_ratio","").isEmpty())return null;
        int realWidth=Math.min(initialWidth,initialHeight);
        int realHeight=Math.max(initialWidth,initialHeight);
        int rogWidth=SystemProperties.getInt("persist.sys.rog.width",0);
        int rogHeight=SystemProperties.getInt("persist.sys.rog.height",0);
        if(rogWidth<0 || rogHeight<0)return null;
        for(var row:geometry.rows()) {
            if(row.ownerUid()!=uid || row.name()==null || row.displayFrame()==null
                    || !row.name().contains(pkg) || !row.name().contains("SurfaceView["))
                return null; // Native contract violation, not complete-empty.
            var b=row.displayFrame();
            int width=rogWidth==0?realWidth:rogWidth;
            int height=rogWidth==0?realHeight:rogHeight;
            if(b.right()-b.left()>width || b.bottom()-b.top()>height) {
                width=realWidth;height=realHeight;
            }
            if(StockVideoFullscreenGeometry.isDefaultPhoneFullscreen(width,height,
                    b.left(),b.top(),b.right(),b.bottom()))return true;
        }
        return false; // Complete eligible enumeration, including COMPLETE_EMPTY.
    }
}
