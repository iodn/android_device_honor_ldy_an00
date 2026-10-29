// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.cameratests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Range;
import android.util.Size;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Ordinary-app captures, distinct from the metadata-only compatibility test. */
@RunWith(AndroidJUnit4.class)
public final class LensHandoffCaptureTest {
    private static final int FRAMES = 12;
    private static final long SETTLE_MS = 500;
    private static final long STEP_TIMEOUT_SECONDS = 10;
    private volatile Step mStep;

    private static final class Step {
        final int index;
        final float requested;
        final LensHandoffSamples samples = new LensHandoffSamples(FRAMES);
        final CountDownLatch done = new CountDownLatch(1);
        volatile String failure;
        Step(int index, float requested) { this.index = index; this.requested = requested; }
        void fail(String reason) { failure = reason; done.countDown(); }
    }

    private void failStep(String reason) {
        Step step = mStep;
        if (step != null) step.fail(reason);
    }

    private static void report(JSONObject data) {
        Bundle status = new Bundle();
        status.putString("camera_lens_handoff_report", data.toString());
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    private static Size outputSize(StreamConfigurationMap map) {
        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
        assertTrue("No YUV output sizes", sizes != null && sizes.length > 0);
        return Arrays.stream(sizes).filter(s -> s.getWidth() > 0 && s.getHeight() > 0)
                .min(Comparator.<Size>comparingInt(s ->
                        s.getWidth() == 640 && s.getHeight() == 480 ? 0 : 1)
                        .thenComparingLong(s -> (long) s.getWidth() * s.getHeight()))
                .orElseThrow(() -> new AssertionError("No valid YUV size"));
    }

    @Test public void captureLogicalCameraZoomHandoff() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        assertTrue("Ordinary app UID required", Process.myUid() >= 10000);
        assertEquals("System app prohibited", 0, context.getApplicationInfo().flags
                & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP));
        assertEquals("Grant CAMERA on installation", PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(android.Manifest.permission.CAMERA));
        String id = InstrumentationRegistry.getArguments().getString("camera_id", "0");
        CameraManager manager = context.getSystemService(CameraManager.class);
        assertTrue("Requested logical camera is not visible to this app",
                Arrays.asList(manager.getCameraIdList()).contains(id));
        CameraCharacteristics chars = manager.getCameraCharacteristics(id);
        int[] capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        assertTrue("Requested camera is not logical", capabilities != null
                && Arrays.stream(capabilities).anyMatch(c -> c
                == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA));
        Range<Float> zoom = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        assertTrue("No advertised zoom-ratio range", zoom != null);
        List<Float> plan = LensHandoffSamples.zoomPlan(zoom.getLower(), zoom.getUpper());
        StreamConfigurationMap map = chars.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        assertTrue("No stream map", map != null);
        Size size = outputSize(map);
        Integer requestedFps = LensHandoffSamples.fixedFps(
                InstrumentationRegistry.getArguments().getString("ae_fps"));
        Range<Integer> aeRange = requestedFps == null ? null
                : new Range<>(requestedFps, requestedFps);
        if (aeRange != null) {
            Range<Integer>[] ranges = chars.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            assertTrue("Fixed AE FPS range not advertised",
                    ranges != null && Arrays.asList(ranges).contains(aeRange));
            long minimumDuration = map.getOutputMinFrameDuration(ImageFormat.YUV_420_888, size);
            assertTrue("Selected YUV size does not qualify requested fixed FPS",
                    LensHandoffSamples.supportsFixedFps(minimumDuration, requestedFps));
        }
        report(new JSONObject().put("phase", "configuration").put("camera_id", id)
                .put("physical_ids", new JSONArray(chars.getPhysicalCameraIds()))
                .put("zoom_plan", new JSONArray(plan)).put("size", size.toString())
                .put("requested_ae_fps", requestedFps == null ? JSONObject.NULL : requestedFps)
                .put("capture_kind", requestedFps == null ? "UNCONSTRAINED_PREVIEW"
                        : requestedFps == 30 ? "LENS_BASELINE_30" : "LOGICAL_CAMERA_60_DIAGNOSTIC")
                .put("frames_per_step", FRAMES).put("settle_ms", SETTLE_MS)
                .put("scope", "YUV preview capture only; no image-quality or video-mode verdict"));

        LensHandoffActivity activity = null;
        HandlerThread thread = new HandlerThread("LensHandoffCapture");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        AtomicBoolean closing = new AtomicBoolean();
        CompletableFuture<CameraDevice> opened = new CompletableFuture<>();
        CompletableFuture<CameraCaptureSession> configured = new CompletableFuture<>();
        CountDownLatch closed = new CountDownLatch(1);
        ImageReader reader = null;
        CameraDevice device = null;
        CameraCaptureSession session = null;
        AtomicReference<CameraDevice> cameraOwner = new AtomicReference<>();
        JSONObject finalReport = null;
        boolean openRequested = false;
        try {
            activity = (LensHandoffActivity) instrumentation.startActivitySync(
                    new Intent(context, LensHandoffActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            assertTrue("Activity did not resume", activity.resumed.await(5, TimeUnit.SECONDS));
            final LensHandoffActivity foregroundActivity = activity;
            reader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                    ImageFormat.YUV_420_888, 4);
            reader.setOnImageAvailableListener(source -> {
                try (Image image = source.acquireLatestImage()) {
                    Step step = mStep;
                    if (image != null && step != null
                            && step.samples.image(image.getTimestamp())) step.done.countDown();
                } catch (IllegalStateException error) {
                    if (!closing.get()) failStep("Image reader: " + error);
                }
            }, handler);
            openRequested = true;
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    cameraOwner.set(camera);
                    // Transfer ownership before checking teardown; otherwise an open
                    // callback can race between cleanup's flag and future checks.
                    boolean transferred = opened.complete(camera);
                    if (!transferred || closing.get()) camera.close();
                }
                @Override public void onDisconnected(CameraDevice camera) {
                    cameraOwner.set(camera);
                    opened.completeExceptionally(new AssertionError("Camera disconnected"));
                    failStep("Camera disconnected");
                    camera.close();
                }
                @Override public void onError(CameraDevice camera, int error) {
                    cameraOwner.set(camera);
                    opened.completeExceptionally(new AssertionError("Camera error " + error));
                    failStep("Camera error " + error);
                    camera.close();
                }
                @Override public void onClosed(CameraDevice camera) {
                    closed.countDown();
                    if (closing.get()) thread.quitSafely();
                }
            }, handler);
            device = opened.get(10, TimeUnit.SECONDS);
            device.createCaptureSession(new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    List.of(new OutputConfiguration(reader.getSurface())),
                    command -> handler.post(command), new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession value) {
                            boolean transferred = configured.complete(value);
                            if (!transferred || closing.get()) value.close();
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession value) {
                            configured.completeExceptionally(
                                    new AssertionError("Session configuration failed"));
                            value.close();
                        }
                    }));
            session = configured.get(10, TimeUnit.SECONDS);
            CameraCaptureSession.CaptureCallback callback = new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession value,
                        CaptureRequest request, TotalCaptureResult result) {
                    Step step = mStep;
                    if (step == null || !Integer.valueOf(step.index).equals(request.getTag())) return;
                    if (!foregroundActivity.foreground) {
                        step.fail("Activity left foreground"); return;
                    }
                    Long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
                    if (timestamp == null || timestamp <= 0) {
                        step.fail("No valid sensor timestamp"); return;
                    }
                    // Use a delta within the sensor clock, not callback arrival time.
                    if (!step.samples.afterSettling(timestamp, SETTLE_MS * 1_000_000L)) return;
                    Range<Integer> resultAeRange = result.get(
                            CaptureResult.CONTROL_AE_TARGET_FPS_RANGE);
                    LensHandoffSamples.Sample sample = new LensHandoffSamples.Sample(
                            timestamp, result.getFrameNumber(),
                            result.get(CaptureResult.CONTROL_ZOOM_RATIO),
                            result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID),
                            resultAeRange == null ? null : resultAeRange.getLower(),
                            resultAeRange == null ? null : resultAeRange.getUpper(),
                            result.get(CaptureResult.SENSOR_FRAME_DURATION));
                    if (step.samples.result(sample)) step.done.countDown();
                }
                @Override public void onCaptureFailed(CameraCaptureSession value,
                        CaptureRequest request, CaptureFailure failure) {
                    Step step = mStep;
                    if (step != null && Integer.valueOf(step.index).equals(request.getTag())) {
                        step.fail("Capture failed: reason=" + failure.getReason());
                    }
                }
            };
            Set<String> observedIds = new TreeSet<>();
            int missingIds = 0;
            int unconfirmedZoomFrames = 0;
            int unconfirmedAeRangeFrames = 0;
            for (int index = 0; index < plan.size(); index++) {
                assertTrue("Activity is not foreground", activity.foreground);
                Step step = new Step(index, plan.get(index));
                mStep = step;
                CaptureRequest.Builder request = device.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW);
                request.addTarget(reader.getSurface());
                request.set(CaptureRequest.CONTROL_ZOOM_RATIO, step.requested);
                if (aeRange != null) request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, aeRange);
                request.setTag(index);
                session.setRepeatingRequest(request.build(), callback, handler);
                boolean completed = step.done.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                JSONArray samples = new JSONArray();
                for (LensHandoffSamples.Sample sample : step.samples.samples()) {
                    if (sample.physicalId == null || sample.physicalId.isEmpty()) {
                        missingIds++;
                    } else {
                        observedIds.add(sample.physicalId);
                    }
                    if (sample.zoom == null || !Float.isFinite(sample.zoom)
                            || Math.abs(sample.zoom - step.requested)
                            > Math.max(0.001f, step.requested * 0.01f)) {
                        unconfirmedZoomFrames++;
                    }
                    if (requestedFps != null && (!requestedFps.equals(sample.aeFpsLower)
                            || !requestedFps.equals(sample.aeFpsUpper))) {
                        unconfirmedAeRangeFrames++;
                    }
                    samples.put(new JSONObject().put("sensor_timestamp_ns", sample.timestamp)
                            .put("frame", sample.frame)
                            .put("result_ae_fps_lower", sample.aeFpsLower == null
                                    ? JSONObject.NULL : sample.aeFpsLower)
                            .put("result_ae_fps_upper", sample.aeFpsUpper == null
                                    ? JSONObject.NULL : sample.aeFpsUpper)
                            .put("result_frame_duration_ns", sample.frameDuration == null
                                    ? JSONObject.NULL : sample.frameDuration)
                            .put("result_zoom", sample.zoom == null ? JSONObject.NULL : sample.zoom)
                            .put("active_physical_id",
                                    sample.physicalId == null ? JSONObject.NULL : sample.physicalId));
                }
                report(new JSONObject().put("phase", "step").put("index", index)
                        .put("requested_zoom", step.requested).put("completed", completed)
                        .put("failure", step.failure == null ? JSONObject.NULL : step.failure)
                        .put("samples", samples));
                assertTrue("Step timed out; inspect partial report", completed);
                assertTrue("Capture error: " + step.failure, step.failure == null);
                assertTrue("Insufficient timestamp-matched output frames", step.samples.isComplete());
                mStep = null;
            }
            String handoffResult;
            if (missingIds > 0) {
                handoffResult = "INCONCLUSIVE_MISSING_ACTIVE_PHYSICAL_ID";
            } else if (unconfirmedZoomFrames > 0) {
                handoffResult = "INCONCLUSIVE_ZOOM_RESULT";
            } else if (!chars.getPhysicalCameraIds().containsAll(observedIds)) {
                handoffResult = "INCONCLUSIVE_UNEXPECTED_ACTIVE_ID";
            } else if (observedIds.size() < 2) {
                handoffResult = "INCONCLUSIVE_NO_HANDOFF_OBSERVED";
            } else {
                handoffResult = "HANDOFF_OBSERVED_FOR_REPORTED_IDS";
            }
            finalReport = new JSONObject().put("phase", "complete")
                    .put("result", "CAPTURE_SWEEP_COMPLETED_REVIEW_PHYSICAL_IDS_AND_ZOOM")
                    .put("handoff_result", handoffResult)
                    .put("observed_physical_ids", new JSONArray(observedIds))
                    .put("missing_active_id_frames", missingIds)
                    .put("unconfirmed_zoom_frames", unconfirmedZoomFrames)
                    .put("unconfirmed_ae_range_frames", unconfirmedAeRangeFrames)
                    .put("cadence_result", requestedFps == null ? "UNCONSTRAINED_NOT_FPS_TESTED"
                            : unconfirmedAeRangeFrames > 0 ? "INCONCLUSIVE_AE_RANGE_RESULT"
                            : "AE_RANGE_CONFIRMED_REVIEW_ACTUAL_TIMESTAMPS")
                    .put("interpretation",
                            "Null/unchanged active ID does not prove all physical lenses work.");
        } finally {
            mStep = null;
            closing.set(true);
            if (session != null) session.close();
            CameraDevice owner = cameraOwner.get();
            if (owner != null) owner.close();
            if (openRequested && !opened.isDone()) {
                try {
                    opened.get(5, TimeUnit.SECONDS);
                } catch (ExecutionException | TimeoutException expected) {
                    // Teardown is classified below; a timeout does not cancel openCamera.
                }
            }
            // Observe completion before owner publication: completing the future
            // publishes the callback's preceding owner.set to this thread.
            boolean openTerminal = !openRequested || opened.isDone();
            owner = cameraOwner.get();
            if (owner != null) owner.close();
            boolean closeConfirmed = owner == null
                    ? !openRequested : closed.await(5, TimeUnit.SECONDS);
            boolean cleanupConfirmed = openTerminal && closeConfirmed;
            if (reader != null) reader.close();
            if (activity != null) {
                LensHandoffActivity value = activity;
                instrumentation.runOnMainSync(value::finish);
            }
            // A pending open or close keeps its executor alive for late cleanup.
            // onOpened closes a late device, and onClosed quits the thread.
            if (cleanupConfirmed) thread.quitSafely();
            thread.join(5000);
            cleanupConfirmed &= !thread.isAlive();
            report(new JSONObject().put("phase", "cleanup")
                    .put("result", cleanupConfirmed ? "CLEANUP_CONFIRMED" : "CLEANUP_UNKNOWN")
                    .put("open_terminal", openTerminal).put("close_confirmed", closeConfirmed)
                    .put("callback_thread_alive", thread.isAlive()));
            assertTrue("Camera cleanup unconfirmed; force-stop test package before retry",
                    cleanupConfirmed);
        }
        report(finalReport);
    }
}
