// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.cameratests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Array;

@RunWith(AndroidJUnit4.class)
public final class CameraCompatibilityTest {
    private static Object jsonValue(Object value) throws Exception {
        if (value == null) return JSONObject.NULL;
        if (value.getClass().isArray()) {
            JSONArray array = new JSONArray();
            for (int i = 0; i < Array.getLength(value); i++) {
                array.put(jsonValue(Array.get(value, i)));
            }
            return array;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return value.toString();
    }

    private static JSONObject characteristics(String id, CameraCharacteristics chars)
            throws Exception {
        JSONObject result = new JSONObject().put("id", id);
        result.put("lens_facing", jsonValue(chars.get(CameraCharacteristics.LENS_FACING)));
        result.put("capabilities", jsonValue(chars.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)));
        result.put("physical_ids", new JSONArray(chars.getPhysicalCameraIds()));
        result.put("ois_modes", jsonValue(chars.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)));
        result.put("video_stabilization_modes", jsonValue(chars.get(
                CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)));
        StreamConfigurationMap map = chars.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map != null) {
            result.put("jpeg_sizes", jsonValue(map.getOutputSizes(ImageFormat.JPEG)));
            result.put("video_sizes", jsonValue(map.getOutputSizes(MediaRecorder.class)));
        }
        JSONObject vendor = new JSONObject();
        for (CameraCharacteristics.Key<?> key : chars.getKeys()) {
            if (key.getName().equals("com.hihonor.device.capabilities.extensionType")
                    || key.getName().equals(
                            "com.hihonor.device.capabilities.cameraIdForExtension")) {
                vendor.put(key.getName(), jsonValue(chars.get(key)));
            }
        }
        result.put("honor_extension_metadata", vendor);
        return result;
    }

    private static void report(JSONObject result) {
        Bundle status = new Bundle();
        status.putString("camera_capability_report", result.toString());
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    @Test
    public void reportApplicationCameraCapabilities() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("The probe must run as an application UID", Process.myUid() >= 10000);
        assertEquals("The probe must be an ordinary application", 0,
                context.getApplicationInfo().flags
                        & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP));
        assertEquals("Grant CAMERA when installing the test APK", PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(android.Manifest.permission.CAMERA));
        CameraManager manager = context.getSystemService(CameraManager.class);
        String[] ids = manager.getCameraIdList();
        assertTrue("No cameras visible to an ordinary app", ids.length > 0);
        int advertisedExtensions = 0;
        for (String id : ids) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            JSONObject result = characteristics(id, chars);
            JSONArray extensions = new JSONArray(manager.getCameraExtensionCharacteristics(id)
                    .getSupportedExtensions());
            result.put("extensions", extensions);
            advertisedExtensions += extensions.length();
            result.put("scope", "logical_camera_availability_only");
            report(result);
            for (String physical : chars.getPhysicalCameraIds()) {
                report(characteristics(physical, manager.getCameraCharacteristics(physical))
                        .put("logical_parent", id).put("scope", "physical_camera_metadata_only"));
            }
        }
        report(new JSONObject().put("camera_count", ids.length)
                .put("extension_result", advertisedExtensions == 0
                        ? "NOT_ADVERTISED" : "ADVERTISED_NOT_CAPTURE_TESTED"));
    }
}
