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
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.util.Size;

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
        result.put("focal_lengths_mm", jsonValue(chars.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)));
        result.put("zoom_ratio_range", jsonValue(chars.get(
                CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)));
        result.put("ae_target_fps_ranges", jsonValue(chars.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)));
        DynamicRangeProfiles profiles = chars.get(
                CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
        JSONArray dynamicRanges = new JSONArray();
        if (profiles != null) {
            for (long profile : profiles.getSupportedProfiles()) {
                dynamicRanges.put(new JSONObject().put("profile", profile)
                        .put("capture_request_constraints",
                                new JSONArray(profiles.getProfileCaptureRequestConstraints(profile)))
                        .put("extra_latency", profiles.isExtraLatencyPresent(profile)));
            }
        }
        result.put("dynamic_range_profiles", profiles == null ? JSONObject.NULL : dynamicRanges);
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
            JSONArray videoTiming = new JSONArray();
            Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);
            if (videoSizes != null) {
                for (Size size : videoSizes) {
                    videoTiming.put(new JSONObject().put("size", size.toString())
                            .put("minimum_frame_duration_ns",
                                    map.getOutputMinFrameDuration(MediaRecorder.class, size)));
                }
            }
            result.put("video_timing", videoTiming);
            JSONArray highSpeed = new JSONArray();
            for (Size size : map.getHighSpeedVideoSizes()) {
                highSpeed.put(new JSONObject().put("size", size.toString())
                        .put("fps_ranges", jsonValue(map.getHighSpeedVideoFpsRangesFor(size))));
            }
            result.put("high_speed_video", highSpeed);
        }
        JSONObject vendor = new JSONObject();
        CameraCharacteristics.Key<byte[]> extensionTypeKey = new CameraCharacteristics.Key<>(
                "com.hihonor.device.capabilities.extensionType", byte[].class);
        CameraCharacteristics.Key<int[]> cameraIdInfoKey = new CameraCharacteristics.Key<>(
                "com.hihonor.device.capabilities.cameraIdCustomInfo", int[].class);
        readVendorCharacteristic(vendor, chars, extensionTypeKey);
        readVendorCharacteristic(vendor, chars, cameraIdInfoKey);
        JSONObject advertised = new JSONObject().put(extensionTypeKey.getName(), false)
                .put(cameraIdInfoKey.getName(), false);
        for (CameraCharacteristics.Key<?> key : chars.getKeys()) {
            if (advertised.has(key.getName())) advertised.put(key.getName(), true);
        }
        result.put("honor_extension_key_advertisement", advertised);
        result.put("honor_extension_metadata", vendor);
        return result;
    }

    private static <T> void readVendorCharacteristic(JSONObject result,
            CameraCharacteristics chars, CameraCharacteristics.Key<T> key) throws Exception {
        Object value;
        try {
            value = chars.get(key);
        } catch (IllegalArgumentException error) {
            result.put(key.getName(), new JSONObject().put("status", "KEY_QUERY_REJECTED")
                    .put("exception", error.toString()));
            return;
        }
        result.put(key.getName(), jsonValue(value));
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
