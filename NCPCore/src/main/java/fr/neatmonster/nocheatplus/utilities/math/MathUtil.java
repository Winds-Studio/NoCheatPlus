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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.bukkit.util.Vector;

/**
 * Auxiliary static methods for dealing with math.<br>
 * Some methods are directly from NMS
 */
public class MathUtil {
   
    /**
     * Clamp double between a maximum and minimum value.
     * 
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
     * 
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
     * 
     * @param inputValue The value to clamp.
     * @param minParameter Exclusive
     * @param maxParameter Exclusive
     * @return the clamped value
     */
    public static int clamp(int inputValue, int minParameter, int maxParameter) {
       return inputValue < minParameter ? minParameter : (inputValue > maxParameter ? maxParameter : inputValue);
    }
    
    /**
     * Test if the input value is between a minimum and maximum threshold
     * 
     * @param minThreshold Exclusive
     * @param inputValue The value to test
     * @param maxThreshold Exclusive
     * @return True if the value is between the thresholds, false otherwise
     */
    public static boolean between(double minThreshold, double inputValue, double maxThreshold) {
       return inputValue > minThreshold && inputValue < maxThreshold;
    }
      
    /**
     * Test if the input value is between a minimum and maximum threshold
     * 
     * @param minThreshold Inclusive
     * @param inputValue The value to test
     * @param maxThreshold Inclusive
     * @return True if the value is between or equal the thresholds, false otherwise
     */
    public static boolean inRange(double minThreshold, double inputValue, double maxThreshold) {
       return inputValue >= minThreshold && inputValue <= maxThreshold;
    }
        
    /**
     * Test if the absolute difference between two values is small enough to be considered equal.
     * 
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
     * 
     * @param xDistance
     * @param zDistance
     * @return the 2d distance
     */
    public static double dist(double xDistance, double zDistance) {
       return Math.sqrt(square(xDistance) + square(zDistance));
    }
    
    /**
     * Convenience method
     * 
     * @param value the value to square.
     * @return squared value.
     */
    public static double square(double value) {
       return value * value;
    }
    
    /**
     * Maximum of the absolute value of two numbers.
     * @param a
     * @param b
     * @return Maximum absolute value between the two inputs.
     */
    public static double absMax(double a, double b) {
       return Math.max(Math.abs(a), Math.abs(b));
    }
    
    /**
     * Maximum value of three numbers
     * 
     * @param a
     * @param b
     * @param c
     * @return The highest number
     */
    public static double max3(double a, double b, double c) {
       return Math.max(a, Math.max(b, c));
    }
    
    /**
     * Absolute non-zero value of the input number.
     * 
     * @param a
     * @param b
     * @return The first non-zero value.
     * @throws IllegalArgumentException if input is 0
     * 
     */
    public static double absNonZero(double input) {
       if (input > 0.0 || input < 0.0) {
          return Math.abs(input);
       } 
       else throw new IllegalArgumentException("Input cannot be 0.");
    }
     
     
     /**
      * Test if the difference between two values is small enough to be considered equal
      * 
      * @param a
      * @param b
      * @param c The difference (not inclusive)
      * @return true, if close enough.
      */
     public static boolean almostEqual(double a, double b, double c){
      return Math.abs(a-b) < c;
     }
   
    /**
     * Convenience method
     * 
     * @param x 
     * @param y
     * @param z
     * @return Normalized vector
     */
    public static double[] normalize(double x, double y, double z) {
       double distanceSq = square(x) + square(y) + square(z);
       double magnitude = Math.sqrt(distanceSq);
       return new double[] {x / magnitude, y / magnitude, z / magnitude};
    }
    
    
    /**
     * "Round" a double.
     * Not for the most precise results.
     * Use for smaller values only.
     * 
     * @param value
     * @param decimalPlaces 
     * @return The rounded double
     * @throws IllegalArgumentException if decimal places are negative
     */
    public static double round(double value, int decimalPlaces) {
        if (decimalPlaces < 0) {
          	throw new IllegalArgumentException("Decimal places cannot be negative.");
        }
        long factor = (long) Math.pow(10, decimalPlaces);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    /**
     * Calculate the standard deviation of this data.
     * https://en.wikipedia.org/wiki/Standard_deviation
     * 
     * @param data
     * @return the std dev
     */
    public static double stdDev(double[] data) {
        double variance = 0.0;
        for (double num : data) {
            variance += Math.pow(num - mean(data), 2);
        }
        return Math.sqrt(variance / data.length);
    }
    
    /**
     * Calculate the mean of this array
     * 
     * @param values
     * @return the mean
     */
    public static double mean(double[] values) {
        double sum = 0.0;
        for (double n : values) {
            sum += n;
        }
        double mean = sum / values.length;
        return mean;
    }

    /**
     * Given an array of double, find the value that is closest to the target value
     * 
     * @param arr
     * @param targetValue
     * @return the value in the array that has the smallest distance to the target value.
     * @throws IllegalArgumentException if the array is empty or null
     * 
     */
     public static double getClosestValue(double[] arr, double targetValue) {
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("Array cannot be empty.");
        }
        double closestValue = arr[0];
        double minDistance = Math.abs(arr[0] - targetValue);
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] == targetValue) {
                return arr[i];
            }
            double distance = Math.abs(arr[i] - targetValue);
            if (distance < minDistance) {
                minDistance = distance;
                closestValue = arr[i];
            }
        }
        return closestValue;
    }

    /**
     * Return the cartesian product of two array of doubles
     * https://en.wikipedia.org/wiki/Cartesian_product
     * 
     * @param data1
     * @param data2
     * @return the product
     */
    public static double[][] cartesianProduct(double[] data1, double[] data2) {
        List<double[]> tmpList = new ArrayList<>();
        for (double value1 : data1) {
            for (double value2 : data2) {
                tmpList.add(new double[]{value1, value2});
            }
        }
        double[][] product = new double[tmpList.size()][2];
        int k = 0;
        for (double[] i : tmpList) {
            product[k++] = i;
        }
        return product;
    }
}