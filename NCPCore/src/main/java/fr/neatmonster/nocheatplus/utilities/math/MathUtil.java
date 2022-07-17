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
 * Some methods are directly from NMS/client-code (See Mth.java)
 */
public class MathUtil {
   
   /**
    * Clamp double between a maximum and minimum value.
    * @param inputValue The value to clamp.
    * @param minParameter Exclusive
    * @param maxParameter Exclusive
    * @return the clamped value
    */
   public static double clamp(double inputValue, double minParameter, double maxParameter) {
      return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
   }

   /**
    * Clamp float between a maximum and minimum value.
    * @param inputValue The value to clamp.
    * @param minParameter Exclusive
    * @param maxParameter Exclusive
    * @return the clamped value
    */
   public static float clamp(float inputValue, float minParameter, float maxParameter) {
      return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
   }

   /**
    * Clamp int between a maximum and minimum value.
    * @param inputValue The value to clamp.
    * @param minParameter Exclusive
    * @param maxParameter Exclusive
    * @return the clamped value
    */
   public static int clamp(int inputValue, int minParameter, int maxParameter) {
      return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
   }

   /**
    * Test if the input value is in range between a minimum and maximum threshold
    * @param inputValue The value to test
    * @param minThreshold Exclusive
    * @param maxThreshold Exclusive
    * @return True if the value is between the thresholds, false otherwise
    */
   public static boolean inRange(double inputValue, double maxThreshold, double minThreshold) {
      return inputValue > minThreshold && inputValue < maxThreshold;
   }
    
   /**
    * Test if the absolute difference between two values is small enough to be considered equal.
    * @param a The minuend
    * @param b The subtrahend
    * @param c Absolute(!) value to compare the difference with
    * @return True if the absolute difference is smaller or equals C.
    *         Returns false for negative C inputs.
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
   
   /**
    * Convenience method
    * @param value the value to square.
    * @return squared value.
    */
   public static double square(double value) {
      return value * value;
   }
   
   /**
    * Test if the absolute difference between two values is small enough to be considered equal.
    * (From Mth.java)
    * @param a The minuend (Must be a float)
    * @param b The subtrahend (Must be a float)
    * @return True, if the difference is smaller than 1.0E-5F
    */
   public static boolean equal(float var0, float var1) {
      return Math.abs(var1 - var0) < 1.0E-5;
   }
   
   /**
    * Test if the absolute difference between two values is small enough to be considered equal.
    * (From Mth.java)
    * @param a The minuend (Must be a double)
    * @param b The subtrahend (Must be a double)
    * @return True, if the difference is smaller than 9.999999747378752E-6D
    */
   public static boolean equal(double var0, double var2) {
      return Math.abs(var2 - var0) < 9.999999747378752E-6D;
   }
   
   /**
    * Maximum of the absolute value of two numbers.
    * (From Mth.java)
    * @param var0
    * @param var2
    * @return Maximum absolute value between the two inputs.
    */
   public static double absMax(double var0, double var2) {
      if (var0 < 0.0D) {
         var0 = -var0;
      }

      if (var2 < 0.0D) {
         var2 = -var2;
      }
      return var0 > var2 ? var0 : var2;
   }
}