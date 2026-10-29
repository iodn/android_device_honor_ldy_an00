#include "PolicyBatch.h"
#include "ReporterValidity.h"
#include <cassert>
#include <climits>
#include <iostream>
using namespace honor::ldy_an00;
int main() {
    PolicyBatch b;
    assert(!EncodePolicyBatch(b));
    b.startedMilliseconds = 20; b.completedMilliseconds = 19;
    assert(!EncodePolicyBatch(b));
    b.completedMilliseconds = 20;
    assert(*EncodePolicyBatch(b) == "4:0:20:20:x:x:x:x");
    b.back = 40000; b.usb = 24000; b.battery = 24100; b.anyReporterInvalid = false;
    assert(*EncodePolicyBatch(b) == "4:0:20:20:0:40000:24000:24100");
    b.anyReporterInvalid = true;
    assert(*EncodePolicyBatch(b) == "4:0:20:20:1:40000:24000:24100");
    b.sequence = UINT32_MAX;
    b.startedMilliseconds = INT64_MAX; b.completedMilliseconds = INT64_MAX;
    b.back = INT32_MIN; b.usb = INT32_MIN; b.battery = INT32_MIN;
    auto encoded = *EncodePolicyBatch(b);
    assert(encoded == "4:4294967295:9223372036854775807:9223372036854775807:1:-2147483648:-2147483648:-2147483648");
    assert(encoded.size() == 90 && encoded.size() + 1 <= 92);
    ReporterValidityInputs in;
    AuxiliaryReadings aux;
    assert(!AnyReporterSensorInvalid(in, aux).has_value());
    for (auto& r : in.ntc) r = 25000;
    for (auto& r : in.shell) r = 25000;
    for (auto& r : aux.values) r = 25000;
    aux.battery = 25000; aux.pa2DefinitelyAbsent = true;
    assert(AnyReporterSensorInvalid(in, aux) == false);
    // Every actuallyconsumed rawfield is required to conclude ORfalse.
    {
        auto test = aux; test.battery.reset();
        assert(!AnyReporterSensorInvalid(in, test).has_value());
    }
    for (size_t i : {0u, 2u, 3u, 4u}) {
        auto test = in; test.ntc[i].reset();
        assert(!AnyReporterSensorInvalid(test, aux).has_value());
    }
    for (size_t i = 0; i < in.shell.size(); ++i) {
        auto test = in; test.shell[i].reset();
        assert(!AnyReporterSensorInvalid(test, aux).has_value());
    }
    for (size_t i = 0; i < aux.values.size(); ++i) {
        auto test = aux; test.values[i].reset();
        assert(!AnyReporterSensorInvalid(in, test).has_value());
    }
    {
        auto test = in; test.ntc[1].reset(); // pa_3 is NOT oneofthe14reporterIDs.
        assert(AnyReporterSensorInvalid(test, aux) == false);
    }
    aux.values[3] = -1000;
    assert(AnyReporterSensorInvalid(in, aux) == true); // LTE59stockminimum0.
    aux.values[3] = -999;
    assert(AnyReporterSensorInvalid(in, aux) == false); // Trunctowardzero.
    aux.values[3] = 25000;
    in.ntc[4] = -1000;
    assert(AnyReporterSensorInvalid(in, aux) == true); // NRPA60stockminimum0.
    in.ntc[4] = 25000;
    aux.values[0].reset();
    assert(!AnyReporterSensorInvalid(in, aux).has_value());
    in.ntc[2] = 81000;
    assert(AnyReporterSensorInvalid(in, aux) == true); // Invalid dominates unknown.
    in.ntc[2] = -30999;
    assert(!AnyReporterSensorInvalid(in, aux).has_value());
    aux.values[0] = 25000;
    assert(AnyReporterSensorInvalid(in, aux) == false); // Truncatedwhole-30 isvalid.
    in.ntc[2] = -31000;
    assert(AnyReporterSensorInvalid(in, aux) == true);
    in.ntc[2] = 25000; aux.pa2DefinitelyAbsent = false;
    assert(!AnyReporterSensorInvalid(in, aux).has_value()); // Presentpa2 needsrealproof.
    aux.pa2DefinitelyAbsent = true; aux.battery = -20999;
    assert(AnyReporterSensorInvalid(in, aux) == false);
    aux.battery = -21000;
    assert(AnyReporterSensorInvalid(in, aux) == true);
    std::cout << "PASS serializer90bytes+NUL91; tri-state whole-validity boundaries\n";
}
