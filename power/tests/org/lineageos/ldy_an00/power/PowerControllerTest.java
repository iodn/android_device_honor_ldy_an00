// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import java.util.ArrayDeque;
import java.util.ArrayList;

public final class PowerControllerTest {
    private static final class Backend implements PowerController.Backend {
        final ArrayList<int[]> requests = new ArrayList<>();
        int[] observed = {2700000, 3500000, 4210000, 0};
        boolean failRead;
        boolean failSend;
        boolean ignore;
        public int[] readLimits() throws Exception {
            if (failRead) throw new Exception("Backend unavailable");
            return observed.clone();
        }
        public void request(int[] limits) throws Exception {
            requests.add(limits.clone());
            if (failSend) throw new Exception("Delivery unknown");
            if (!ignore) observed = PowerPolicy.released(limits)
                    ? new int[]{2700000, 3500000, 4210000, 0} : limits.clone();
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
        f.count(2); // Unknown setter delivery causes one best-effort release.
        f.releaseLast();

        f = new Fixture();
        f.backend.failRead = true;
        f.controller.update(true, true, false);
        f.controller.update(false, true, false);
        f.drain(); // Stale initialization retry cannot reapply saver after disabling it.
        f.count(1);
        f.releaseLast();
        System.out.println("POWER_CONTROLLER_TESTS_OK");
    }
}
