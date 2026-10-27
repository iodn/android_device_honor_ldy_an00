public final class AbsentPolicyTest {
    public static void main(String[] args) {
        if (hihonor.cust.HwCfgFilePolicy.getCloudCfgBoolValue(
                "/rtt/", "rsa_show_rtt_switch", false)) throw new AssertionError("false default");
        if (!hihonor.cust.HwCfgFilePolicy.getCloudCfgBoolValue(
                "/rtt/", "rsa_show_rtt_switch", true)) throw new AssertionError("true default");
        for (int slot = 0; slot < 2; slot++) {
            Boolean value = com.hihonor.android.cust.HwCfgFilePolicy.getValue(
                    "carrier_support_callforward_voice_video_separate", slot, Boolean.class);
            if (value != null) throw new AssertionError("invented carrier override");
            if (value != null && value.booleanValue()) throw new AssertionError("forced video forwarding");
        }
        System.out.println("ABSENT_POLICY_TESTS_PASS 6");
    }
}
