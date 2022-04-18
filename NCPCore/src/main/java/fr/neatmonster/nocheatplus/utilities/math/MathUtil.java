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
 * Auxiliary static methods for dealing with math operations. 
 */
public class MathUtil {
   
   /**
    * Clamp double between a maximum and minimum value.
    * @param inputValue The value to clamp.
    * @param minParameter
    * @param maxParameter
    */
   public static double clampDouble(double inputValue, double minParameter, double maxParameter) {
       return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
   }

   /**
    * Clamp float between a maximum and minimum value.
    * @param inputValue The value to clamp.
    * @param minParameter
    * @param maxParameter
    */
   public static float clampFloat(float inputValue, float minParameter, float maxParameter) {
       return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
   }

   /**
    * Clamp int between a maximum and minimum value.
    * @param inputValue The value to clamp.
    * @param minParameter
    * @param maxParameter
    */
   public static int clampInt(int inputValue, int minParameter, int maxParameter) {
       return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
   }

   /**
    * Test if the input value in range between a minimum and maximum threshold
    * @param inputValue The value to clamp.
    * @param minThreshold
    * @param maxThreshold
    * @param exclusive If thresholds should not be considered.
    */
    public static boolean isInRange(double inputValue, double maxThreshold, double minThreshold, boolean exclusive) {
        if (exclusive) {
           return inputValue > minThreshold && inputValue < maxThreshold;
        }
        return inputValue >= minThreshold && inputValue <= maxThreshold;
   }

   public static double distance(double xDistance, double zDistance) {
        return Math.sqrt(xDistance * xDistance + zDistance * zDistance);
   }
}