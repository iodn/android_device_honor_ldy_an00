package org.lineageos.ldy_an00.display;

/** Stock APS's default non-foldable phone branch, after screen-domain qualification.
 * This does not classify video, select packages/displays, or supply current geometry.
 */
public final class StockVideoFullscreenGeometry {
    private StockVideoFullscreenGeometry() {}

    public static boolean isDefaultPhoneFullscreen(int screenWidth, int screenHeight,
            int left, int top, int right, int bottom) {
        if (screenWidth == 0 || screenHeight == 0) return false;
        int width = right - left;
        int height = bottom - top;
        int twiceCenterX = right + left;
        int twiceCenterY = bottom + top;
        return width > screenWidth * 7 / 10 && height >= screenHeight * 4 / 5
                && twiceCenterX >= screenWidth * 7 / 10
                && twiceCenterX <= screenWidth * 6 / 5
                && twiceCenterY >= screenHeight * 4 / 5
                && twiceCenterY <= screenHeight * 6 / 5;
    }
}
