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
 * Auxiliary static methods for dealing with math. 
 */
public class MathUtil {
   
   /**
    * Clamp double between a maximum and minimum value.
    * @param inputValue The value to clamp.
    * @param minParameter
    * @param maxParameter
    * @return the clamped value
    */
   public static double clamp(double inputValue, double minParameter, double maxParameter) {
      return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
   }

   /**
    * Clamp float between a maximum and minimum value.
    * @param inputValue The value to clamp.
    * @param minParameter
    * @param maxParameter
    * @return the clamped value
    */
   public static float clamp(float inputValue, float minParameter, float maxParameter) {
      return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
   }

   /**
    * Clamp int between a maximum and minimum value.
    * @param inputValue The value to clamp.
    * @param minParameter
    * @param maxParameter
    * @return the clamped value
    */
   public static int clamp(int inputValue, int minParameter, int maxParameter) {
      return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
   }

   /**
    * Test if the input value is in range between a minimum and maximum threshold (esclusive)
    * @param inputValue The value
    * @param minThreshold
    * @param maxThreshold
    * @return True if the value is between the thresholds, false otherwise
    */
   public static boolean inRange(double inputValue, double maxThreshold, double minThreshold) {
      return inputValue > minThreshold && inputValue < maxThreshold;
   }
    
    /**
     * Test if the difference equals or is smaller than the comparison value (absolute(!))
     * @param a The minuend
     * @param b The subtrahend
     * @param c Absolute value to compare the difference with
     * @return True if the difference is smaller or equals C, false otherwise
     */
   public static boolean equal(double a, double b, double c) {
      if (c < 0.0) return false;
      return Math.abs(a-b) <= c;
   }
   
   /**
     * Convenience method to calculate horizontal distance
     * @param xDistance
     * @param zDistance
     * @return the distance
     */
   public static double dist(double xDistance, double zDistance) {
      return Math.sqrt(xDistance * xDistance + zDistance * zDistance);
   }
   
   public static double square(double var0) {
      return var0 * var0;
   }
}