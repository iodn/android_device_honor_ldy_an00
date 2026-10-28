// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.gputests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class GpuCompatibilityTest {
    private static native String runOpenClProbe();

    @Test
    public void publicOpenClGpuComputation() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("The probe must run as an application UID", Process.myUid() >= 10000);
        assertEquals("The probe must be installed as an ordinary application", 0,
                context.getApplicationInfo().flags
                        & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP));
        System.loadLibrary("ldy_an00_opencl_probe");
        Log.i("LdyAn00GpuTest", runOpenClProbe());
    }
}
