// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import java.util.ArrayDeque;
import java.util.ArrayList;

public final class PowerControllerTest {
    private static final class Backend implements PowerController.Backend {
        final ArrayList<int[]> requests = new ArrayList<>();
        int[] observed = {2600000, 2600000, 3200000};
        int reads;
        final ArrayList<Boolean> screenStates = new ArrayList<>();
        int failedScreenSendsRemaining;
        boolean failScreenSend;
        public void notifyInteractive(boolean interactive) throws Exception {
            screenStates.add(interactive);
            if (failScreenSend || failedScreenSendsRemaining-- > 0) {
                throw new Exception("Screen delivery unknown");
            }
        }
        int gpuLevels = -1;
        boolean failGpuRead;
        public int gpuLevelCount() throws Exception {
            if (failGpuRead) throw new Exception("GPU unavailable");
            return gpuLevels;
        }
        boolean failRead;
        boolean failSend;
        int failedSendsRemaining;
        boolean ignore;
        public int[] readLimits() throws Exception {
            reads++;
            if (failRead) throw new Exception("Backend unavailable");
            return observed.clone();
        }
        public void request(int[] limits) throws Exception {
            if (!PowerPolicy.validRequest(limits)) {
                throw new AssertionError("Invalid frequency request");
            }
            requests.add(limits.clone());
            if (failSend) throw new Exception("Delivery unknown");
            if (failedSendsRemaining > 0) {
                failedSendsRemaining--;
                throw new Exception("Delivery unknown");
            }
            if (!ignore) observed = PowerPolicy.released(limits)
                    ? new int[]{2600000, 2600000, 3200000}
                    : new int[]{limits[0], limits[1], limits[2]};
        }
    }

    private static final class Fixture {
        final Backend backend = new Backend();
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final PowerController controller = new PowerController(backend,
                (task, delay) -> queue.add(task), (message, error) -> {});
        void drain() {
            int bound = 30;
            while (!queue.isEmpty()) {
                if (--bound == 0) throw new AssertionError("Unbounded retry");
                queue.remove().run();
            }
        }
        void count(int expected) {
            if (backend.requests.size() != expected) throw new AssertionError("Wrong request count");
        }
        void releaseLast() {
            if (!PowerPolicy.released(backend.requests.get(backend.requests.size() - 1))) {
                throw new AssertionError("Expected release");
            }
        }
    }

    public static void main(String[] args) {
        Fixture f = new Fixture();
        f.controller.update(true, true, false);
        f.controller.update(false, true, false);
        f.drain(); // Old saver verification cannot undo a newer wake/release.
        f.count(2);
        f.releaseLast();
        f.controller.update(false, true, false);
        f.count(2); // Unchanged state does not rewrite limits.

        f = new Fixture();
        f.controller.update(true, false, false);
        f.controller.update(true, false, true); // Call/camera opens while asleep.
        f.drain();
        f.count(2);
        f.releaseLast();

        f = new Fixture();
        f.backend.ignore = true;
        f.controller.update(true, true, false);
        f.drain(); // Three stale observations must release, then suppress the failed policy.
        f.count(2);
        f.releaseLast();
        f.controller.update(true, true, false);
        f.count(2);

        f = new Fixture();
        f.backend.failRead = true;
        f.controller.update(true, true, false);
        f.drain();
        f.count(0); // An uninitialized getter cannot authorize tuning.
        if (f.backend.reads != 4) throw new AssertionError("Expected initial read and three retries");
        f.controller.update(false, true, false);
        f.drain();
        f.count(1); // A broken getter must not block release.
        f.releaseLast();

        f = new Fixture();
        f.controller.update(true, true, false);
        f.controller.invalidate(); // HAL restart invalidates every old verification.
        f.controller.update(false, true, false);
        f.drain();
        f.count(2);
        f.releaseLast();

        f = new Fixture();
        f.backend.failSend = true;
        f.controller.update(true, true, false);
        f.drain();
        f.count(5); // Unknown delivery and failed releases have bounded retries.
        f.releaseLast();

        f = new Fixture();
        f.controller.update(true, true, false);
        f.drain();
        f.backend.failedSendsRemaining = 2;
        f.controller.update(false, true, false);
        f.drain(); // Release recovers from a transport outage without a new screen event.
        f.count(4);
        f.releaseLast();

        f = new Fixture();
        f.backend.failedSendsRemaining = 2;
        f.controller.update(true, true, false);
        f.controller.update(false, false, false);
        f.drain(); // The old release retry cannot clear a newer screen-off ceiling.
        f.count(3);
        if (PowerPolicy.released(f.backend.requests.get(2))) {
            throw new AssertionError("Stale release replaced the newer policy");
        }

        f = new Fixture();
        f.backend.failSend = true;
        f.controller.update(false, true, false);
        f.drain();
        f.count(5);
        f.backend.failSend = false;
        f.controller.update(false, true, false);
        f.drain(); // Exhausting release retries must not block a later recovery event.
        f.count(6);
        f.releaseLast();

        f = new Fixture();
        f.backend.failRead = true;
        f.controller.update(true, true, false);
        f.controller.update(false, true, false);
        f.drain(); // Stale initialization retry cannot reapply saver after disabling it.
        f.count(1);
        f.releaseLast();
        f = new Fixture();
        f.backend.gpuLevels = 20;
        f.controller.update(true, true, false);
        f.drain();
        if (f.backend.requests.get(0)[3] != 10) throw new AssertionError("Awake GPU cap absent");
        f.controller.update(true, false, false);
        f.drain();
        if (f.backend.requests.get(1)[3] != 12) throw new AssertionError("Asleep GPU cap absent");
        f.controller.update(true, false, true);
        f.drain();
        f.releaseLast(); // Camera/call protection removes CPU and GPU votes.
        f.backend.failGpuRead = true;
        f.controller.update(true, true, false);
        f.drain();
        if (f.backend.requests.get(3)[3] != -1) throw new AssertionError("GPU failure retained cap");
        f.backend.failGpuRead = false;
        f.controller.update(true, true, false);
        f.drain();
        if (f.backend.requests.get(4)[3] != 10) throw new AssertionError("GPU did not recover");
        f.backend.ignore = true;
        f.backend.observed = new int[]{2600000, 2600000, 3200000};
        f.controller.update(true, false, false);
        f.drain();
        f.releaseLast(); // Failed CPU verification also releases any GPU vote.
        f = new Fixture();
        f.controller.update(false, true, false);
        f.controller.update(true, true, false);
        f.controller.update(false, false, false);
        f.drain();
        if (!f.backend.screenStates.equals(java.util.List.of(true, false))) {
            throw new AssertionError("Duplicate or missing screen notification");
        }

        f = new Fixture();
        f.backend.failedScreenSendsRemaining = 1;
        f.controller.update(false, false, false);
        f.controller.update(false, true, false);
        f.drain();
        if (!f.backend.screenStates.equals(java.util.List.of(false, true))) {
            throw new AssertionError("Stale screen retry changed the newer state");
        }
        f.controller.invalidate();
        f.controller.update(false, true, false);
        f.drain();
        if (!f.backend.screenStates.equals(java.util.List.of(false, true, true))) {
            throw new AssertionError("HAL restart did not replay screen state");
        }

        f = new Fixture();
        f.backend.failScreenSend = true;
        f.controller.update(false, false, false);
        f.drain();
        if (f.backend.screenStates.size() != 4) throw new AssertionError("Screen retries unbounded");
        f.backend.failScreenSend = false;
        f.controller.update(false, false, false);
        f.drain();
        if (f.backend.screenStates.size() != 5 || f.backend.screenStates.get(4)) {
            throw new AssertionError("Screen notification did not recover");
        }

        f = new Fixture();
        f.backend.failedScreenSendsRemaining = 1;
        f.controller.update(false, false, false);
        f.drain();
        if (!f.backend.screenStates.equals(java.util.List.of(false, false))) {
            throw new AssertionError("Transient failure was not retried");
        }
        f = new Fixture();
        f.controller.release();
        f.drain();
        f.releaseLast();
        if (!f.backend.screenStates.isEmpty()) {
            throw new AssertionError("Initial release invented a screen state");
        }
        f.controller.update(false, false, true);
        f.drain();
        if (!f.backend.screenStates.equals(java.util.List.of(false))) {
            throw new AssertionError("First real screen observation was lost");
        }
        System.out.println("POWER_CONTROLLER_TESTS_OK");
    }
}
