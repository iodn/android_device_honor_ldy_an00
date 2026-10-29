package org.lineageos.ldy_an00.display;

/** Owner-thread state only. The owner schedules returned tokens after DELAY_MILLIS. */
final class StockFirstScreenDelay {
    static final long DELAY_MILLIS = 400;
    enum Kind { ANIMATION_12, SWITCH_17 }
    static final class Timeout {
        private final StockFirstScreenDelay owner;
        final Kind kind;
        private Timeout(StockFirstScreenDelay owner, Kind kind) {
            this.owner = owner;
            this.kind = kind;
        }
    }
    record Update(boolean first, Timeout timeout) {}
    private final boolean mAnyHdrModeEnabled;
    private boolean mFirst;
    private boolean mClosed;
    private Timeout mAnimation;
    private Timeout mSwitch;

    StockFirstScreenDelay(boolean anyHdrModeEnabled) {
        mAnyHdrModeEnabled = anyHdrModeEnabled;
    }

    Update update(float currentSdr, float currentMain, float targetMain, float rate) {
        if (mClosed || !mAnyHdrModeEnabled) return new Update(mFirst, null);
        if (floatEquals(targetMain, 0f) || floatEquals(targetMain, -1f)) {
            mFirst = false;
            return new Update(false, null);
        }
        if (targetMain < 0f) return new Update(mFirst, null);
        if (floatEquals(currentSdr, 0f) || floatEquals(currentSdr, -1f)
                || floatEquals(currentMain, 0f) || floatEquals(currentMain, -1f)
                || Math.round(rate * 255f) == 0) {
            mFirst = true;
            mAnimation = new Timeout(this, Kind.ANIMATION_12);
            return new Update(true, mAnimation);
        }
        return new Update(mFirst, null);
    }

    Timeout switchScreenHdrDelay() {
        if (mClosed) return null;
        mFirst = true;
        mSwitch = new Timeout(this, Kind.SWITCH_17);
        return mSwitch;
    }

    boolean onTimeout(Timeout timeout) {
        if (mClosed || timeout == null || timeout.owner != this) return false;
        if (timeout.kind == Kind.ANIMATION_12) {
            if (timeout != mAnimation) return false;
            mAnimation = null;
        } else {
            if (timeout != mSwitch) return false;
            mSwitch = null;
        }
        mFirst = false;
        return true;
    }

    boolean isFirst() { return mFirst; }

    void close() {
        mClosed = true;
        mFirst = false;
        mAnimation = null;
        mSwitch = null;
    }

    private static boolean floatEquals(float a, float b) {
        if (a == b || (Float.isNaN(a) && Float.isNaN(b))) return true;
        if (Float.isNaN(a) || Float.isNaN(b)) return false;
        if (a > 0.05f) return Math.abs(a - b) < 0.0001f;
        return (float) Math.round(a < 0f ? a : a * 100000f)
                == (float) Math.round(b < 0f ? b : b * 100000f);
    }
}
