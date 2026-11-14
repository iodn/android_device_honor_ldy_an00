#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise the actual service lifecycle with host Android fakes; no device access."""
from pathlib import Path
import subprocess
import sys
import tempfile

POWER = Path(__file__).resolve().parents[1]
PACKAGE = "org/lineageos/ldy_an00/power"
STUBS = {
    "android/Manifest.java": """package android;
public final class Manifest { public static final class permission {
public static final String CAMERA_OPEN_CLOSE_LISTENER = "camera"; } }""",
    "android/content/Context.java": """package android.content;
import java.util.HashMap;
public class Context {
public static final int RECEIVER_NOT_EXPORTED = 4;
public final HashMap<Class<?>,Object> services = new HashMap<>();
public BroadcastReceiver receiver;
public <T> T getSystemService(Class<T> type) { return type.cast(services.get(type)); }
public int checkSelfPermission(String permission) { return 0; }
public Intent registerReceiver(BroadcastReceiver r, IntentFilter f, String p,
android.os.Handler h, int flags) { receiver=r; return null; }
}""",
    "android/content/BroadcastReceiver.java": """package android.content;
public abstract class BroadcastReceiver {
public abstract void onReceive(Context context, Intent intent);
}""",
    "android/content/Intent.java": """package android.content;
public class Intent {
public static final String ACTION_SCREEN_ON="on", ACTION_SCREEN_OFF="off";
}""",
    "android/content/IntentFilter.java": """package android.content;
public class IntentFilter { public void addAction(String action) {} }""",
    "android/content/pm/PackageManager.java": """package android.content.pm;
public final class PackageManager { public static final int PERMISSION_GRANTED=0; }""",
    "android/hardware/camera2/CameraManager.java": """package android.hardware.camera2;
public class CameraManager {
public boolean failRegistration;
public static class AvailabilityCallback {
public void onCameraOpened(String id,String pkg) {}
public void onCameraClosed(String id) {}
}
public void registerAvailabilityCallback(AvailabilityCallback callback,android.os.Handler handler) {
if(failRegistration)throw new IllegalStateException("camera observer unavailable");
}
}""",
    "android/media/AudioManager.java": """package android.media;
import java.util.concurrent.Executor;
public class AudioManager {
public static final int MODE_NORMAL=0;
public boolean failMode, failRegistration;
public int mode;
public interface OnModeChangedListener { void onModeChanged(int mode); }
public int getMode() {
if(failMode)throw new IllegalStateException("audio state unavailable");
return mode;
}
public void addOnModeChangedListener(Executor executor,OnModeChangedListener listener) {
if(failRegistration)throw new IllegalStateException("audio observer unavailable");
}
}""",
    "android/os/HandlerThread.java": """package android.os;
public class HandlerThread {
public HandlerThread(String name) {}
public void start() {}
public Object getLooper() { return this; }
}""",
    "android/os/Handler.java": """package android.os;
public class Handler {
public Handler(Object looper) {}
public boolean post(Runnable task) { task.run(); return true; }
public boolean postDelayed(Runnable task,long delay) { return true; }
}""",
    "android/os/PowerManager.java": """package android.os;
public class PowerManager {
public static final String ACTION_POWER_SAVE_MODE_CHANGED="saver";
public boolean interactive, saver, failInteractive, failSaver;
public boolean isInteractive() {
if(failInteractive)throw new IllegalStateException("screen state unavailable");
return interactive;
}
public boolean isPowerSaveMode() {
if(failSaver)throw new IllegalStateException("saver state unavailable");
return saver;
}
}""",
    "android/util/ArraySet.java": """package android.util;
public class ArraySet<T> extends java.util.HashSet<T> {}""",
    "android/util/Slog.java": """package android.util;
public final class Slog {
public static void i(String tag,String message) {}
public static void w(String tag,String message,Throwable error) {}
public static void e(String tag,String message) {}
public static void e(String tag,String message,Throwable error) {}
}""",
    "com/android/server/SystemService.java": """package com.android.server;
public class SystemService {
public static final int PHASE_BOOT_COMPLETED=1000;
private final android.content.Context context;
public SystemService(android.content.Context context) { this.context=context; }
public android.content.Context getContext() { return context; }
public void onStart() {}
public void onBootPhase(int phase) {}
}""",
    PACKAGE + "/StockUniPerf.java": """package org.lineageos.ldy_an00.power;
import java.util.ArrayList;
final class StockUniPerf implements PowerController.Backend {
static StockUniPerf last;
final ArrayList<Boolean> screens=new ArrayList<>();
final ArrayList<int[]> requests=new ArrayList<>();
final Runnable death;
StockUniPerf(Runnable death) { this.death=death; last=this; }
public int[] readLimits() { return new int[]{2600000,2600000,3200000}; }
public int gpuLevelCount() { return 20; }
public void notifyInteractive(boolean interactive) { screens.add(interactive); }
public void request(int[] limits) { requests.add(limits.clone()); }
}""",
    PACKAGE + "/DevicePowerServiceHostTest.java": """package org.lineageos.ldy_an00.power;
import android.content.*;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.PowerManager;
import com.android.server.SystemService;
import java.util.List;

public final class DevicePowerServiceHostTest {
static int checks;
static void check(boolean value,String message) {
checks++; if(!value)throw new AssertionError(message);
}
static final class Fixture {
final Context context=new Context();
final PowerManager power=new PowerManager();
final AudioManager audio=new AudioManager();
final CameraManager camera=new CameraManager();
DevicePowerService service;
StockUniPerf backend;
void start(boolean hasPower,boolean hasAudio,boolean hasCamera) {
if(hasPower)context.services.put(PowerManager.class,power);
if(hasAudio)context.services.put(AudioManager.class,audio);
if(hasCamera)context.services.put(CameraManager.class,camera);
service=new DevicePowerService(context);
service.onStart();backend=StockUniPerf.last;
service.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
}
void observe() {
check(context.receiver!=null,"Missing screen receiver");
context.receiver.onReceive(context,new Intent());
}
void released() {
check(!backend.requests.isEmpty(),"No initial release");
check(PowerPolicy.released(backend.requests.get(backend.requests.size()-1)),
"Workload failure retained frequency limits");
}
void screens(Boolean... values) {
check(backend.screens.equals(List.of(values)),"Wrong screen admission state: "+backend.screens);
}
}
public static void main(String[] args) {
Fixture f=new Fixture();
f.start(true,false,true);f.screens(false);f.released();
f.power.interactive=true;f.observe();f.screens(false,true);f.released();

f=new Fixture();f.start(true,true,false);
f.screens(false);f.released();
f.power.interactive=true;f.observe();f.screens(false,true);f.released();

f=new Fixture();f.power.interactive=true;f.power.saver=true;
f.start(true,true,true);
f.screens(true);
check(!PowerPolicy.released(f.backend.requests.get(f.backend.requests.size()-1)),
"Ready observers did not permit saver policy");
f.audio.failMode=true;f.power.interactive=false;f.observe();
f.screens(true,false);f.released();
f.audio.failMode=false;f.power.interactive=true;f.observe();
f.screens(true,false,true);
check(!PowerPolicy.released(f.backend.requests.get(f.backend.requests.size()-1)),
"Workload recovery did not restore the current saver policy");

f=new Fixture();f.power.interactive=true;f.power.saver=true;
f.start(true,true,true);
f.power.failSaver=true;f.power.interactive=false;f.observe();
f.screens(true,false);f.released();

f=new Fixture();f.power.interactive=true;f.power.saver=true;
f.start(true,true,true);
f.power.failInteractive=true;f.observe();
f.screens(true);f.released(); // An unavailable screen source must not invent sleep.

f=new Fixture();f.camera.failRegistration=true;f.power.saver=true;
f.start(true,true,true);f.screens(false);f.released();
f.power.interactive=true;f.observe();f.screens(false,true);f.released();

f=new Fixture();f.audio.failRegistration=true;f.power.saver=true;
f.start(true,true,true);f.screens(false);f.released();
f.power.interactive=true;f.observe();f.screens(false,true);f.released();

f=new Fixture();f.start(false,true,true);f.screens();f.released();

f=new Fixture();f.start(true,false,true);f.screens(false);
f.backend.death.run();f.screens(false,false);f.released();
System.out.println("DEVICE_POWER_SERVICE_HOST_OK checks="+checks);
}
}""",
}


def main():
    service = (Path(sys.argv[1]) if len(sys.argv) > 1 else
               POWER / "src" / PACKAGE / "DevicePowerService.java")
    with tempfile.TemporaryDirectory(prefix="ldy-power-service-") as directory:
        root = Path(directory)
        sources = []
        for name, source in STUBS.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source + "\n")
            sources.append(path)
        sources += [POWER / "src" / PACKAGE / (name + ".java")
                    for name in ["PowerPolicy", "PowerController"]]
        sources.append(service)
        classes = root / "classes"
        classes.mkdir()
        subprocess.run(["javac", "--release", "17", "-d", str(classes),
                        *map(str, sources)], check=True)
        subprocess.run(["java", "-cp", str(classes),
                        "org.lineageos.ldy_an00.power.DevicePowerServiceHostTest"], check=True)


if __name__ == "__main__":
    main()
