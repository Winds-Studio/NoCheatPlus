/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.utilities.math;

/**
 * Math utilities from NMS/client
 */
public class VanillaMath {
   
    private static final int SIN_MASK = 4095;

    private static final int SIN_COUNT = 4096;

    public static final float PI = (float)Math.PI;

    public static final float PI2 = ((float)Math.PI * 2F);

    public static final float PId2 = ((float)Math.PI / 2F);

    private static final float radFull = ((float)Math.PI * 2F);

    private static final float degFull = 360.0F;

    private static final float radToIndex = 651.8986F;

    private static final float degToIndex = 11.377778F;

    public static final float deg2Rad = 0.017453292F;

    private static final float[] SIN_FAST = new float[4096]; // Uh, oh... Optifine might be a problem here.

   /**
    * A table of sin values computed from 0 (inclusive) to 2*pi (exclusive), with steps of 2*PI / 65536.
    */
    private static float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; ++i) {
            SIN[i] = (float) Math.sin((double) i * 3.141592653589793D * 2.0D / 65536.0D);
        }
    }

    public static float sin(float var0) {
      return SIN[(int)(var0 * 10430.378F) & '\uffff'];
    }

    public static float cos(float var0) {
      return SIN[(int)(var0 * 10430.378F + 16384.0F) & '\uffff'];
    }
}
