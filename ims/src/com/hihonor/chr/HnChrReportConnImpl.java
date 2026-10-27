// SPDX-License-Identifier: Apache-2.0
package com.hihonor.chr;

/** IMS-local absent optional CHR transport. No payload is retained or transmitted. */
public final class HnChrReportConnImpl {
    private static final HnChrReportConnImpl INSTANCE = new HnChrReportConnImpl();
    private HnChrReportConnImpl() {}

    public static HnChrReportConnImpl getInstance() {
        return INSTANCE;
    }

    public void handleChrLogItemFlow(int module, int cause, int level, String payload) {
        // Honor chr_report is unavailable. Stock also returns without reporting in this case.
        // Void conveys no delivery success; no IMS/radio state is changed.
    }
}
