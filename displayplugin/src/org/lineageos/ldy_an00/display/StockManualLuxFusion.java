package org.lineageos.ldy_an00.display;

/** Lux-only projection of the selected hub profile; acquisition owns averaging and cadence. */
final class StockManualLuxFusion {
    record Parameters(float floor, float dark, float dark1, float dark2, float dark3,
            float dark4, float delta, float delta1, float delta2, float delta3, float ratio) {}

    record Result(int lux, float backFiltered) {}

    private final Parameters p;

    StockManualLuxFusion(Parameters parameters) {
        p = parameters;
    }

    static int frontLux(float value) {
        return (int) (value + .5f);
    }

    static int backLux(float value) {
        return Math.max(0, (int) (value + .5f));
    }

    Result fuse(int frontAverage, float backFiltered, boolean backEnabled,
            boolean cameraConnected, boolean torchOn) {
        float front = frontAverage;
        float back = backFiltered;
        if (Float.compare(front, back) >= 0 || torchOn || !backEnabled || !cameraConnected) {
            return new Result(frontAverage, back);
        }
        float delta = back - front;
        float relative = (((back - front) * front) / back) * p.ratio();
        if (front >= 0 && front <= p.dark()) {
            delta = p.delta() + (back > p.floor() ? 1 : 0);
        } else if (Float.compare(front, p.dark1()) <= 0) {
            // Stock uses the threshold here, rather than p.delta(), as the lower ordinate.
            delta = ((front - p.dark()) * (p.delta1() - p.dark()))
                    / (p.dark1() - p.dark()) + p.dark();
        } else if (Float.compare(front, p.dark2()) <= 0) {
            delta = ((front - p.dark1()) * (p.delta2() - p.delta1()))
                    / (p.dark2() - p.dark1()) + p.delta1();
        } else if (Float.compare(front, p.dark3()) <= 0) {
            delta = ((front - p.dark2()) * (p.delta3() - p.delta2()))
                    / (p.dark3() - p.dark2()) + p.delta2();
        } else if (Float.compare(front, p.dark4()) <= 0) {
            delta = relative;
        }
        float floor = front + delta;
        if (back >= floor) back = floor;
        return new Result(Math.max((int) front, (int) back), back);
    }
}
