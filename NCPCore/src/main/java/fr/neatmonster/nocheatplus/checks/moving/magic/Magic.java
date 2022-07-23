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
package fr.neatmonster.nocheatplus.checks.moving.magic;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;


/**
 * Keeping some of the magic confined in here.
 * 
 * @author asofold
 *
 */
public class Magic {

    // TODO: Do any of these belong to MovingUtil?
    // Might move some methods to another class (EnvironmentUtils (?))
    
    // CraftBukkit/Minecraft constants.
    public static final double CB_DEFAULT_WALKSPEED = 0.2;
    public static final double DEFAULT_FLYSPEED = 0.1;
    public static final double HORIZONTAL_INERTIA = 0.91;
    public static final double BUNNYHOP_ACCEL_BOOST = 0.2;
    public static final int BUNNYHOP_MAX_DELAY = 10;
    public static final double CLIMBABLE_MAX_HORIZONTAL_SPEED = 0.15200000596046448;
    public static final double AIR_MOVEMENT_SPEED_ATTRIBUTE = 0.02;
    public static final double LIQUID_BASE_ACCELERATION = 0.02;
    public static final double HORIZONTAL_SWIMMING_INERTIA = 0.9;
    public static final double WATER_HORIZONTAL_INERTIA = 0.8;
    public static final double DOLPHIN_GRACE_INERTIA = 0.96;
    public static final double STRIDER_OFF_GROUND_PENALTY_MULTIPLIER = 0.5;
    public static final double SNEAK_MULTIPLIER = 0.3;
    public static final double SPRINT_MULTIPLIER = 1.3;
    public static final double USING_ITEM_MULTIPLIER = 0.2;
    public static final double SNEAK_STEP_DISTANCE = 0.05;
    public static final double LAVA_HORIZONTAL_INERTIA = 0.5;
    public static final double MIN_MOVEMENT_DISTANCE = 0.003;
    public static final double BASE_MOVEMENT_SPEED = 0.21600002;
    public static final double LEGACY_BASE_MOVEMENT_SPEED = 0.16277136;
    public static final double BUNNYHOP_JUMP_BONUS = 0.291924;
    public static final double SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD = 0.13;
    public static final double SLIDE_SPEED_THROTTLE = 0.05;
    public static final double MIN_SLIDE_FALLING_SPEED = 0.08;


    // Gravity.
    public static final double GRAVITY_MAX = 0.0834;
    public static final double GRAVITY_DEFAULT = 0.08;
    public static final double GRAVITY_MIN = 0.0624; 
    public static final double GRAVITY_ODD = 0.05;
    /** Assumed minimal average decrease per move, suitable for regarding 3 moves. */
    public static final float GRAVITY_VACC = (float) (GRAVITY_MIN * 0.6); // 0.03744
    public static final double GRAVITY_SPAN = GRAVITY_MAX - GRAVITY_MIN; // 0.021
    public static final double SLOWFALL_GRAVITY = 0.01;

    // Friction factor by medium (move inside of).
    public static final double FRICTION_MEDIUM_AIR = 0.98;
    public static final double FRICTION_MEDIUM_WATER = 0.98;
    public static final double FRICTION_MEDIUM_LAVA = 0.535;
    public static final double FRICTION_MEDIUM_ELYTRA_AIR = 0.9800002;

    // Horizontal speeds/modifiers. 
    public static final double WALK_SPEED           = 0.221D;
    public static final double[] modSwim            = new double[] {
            // Horizontal AND vertical with body fully in water
            0.115D / WALK_SPEED,  
            // Horizontal swimming only, 1.13 (Do not multiply with thisMove.walkSpeed)
            0.044D / WALK_SPEED,  
            // Vertical swimming only, 1.13 
            0.3D / WALK_SPEED, 
            // Horizontal with body out of water (surface level)
            0.146D / WALK_SPEED,}; 
    public static final double modDownStream        = 0.19D / (WALK_SPEED * modSwim[0]);
    public static final double[] modDepthStrider    = new double[] {
            1.0,
            0.1645 / modSwim[0] / WALK_SPEED,
            0.1995 / modSwim[0] / WALK_SPEED,
            1.0 / modSwim[0], // Results in walkspeed.
    };
    /**
     * Somewhat arbitrary horizontal speed gain maximum for advance glide phase.
     */
    public static final double GLIDE_HORIZONTAL_GAIN_MAX = GRAVITY_MAX / 2.0;

    // Vertical speeds/modifiers. 
    public static final double climbSpeedAscend        = 0.119;
    public static final double climbSpeedDescend       = 0.151;
    public static final double snowClimbSpeedAscend    = 0.177;
    public static final double snowClimbSpeedDescend   = 0.118;
    public static final double webSpeedDescendH        = -0.062;
    public static final double webSpeedDescendDefault  = -0.032;
    public static final double bushSpeedAscend         = 0.315;
    public static final double bushSpeedDescend        = -0.09;
    public static final double bubbleStreamDescend     = 0.49; // from wiki.
    public static final double bubbleStreamAscend      = 0.9; // 1.1 from wiki. Wiki is too fast 
    /**
     * Some kind of minimum y descend speed (note the negative sign), for an
     * already advanced gliding/falling phase with elytra.
     */
    public static final double GLIDE_DESCEND_PHASE_MIN = -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN;
    /**
     * Somewhat arbitrary, advanced glide phase, maximum descend speed gain
     * (absolute value is negative).
     */
    public static final double GLIDE_DESCEND_GAIN_MAX_NEG = -GRAVITY_MAX;
    /**
     * Somewhat arbitrary, advanced glide phase, maximum descend speed gain
     * (absolute value is positive, a negative gain seen in relation to the
     * moving direction).
     */
    public static final double GLIDE_DESCEND_GAIN_MAX_POS = GRAVITY_ODD / 1.95;

    // On-ground.
    public static final double Y_ON_GROUND_MIN = 0.00001;
    public static final double Y_ON_GROUND_MAX = 0.0626;
    // TODO: Model workarounds as lost ground, use Y_ON_GROUND_MIN?
    public static final double Y_ON_GROUND_DEFAULT = 0.025; // Jump upwards, while placing blocks. // Old 0.016
    //    public static final double Y_ON_GROUND_DEFAULT = 0.029; // Bounce off slime blocks.
    /** The lower bound of fall distance for taking fall damage. */
    public static final double FALL_DAMAGE_DIST = 3.0;
    /** The minimum damage amount that actually should get applied. */
    public static final double FALL_DAMAGE_MINIMUM = 0.5;

    /**
     * The maximum distance that can be achieved with bouncing back from slime
     * blocks.
     */
    public static final double BOUNCE_VERTICAL_MAX_DIST = 3.5;

    // Other constants.
    public static final double PAPER_DIST = 0.01;
    /**
     * Extreme move check threshold (Actual like 3.9 upwards with velocity,
     * velocity downwards may be like -1.835 max., but falling will be near 3
     * too.)
     */
    public static final double EXTREME_MOVE_DIST_VERTICAL = 4.0;
    public static final double EXTREME_MOVE_DIST_HORIZONTAL = 22.0;
    /** Minimal xz-margin for chunk load. */
    public static final double CHUNK_LOAD_MARGIN_MIN = 3.0;

    /**
     * Test if this move is a bunnyhop (sprint-jump)
     * 
     * @param data
     * @param fromOnGroundOrLostGround This is tested for only if regular thisMove.from.onGround returns false.
     * @param sprinting
     * @param doubleHop Special hop case.
     * @return true if is bunnyhop.
     * 
     */
    public static boolean isBunnyhop(final MovingData data, final boolean fromOnGroundOrLostGround, boolean sprinting, boolean doubleHop) {
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        
        // Ensure the mechanic is not exploited to hop faster, if lowjumping
        if (data.sfLowJump) {
            return false;
        }

        return 
                // This mechanic is applied only if the player is sprinting
                sprinting && lastMove.toIsValid
                // 0: Motion speed speed condition. Demand the player to hit the jumping envelope.
                && thisMove.yDistance > LiftOffEnvelope.NORMAL.getMinJumpGain(data.jumpAmplifier) - GRAVITY_SPAN
                // 0: Ground conditions
                && (
                    // 1: Ordinary/obvious lift-off.
                    data.sfJumpPhase == 0 && thisMove.from.onGround
                    // 1: Do allow hopping if a past on ground status is found or this (legitimate) on ground phase was lost 
                    || data.sfJumpPhase == 1 && fromOnGroundOrLostGround && !lastMove.bunnyHop
                    // 1: Special case: double bunny 
                    || doubleHop 
                )
            ;
    }
    
    /**
     * 
     * @param thisMove
     *            Not strictly the latest move in MovingData.
     * @return
     */
    public static boolean touchedIce(final PlayerMoveData thisMove) {
        return thisMove.from.onIce || thisMove.from.onBlueIce || thisMove.to.onIce || thisMove.to.onBlueIce;
    } 

    /**
     * The absolute per-tick base speed for swimming vertically.
     * 
     * @return
     */
    public static double swimBaseSpeedV(boolean isSwimming) {
        // TODO: Does this have to be the dynamic walk speed (refactoring)?
        return isSwimming ? WALK_SPEED * modSwim[2] + 0.1 : WALK_SPEED * modSwim[0] + 0.07; // 0.244
    }

    /**
     * Test if the player is (well) within in-air falling envelope.
     * @param yDistance
     * @param lastYDist
     * @param extraGravity Extra amount to fall faster.
     * @return
     */
    public static boolean fallingEnvelope(final double yDistance, final double lastYDist, 
                                          final double lastFrictionVertical, final double extraGravity) {
        if (yDistance >= lastYDist) {
            return false;
        }
        // TODO: data.lastFrictionVertical (see vDistAir).
        final double frictDist = lastYDist * lastFrictionVertical - GRAVITY_MIN;
        // TODO: Extra amount: distinguish pos/neg?
        return yDistance <= frictDist + extraGravity && yDistance > frictDist - GRAVITY_SPAN - extraGravity;
    }

    /**
     * Friction envelope testing, with a different kind of leniency (relate
     * off-amount to decreased amount), testing if 'friction' has been accounted
     * for in a sufficient but not necessarily exact way.<br>
     * In the current shape this method is meant for higher speeds rather (needs
     * a twist for low speed comparison).
     * 
     * @param thisMove
     * @param lastMove
     * @param friction
     *            Friction factor to apply.
     * @param minGravity
     *            Amount to subtract from frictDist by default.
     * @param maxOff
     *            Amount yDistance may be off the friction distance.
     * @param decreaseByOff
     *            Factor, how many times the amount being off friction distance
     *            must fit into the decrease from lastMove to thisMove.
     * @return
     */
    public static boolean enoughFrictionEnvelope(final PlayerMoveData thisMove, final PlayerMoveData lastMove, final double friction, 
                                                 final double minGravity, final double maxOff, final double decreaseByOff) {

        // TODO: Elaborate... could have one method to test them all?
        final double frictDist = lastMove.yDistance * friction - minGravity;
        final double off = Math.abs(thisMove.yDistance - frictDist);
        return off <= maxOff && Math.abs(thisMove.yDistance - lastMove.yDistance) <= off * decreaseByOff;
    }
    
   /**
    * Test (using the past move tracking) if the player has jumped at least 1 block up.
    * No tight checking.
    * @param data
    * @param currentLoc
    *             From/To location
    * @param limit
    *             How many past moves should be tracked
    * @return 
    */
    public static boolean jumpedUpSlope(final MovingData data, final PlayerLocation loc, int limit) {
        limit = Math.min(limit, data.playerMoves.getNumberOfPastMoves());
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();

        // Don't care about jump potions.
        if (data.jumpAmplifier != 0.0){
            return false;
        }
            
        for (int i = 0; i < limit; i++) {
            final PlayerMoveData pastMove = data.playerMoves.getPastMove(i);
            // Stairs are for now skipped, need to fix on ground logic.
            if (!pastMove.toIsValid || thisMove.from.aboveStairs) {
                return false;
            }
            // this move is on ground
            else if (loc.isOnGround()
                    // Sufficient (absolute) height difference
                    && (loc.getY() - pastMove.to.getY()) < data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                    && (loc.getY() - pastMove.to.getY()) >= 0.90 // (needed to prevent regular jumps from being seen as as slope)
                    // Past moves were on ground
                    && pastMove.touchedGround && !pastMove.touchedGroundWorkaround) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Simplistic check for past lift off states done via the past move tracking.
     * Does not check if players may be able to lift off at all (i.e: in liquid)
     * @return
     */
    public static boolean isValidLiftOffAvailable(int limit, final MovingData data) {
        limit = Math.min(limit, data.playerMoves.getNumberOfPastMoves());
        for (int i = 0; i < limit; i++) {
            final PlayerMoveData pastMove = data.playerMoves.getPastMove(i);
            if (pastMove.from.onGround && !pastMove.to.onGround 
                && pastMove.toIsValid && pastMove.yDistance > LiftOffEnvelope.NORMAL.getMaxJumpGain(data.jumpAmplifier) - GRAVITY_MAX - Y_ON_GROUND_MIN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test for a specific move in-air -> water, then water -> in-air.
     * 
     * @param thisMove
     *            Not strictly the latest move in MovingData.
     * @param lastMove
     *            Move before thisMove.
     * @return
     */
    static boolean splashMove(final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        // Use past move data for two moves.
        return !thisMove.touchedGround && thisMove.from.inWater && !thisMove.to.resetCond // Out of water.
                && !lastMove.touchedGround && !lastMove.from.resetCond && lastMove.to.inWater // Into water.
                && excludeStaticSpeed(thisMove) && excludeStaticSpeed(lastMove)
                ;
    }

    /**
     * Test for a specific move ground/in-air -> water, then water -> in-air.
     * 
     * @param thisMove
     *            Not strictly the latest move in MovingData.
     * @param lastMove
     *            Move before thisMove.
     * @return
     */
    static boolean splashMoveNonStrict(final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        // Use past move data for two moves.
        return !thisMove.touchedGround && thisMove.from.inWater && !thisMove.to.resetCond // Out of water.
                && !lastMove.from.resetCond && lastMove.to.inWater // Into water.
                && excludeStaticSpeed(thisMove) && excludeStaticSpeed(lastMove)
                ;
    }


    public static boolean recentlyInWaterfall(final MovingData data, int limit) {
        limit = Math.min(limit, data.playerMoves.getNumberOfPastMoves());
        for (int i = 0; i < limit; i++) {
            final PlayerMoveData move = data.playerMoves.getPastMove(i);
            if (!move.toIsValid) {
                return false;
            }
            else if (move.inWaterfall) {
                return true;
            }
        }
        return false;
    }

    /* Fully in-air move.
     * 
     * @param thisMove
     *            Not strictly the latest move in MovingData.
     * @return
     */
    public static boolean inAir(final PlayerMoveData thisMove) {
        return !thisMove.touchedGround && !thisMove.from.resetCond && !thisMove.to.resetCond;
    }

    /**
     * Test if the player has lifted off from the ground or is landing (not in air, not walking on ground)
     * (Does not check for resetCond)
     * 
     * @return 
     */
    public static boolean XORonGround(final PlayerMoveData move) {
        return move.from.onGround ^ move.to.onGround;
    }

    /**
     * A liquid -> liquid move. Exclude web and climbable.
     * 
     * @param thisMove
     * @return
     */
    static boolean inLiquid(final PlayerMoveData thisMove) {
        return thisMove.from.inLiquid && thisMove.to.inLiquid && excludeStaticSpeed(thisMove);
    }

    /**
     * A water -> water move. Exclude web and climbable.
     * 
     * @param thisMove
     * @return
     */
    public static boolean inWater(final PlayerMoveData thisMove) {
        return thisMove.from.inWater && thisMove.to.inWater && excludeStaticSpeed(thisMove);
    }

    /**
     * Test if either point is in reset condition (liquid, web, ladder).
     * 
     * @param thisMove
     * @return
     */
    static boolean resetCond(final PlayerMoveData thisMove) {
        return thisMove.from.resetCond || thisMove.to.resetCond;
    }

    /**
     * Moving out of liquid, might move onto ground. Exclude web and climbable.
     * 
     * @param thisMove
     * @return
     */
    public static boolean leavingLiquid(final PlayerMoveData thisMove) {
        return thisMove.from.inLiquid && !thisMove.to.inLiquid && excludeStaticSpeed(thisMove);
    }

    /**
     * Moving out of water, might move onto ground. Exclude web and climbable.
     * 
     * @param thisMove
     * @return
     */
    public static boolean leavingWater(final PlayerMoveData thisMove) {
        return thisMove.from.inWater && !thisMove.to.inWater && excludeStaticSpeed(thisMove);
    }

    /**
     * Moving into water, might move onto ground. Exclude web and climbable.
     * 
     * @param thisMove
     * @return
     */
    public static boolean intoWater(final PlayerMoveData thisMove) {
        return !thisMove.from.inWater && thisMove.to.inWater && excludeStaticSpeed(thisMove);
    }

    /**
     * Moving into liquid., might move onto ground. Exclude web and climbable.
     * 
     * @param thisMove
     * @return
     */
    static boolean intoLiquid(final PlayerMoveData thisMove) {
        return !thisMove.from.inLiquid && thisMove.to.inLiquid && excludeStaticSpeed(thisMove);
    }

    /**
     * Exclude moving from/to blocks with static (vertical) speed, such as web, climbable, berry bushes.
     * 
     * @param thisMove
     * @return
     */
    public static boolean excludeStaticSpeed(final PlayerMoveData thisMove) {
        return !thisMove.from.inWeb && !thisMove.to.inWeb
                && !thisMove.from.onClimbable && !thisMove.to.onClimbable
                && !thisMove.from.inBerryBush && !thisMove.to.inBerryBush;
    }

    /**
     * First move after set back / teleport. Originally has been found with
     * PaperSpigot for MC 1.7.10, however it also does occur on Spigot for MC
     * 1.7.10.
     * 
     * @param thisMove
     * @param lastMove
     * @param data
     * @return
     */
    public static boolean skipPaper(final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data) {
        // TODO: Confine to from at block level (offset 0)?
        final double setBackYDistance;
        if (data.hasSetBack()) {
            setBackYDistance = thisMove.to.getY() - data.getSetBackY();
        }
        // Skip being all too forgiving here.
        //        else if (thisMove.touchedGround) {
        //            setBackYDistance = 0.0;
        //        }
        else {
            return false;
        }
        return !lastMove.toIsValid && data.sfJumpPhase == 0 && thisMove.multiMoveCount > 0
                && setBackYDistance > 0.0 && setBackYDistance < PAPER_DIST 
                && thisMove.yDistance > 0.0 && thisMove.yDistance < PAPER_DIST && inAir(thisMove);
    }

    /**
     * Advanced glide phase vertical gain envelope.
     * @param yDistance
     * @param previousYDistance
     * @return
     */
    public static boolean glideVerticalGainEnvelope(final double yDistance, final double previousYDistance) {
        return // Sufficient speed of descending.
                yDistance < GLIDE_DESCEND_PHASE_MIN && previousYDistance < GLIDE_DESCEND_PHASE_MIN
                // Controlled difference.
                && yDistance - previousYDistance > GLIDE_DESCEND_GAIN_MAX_NEG 
                && yDistance - previousYDistance < GLIDE_DESCEND_GAIN_MAX_POS;
    }

    /**
     * Test if this + last 2 moves are within the gliding envelope (elytra), in
     * this case with horizontal speed gain.
     * 
     * @param thisMove
     * @param lastMove
     * @param pastMove1
     *            Is checked for validity in here (needed).
     * @return
     */
    public static boolean glideEnvelopeWithHorizontalGain(final PlayerMoveData thisMove, final PlayerMoveData lastMove, final PlayerMoveData pastMove1) {
        return pastMove1.toIsValid 
                && Magic.glideVerticalGainEnvelope(thisMove.yDistance, lastMove.yDistance)
                && Magic.glideVerticalGainEnvelope(lastMove.yDistance, pastMove1.yDistance)
                && lastMove.hDistance > pastMove1.hDistance && thisMove.hDistance > lastMove.hDistance
                && Math.abs(lastMove.hDistance - pastMove1.hDistance) < Magic.GLIDE_HORIZONTAL_GAIN_MAX
                && Math.abs(thisMove.hDistance - lastMove.hDistance) < Magic.GLIDE_HORIZONTAL_GAIN_MAX
                ;
    }

    /**
     * Jump off the top off a block with the ordinary jumping envelope, however
     * from a slightly higher position with the initial gain being lower than
     * typical, but the following move having the y distance as if jumped off
     * with typical gain.
     * 
     * @param yDistance
     * @param maxJumpGain
     * @param thisMove
     * @param lastMove
     * @param data
     * @return
     */
    public static boolean noobJumpsOffTower(final double yDistance, final double maxJumpGain, 
            final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data) {
        final PlayerMoveData secondPastMove = data.playerMoves.getSecondPastMove();
        return (
                data.sfJumpPhase == 1 && lastMove.touchedGroundWorkaround // TODO: Not observed though.
                || data.sfJumpPhase == 2 && inAir(lastMove)
                && secondPastMove.valid && secondPastMove.touchedGroundWorkaround
                )
                && inAir(thisMove)
                && lastMove.yDistance < maxJumpGain && lastMove.yDistance > maxJumpGain * 0.67
                && Magic.fallingEnvelope(yDistance, maxJumpGain, data.lastFrictionVertical, Magic.GRAVITY_SPAN);
    }
}
