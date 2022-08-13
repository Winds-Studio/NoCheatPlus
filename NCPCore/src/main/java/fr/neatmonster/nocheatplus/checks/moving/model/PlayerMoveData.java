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
package fr.neatmonster.nocheatplus.checks.moving.model;

import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;

/**
 * Include player specific data for a move.
 * 
 * @author asofold
 *
 */
public class PlayerMoveData extends MoveData {

    //////////////////////////////////////////////////////////
    // Reset with set, could be lazily set during checking.
    //////////////////////////////////////////////////////////

    // Properties of the player.
	/** Whether this move has the levitation effect active */
	public boolean hasLevitation;
	
	/** Whether this move has the slowfall effect active */
	public boolean hasSlowfall;
	
	/** Whether this movement is influenced by gravity */
	public boolean hasGravity;

    /**
     * Typical maximum walk speed, accounting for player capabilities. Set in
     * SurvivalFly.check.
     */
    public double walkSpeed;
    
    /**
     * The distance covered by a move from the setback point to the to.getY() point
     */
    public double setBackYDistance;


    // Bounds set by checks.
    /**
     * Allowed horizontal distance (including frictions, workarounds like bunny
     * hopping). Set in SurvivalFly.check.
     */
    public double hAllowedDistance;

    /**
     * Allowed vertical distance mostly use for elytra. Set in CreativeFly.check.
     */
    public double yAllowedDistance;


    // Properties involving the environment.
    /** This move was a bunny hop. */
    public boolean bunnyHop;
   
    /** This move was allowed to step. Set in SurvivalFly.check(vdistrel) */
    public boolean allowstep;

    /** This move was allowed to jump. Set in SurvivalFly.check(vdistrel) */
    public boolean allowjump;


    // Meta stuff.
    /**
     * Due to the thresholds for moving events, there could have been other
     * (micro-) moves by the player which could not be checked. One moving event
     * is split into two moves 1: from -> loc, 2: loc -> to.
     */
    public int multiMoveCount;

    /**
     * Just the used vertical velocity. Could be overridden multiple times
     * during processing of moving checks.
     */
    public SimpleEntry verVelUsed = null;
    
    /** The lift off envelope currently used by this move. Set in the moving listener. */
    public LiftOffEnvelope liftOffEnvelope = null;

    @Override
    protected void resetBase() {
        // Properties of the player.
        walkSpeed = 0.287;
        hasLevitation = false;
        hasSlowfall = false;
        hasGravity = true; // Assume one to have gravity rather than the opposite... :)
        // Properties involving the environment.
        bunnyHop = false;
        allowstep = false;
        allowjump = false;
        // Bounds set by checks.
        yAllowedDistance = 0.0;
        hAllowedDistance = 0.0;
        // Meta stuff.
        multiMoveCount = 0;
        verVelUsed = null;
        liftOffEnvelope = LiftOffEnvelope.UNKNOWN;
        // Super class last, because it'll set valid to true in the end.
        super.resetBase();
    }

}
