package org.lineageos.ldy_an00.display;
import java.util.Objects;
/** Source setBacklight guard: no prior input is UNKNOWN and raw0 is NO_EVENT, not false. */
final class StockBacklightThreshold {
    private Boolean above;
    boolean observe(Integer raw) {
        Boolean before=above;
        if(raw==null)above=null;
        else if(raw>0)above=raw>13990;
        return !Objects.equals(before,above);
    }
    Boolean above() { return above; }
}
