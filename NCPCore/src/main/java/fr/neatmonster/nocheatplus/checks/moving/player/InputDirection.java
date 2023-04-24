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
package fr.neatmonster.nocheatplus.checks.moving.player;

public class InputDirection {
    private float strafe;
    private float forward;
    private ForwardDirection fdir;
    private StrafeDirection sdir;

    public InputDirection(float strafe, float forward) {
        this.forward = forward;
        this.strafe = strafe;
        fdir = forward >= 0.0 ? forward == 0.0 ? ForwardDirection.NONE : ForwardDirection.FORWARD : ForwardDirection.BACKWARD;
        sdir = strafe >= 0.0 ? strafe == 0.0 ? StrafeDirection.NONE : StrafeDirection.LEFT : StrafeDirection.RIGHT;
    }

    public float getStrafe() {
        return strafe;
    }

    public float getForward() {
        return forward;
    }

    public void calculateDir(double strafeMult, double forwardMult, int operationtype) {
    	switch (operationtype) {
    		case 0:
    			strafe = 0f;
    			forward = 0f;
    			fdir = ForwardDirection.NONE;
    			sdir = StrafeDirection.NONE;
    			break;
    		case 1:
    			strafe *= strafeMult;
    			forward *= forwardMult;
    			break;
    		case 2:
    			strafe /= strafeMult;
    			forward /= forwardMult;
    			break;
    		default: return;
    	}
    	
    }

    public StrafeDirection getStrafeDir() {
        return sdir;
    }

    public ForwardDirection getForwardDir() {
        return fdir;
    }

    public enum ForwardDirection {
        NONE,
        FORWARD,
        BACKWARD
    }

    public enum StrafeDirection {
        NONE,
        LEFT,
        RIGHT
    }
}
