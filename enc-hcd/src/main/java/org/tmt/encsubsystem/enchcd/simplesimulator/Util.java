package org.tmt.encsubsystem.enchcd.simplesimulator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class having common function
 */
public class Util {
    /**
     * This function round value of double to given decimal places.
     * @param value
     * @param places
     * @return
     */
    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static double diff(double v1, double v2, int places){
        return round(round(v1, places) - round(v2, places), places);
    }
}
