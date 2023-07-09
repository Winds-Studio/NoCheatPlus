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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.envelope.PlayerEnvelopes;
import fr.neatmonster.nocheatplus.checks.moving.envelope.workaround.AirWorkarounds;
import fr.neatmonster.nocheatplus.checks.moving.envelope.workaround.LiquidWorkarounds;
import fr.neatmonster.nocheatplus.checks.moving.envelope.workaround.LostGround;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.InputDirection;
import fr.neatmonster.nocheatplus.checks.moving.model.InputDirection.ForwardDirection;
import fr.neatmonster.nocheatplus.checks.moving.model.InputDirection.StrafeDirection;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.Direction;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.collision.CollisionUtil;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionAccumulator;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.moving.Magic;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;

/**
 * The counterpart to the CreativeFly check. <br>People that are not allowed to fly get checked by this. <br>It will try to
 * identify when they are jumping, check if they aren't jumping too high or far, check if they aren't moving too fast on
 * normal ground, while sprinting, sneaking, swimming, etc.
 */
public class SurvivalFly extends Check {

    private final boolean ServerIsAtLeast1_13 = ServerVersion.compareMinecraftVersion("1.13") >= 0;
    /** To join some tags with moving check violations. */
    private final ArrayList<String> tags = new ArrayList<String>(15);
    private final ArrayList<String> justUsedWorkarounds = new ArrayList<String>();
    private final Set<String> reallySneaking = new HashSet<String>(30);
    private final BlockChangeTracker blockChangeTracker;
    /** Location for temporary use with getLocation(useLoc). Always call setWorld(null) after use. Use LocUtil.clone before passing to other API. */
    private final Location useLoc = new Location(null, 0, 0, 0);
    

    /**
     * Instantiates a new survival fly check.
     */
    public SurvivalFly() {
        super(CheckType.MOVING_SURVIVALFLY);
        blockChangeTracker = NCPAPIProvider.getNoCheatPlusAPI().getBlockChangeTracker();
    }


    /**
     * Checks a player
     * @param player
     * @param from
     * @param to
     * @param multiMoveCount
     *            0: Ordinary, 1/2: first/second of a split move.
     * @param data
     * @param cc
     * @param pData
     * @param tick
     * @param now
     * @param useBlockChangeTracker
     * @param isNormalOrPacketSplitMove
     *           Flag to indicate if the packet-based split move mechanic is used instead of the Bukkit-based one (or the move was not split)
     * @return
     */
    public Location check(final Player player, final PlayerLocation from, final PlayerLocation to, 
                          final int multiMoveCount, final MovingData data, final MovingConfig cc, 
                          final IPlayerData pData, final int tick, final long now, 
                          final boolean useBlockChangeTracker, final boolean isNormalOrPacketSplitMove) {
        tags.clear();
        // Shortcuts:
        final boolean debug = pData.isDebugActive(type);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final boolean isSamePos = from.isSamePos(to);
        final double xDistance, yDistance, zDistance, hDistance;
        final boolean HasHorizontalDistance;
        /** Regular and past fromOnGround */
        final boolean fromOnGround = thisMove.from.onGround || useBlockChangeTracker && from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick);
        // TODO: A more flexible method for isOnGround that includes ALL cases. See todo in discord
        /** Regular and past toOnGround */
        final boolean toOnGround = thisMove.to.onGround || useBlockChangeTracker && to.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick);  // TODO: Work in the past ground stuff differently (thisMove, touchedGround?, from/to ...)
        /** To a reset-condition location (basically everything that isn't in air) */
        final boolean resetTo = toOnGround || to.isResetCond();

        if (debug) {
            justUsedWorkarounds.clear();
            data.ws.setJustUsedIds(justUsedWorkarounds);
        }

        // Calculate some distances.
        if (isSamePos) {
            xDistance = yDistance = zDistance = hDistance = 0.0;
            HasHorizontalDistance = false;
        }
        else {
            xDistance = to.getX() - from.getX();
            yDistance = thisMove.yDistance;
            zDistance = to.getZ() - from.getZ();
            if (xDistance == 0.0 && zDistance == 0.0) {
                hDistance = 0.0;
                HasHorizontalDistance = false;
            }
            else {
                HasHorizontalDistance = true;
                hDistance = thisMove.hDistance;
            }
        }

        // Recover from data removal (somewhat random insertion point).
        if (data.liftOffEnvelope == LiftOffEnvelope.UNKNOWN) {
            data.adjustLiftOffEnvelope(from);
        }
        
        /** From a reset-condition location, lostground is accounted for here. */
        // TODO: This isn't correct, needs redesign.
        final boolean resetFrom = fromOnGround || from.isResetCond() 
                                || isSamePos && lastMove.toIsValid && LostGround.lostGroundStill(player, from, to, hDistance, yDistance, player.isSprinting(), lastMove, data, cc, tags)
                                || LostGround.lostGround(player, from, to, hDistance, yDistance, player.isSprinting(), lastMove, data, cc, useBlockChangeTracker ? blockChangeTracker : null, tags);

        // Alter some data before checking anything
        // NOTE: Should not use loc for adjusting as split moves can mess up
        final Location loc = player.getLocation(useLoc);
        data.adjustMediumProperties(loc, cc, player, thisMove);
        // Cleanup
        useLoc.setWorld(null);

        if (thisMove.touchedGround) {
            // Lost ground workaround has just been applied, check resetting of the dirty flag.
            // TODO: Always/never reset with any ground touched?
            if (!thisMove.from.onGround && !thisMove.to.onGround) {
                data.resetVelocityJumpPhase(tags);
            }
            // Ground somehow appeared out of thin air (block place).
            else if (multiMoveCount == 0 && thisMove.from.onGround && Magic.inAir(lastMove)
                    && TrigUtil.isSamePosAndLook(thisMove.from, lastMove.to)) {
                data.setSetBack(from);
                if (debug) {
                    debug(player, "Ground appeared due to a block-place: adjust set-back location.");
                }
            }
        }
        
        // Renew the "dirty"-flag (in-air phase affected by velocity).
        // (Reset is done after checks run.) 
        if (data.isVelocityJumpPhase() || data.resetVelocityJumpPhase(tags)) {
            tags.add("dirty");
        }

        // Decrease bunnyhop delay counter (bunnyfly)
        if (data.bunnyhopDelay > 0) {
            data.bunnyhopDelay--; 
        }


        /////////////////////////////////////
        // Horizontal move                ///
        /////////////////////////////////////
        double hAllowedDistance = 0.0, hDistanceAboveLimit = 0.0, hFreedom = 0.0;

        // Run through all hDistance checks if the player has actually some horizontal distance (saves some performance)
        if (HasHorizontalDistance) {
            // Set the allowed distance and determine the distance above limit
            double[] estimationRes = hDistRel(from, to, pData, player, data, thisMove, lastMove, cc, tick, 
                                              useBlockChangeTracker, fromOnGround, toOnGround, debug, multiMoveCount, isNormalOrPacketSplitMove);
            hAllowedDistance = estimationRes[0];
            hDistanceAboveLimit = estimationRes[1];
            // The player went beyond the allowed limit, execute the after failure checks.
            if (hDistanceAboveLimit > 0.0) {
                double[] failureResult = hDistAfterFailure(player, multiMoveCount, from, to, hAllowedDistance, hDistanceAboveLimit, 
                                                           thisMove, lastMove, debug, data, cc, pData, tick, useBlockChangeTracker, fromOnGround, 
                                                           toOnGround, isNormalOrPacketSplitMove);
                hAllowedDistance = failureResult[0];
                hDistanceAboveLimit = failureResult[1];
                hFreedom = failureResult[2];
            }
            // Clear active velocity if the distance is within limit (clearly not needed. :))
            else {
                data.clearActiveHorVel();
                hFreedom = 0.0;
            }
        }
        // No horizontal distance present, mildly reset some horizontal-related data.
        else {
            data.clearActiveHorVel();
            thisMove.hAllowedDistance = 0.0;
            thisMove.xAllowedDistance = 0.0;
            thisMove.zAllowedDistance = 0.0;
            hFreedom = 0.0;
        }
        // Adjust some data after horizontal checking but before vertical
        data.setHorDataExPost();


        /////////////////////////////////////
        // Vertical move                  ///
        /////////////////////////////////////
        double vAllowedDistance = 0, vDistanceAboveLimit = 0;
        // Step wild-card: allow step height from ground to ground.
        if (yDistance >= 0.0 && yDistance <= cc.sfStepHeight 
            && toOnGround && fromOnGround && !from.isOnClimbable() 
            && !to.isOnClimbable() && !thisMove.hasLevitation
            && !from.isInPowderSnow() && !to.isInPowderSnow()) {
            vAllowedDistance = cc.sfStepHeight;
            thisMove.canStep = true;
            tags.add("groundstep");
        }
        else if (from.isOnClimbable()) {
            // Climbable speed has priority over stuck speed: else false positives.
            // They can technically be placed inside liquids.
            final double[] resultClimbable = vDistClimbable(player, from, to, fromOnGround, toOnGround, pData, thisMove, lastMove, yDistance, data, cc);
            vAllowedDistance = resultClimbable[0];
            vDistanceAboveLimit = resultClimbable[1];
        }
        else if (from.isInLiquid()) { 
            // Minecraft checks for liquids first, then for air.
            final double[] resultLiquid = vDistLiquid(thisMove, from, to, toOnGround, yDistance, lastMove, data, player, cc, pData, fromOnGround);
            vAllowedDistance = resultLiquid[0];
            vDistanceAboveLimit = resultLiquid[1];

            // The friction jump phase has to be set externally.
            if (vDistanceAboveLimit <= 0.0 && yDistance > 0.0 
                && Math.abs(yDistance) > Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player))) {
                data.setFrictionJumpPhase();
            }
        }
        else {
            final double[] resultAir = vDistRel(now, player, from, fromOnGround, resetFrom, 
                                                to, toOnGround, resetTo, hDistanceAboveLimit, yDistance, 
                                                multiMoveCount, lastMove, data, cc, pData);
            vAllowedDistance = resultAir[0];
            vDistanceAboveLimit = resultAir[1];
        }



        //////////////////////////////////////////////////////////////////////
        // Vertical push/pull. (Horizontal is done in hDistanceAfterFailure)//
        //////////////////////////////////////////////////////////////////////
        // TODO: Better place for checking for moved blocks [redesign for intermediate result objects?].
        if (useBlockChangeTracker && vDistanceAboveLimit > 0.0) {
            double[] blockMoveResult = getVerticalBlockMoveResult(yDistance, from, to, tick, data);
            if (blockMoveResult != null) {
                vAllowedDistance = blockMoveResult[0];
                vDistanceAboveLimit = blockMoveResult[1];
            }
        }


        ////////////////////////////
        // Debug output.          //
        ////////////////////////////
        final int tagsLength;
        if (debug) {
            outputDebug(player, to, from, data, cc, hDistance, hAllowedDistance, hFreedom, 
                        yDistance, vAllowedDistance, fromOnGround, resetFrom, toOnGround, 
                        resetTo, thisMove, vDistanceAboveLimit);
            tagsLength = tags.size();
            data.ws.setJustUsedIds(null);
        }
        else tagsLength = 0; // JIT vs. IDE.


        //////////////////////////////////////
        // Handle violations               ///
        //////////////////////////////////////
        final boolean inAir = Magic.inAir(thisMove);
        final double result = (Math.max(hDistanceAboveLimit, 0D) + Math.max(vDistanceAboveLimit, 0D)) * 100D;
        if (result > 0D) {

            final Location vLoc = handleViolation(now, result, player, from, to, data, cc);
            if (inAir) {
                data.sfVLInAir = true;
            }
            // Request a new to-location
            if (vLoc != null) {
                return vLoc;
            }
        }
        else {
            // Slowly reduce the level with each event, if violations have not recently happened.
            if (data.getPlayerMoveCount() - data.sfVLMoveCount > cc.survivalFlyVLFreezeCount 
                && (!cc.survivalFlyVLFreezeInAir || !inAir
                    // Favor bunny-hopping slightly: clean descend.
                    || !data.sfVLInAir
                    && data.liftOffEnvelope == LiftOffEnvelope.NORMAL
                    && lastMove.toIsValid 
                    && lastMove.yDistance < -Magic.GRAVITY_MIN
                    && thisMove.yDistance - lastMove.yDistance < -Magic.GRAVITY_MIN)) {
                // Relax VL.
                data.survivalFlyVL *= 0.95;
            }
        }


        //////////////////////////////////////////////////////////////////////////////////////////////
        //  Set data for normal move or violation without cancel (cancel would have returned above) //
        //////////////////////////////////////////////////////////////////////////////////////////////
        // Adjust lift off envelope to medium
        // NOTE: isNextToGround(0.15, 0.4) allows a little much (yMargin), but reduces false positives.
        // Related commit: https://github.com/NoCheatPlus/NoCheatPlus/commit/c8ac66de2c94ac9f70f29c350054dd7896cd8646#diff-b8df089e2a4295e12695420f6066320d96119aa12c80e1c64efcb959f089db2d
        // NOTE: 0.2 margin seems to work fine. No new false positives have been reported.
        // Let's try to lower it even more next time.
        final LiftOffEnvelope oldLiftOffEnvelope = data.liftOffEnvelope;
        if (thisMove.to.inLiquid) {
            if (fromOnGround && !toOnGround && data.liftOffEnvelope == LiftOffEnvelope.NORMAL
                && data.sfJumpPhase <= 0 && !thisMove.from.inLiquid) {
                // KEEP
            }
            // Moving in liquid near ground, weak/no jump limit.
            else if (to.isNextToGround(0.15, 0.2)) {
                data.liftOffEnvelope = LiftOffEnvelope.LIMIT_NEAR_GROUND;
            }
            // Fallback to default liquid lift-off limit.
            else data.liftOffEnvelope = LiftOffEnvelope.LIMIT_LIQUID;
        }
        else if (thisMove.to.inPowderSnow) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_POWDER_SNOW;
        }
        else if (thisMove.to.inWeb) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_WEBS; 
        }
        else if (thisMove.to.inBerryBush) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_SWEET_BERRY;
        }
        else if (thisMove.to.onHoneyBlock) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_HONEY_BLOCK;
        }
        else if (resetTo) {
            // TODO: This might allow jumping on vines etc., but should do for the moment.
            data.liftOffEnvelope = LiftOffEnvelope.NORMAL;
        }
        else if (thisMove.from.inLiquid) {
            if (!resetTo && data.liftOffEnvelope == LiftOffEnvelope.NORMAL && data.sfJumpPhase <= 0) {
                // KEEP
            }
            // Moving in liquid near ground, weak/no jump limit.
            else if (to.isNextToGround(0.15, 0.2)) {
                data.liftOffEnvelope = LiftOffEnvelope.LIMIT_NEAR_GROUND;
            }
            // Fallback to default liquid lift-off limit.
            else data.liftOffEnvelope = LiftOffEnvelope.LIMIT_LIQUID;

        }
        else if (thisMove.from.inPowderSnow) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_POWDER_SNOW;
        }
        else if (thisMove.from.inWeb) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_WEBS; 
        }
        else if (thisMove.from.inBerryBush) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_SWEET_BERRY;
        }
        else if (thisMove.from.onHoneyBlock) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_HONEY_BLOCK;
        }
        else if (resetFrom || thisMove.touchedGround) {
            data.liftOffEnvelope = LiftOffEnvelope.NORMAL;
        }
        else {
            // Air, Keep medium.
        }

        // 2: Count how long one is moving inside of a medium.
        if (oldLiftOffEnvelope != data.liftOffEnvelope) {
            data.insideMediumCount = 0;
        }
        else if (!resetFrom || !resetTo) {
            data.insideMediumCount = 0;
        }
        else data.insideMediumCount ++;

        // Apply reset conditions.
        if (resetTo) {
            // Reset data.
            data.setSetBack(to);
            data.sfJumpPhase = 0;
            if (hFreedom <= 0.0 && thisMove.verVelUsed == null) {
                data.resetVelocityJumpPhase(tags);
            }
        }
        // The player moved from ground.
        else if (resetFrom) {
            data.setSetBack(from);
            data.sfJumpPhase = 1; // This event is already in air.
        }
        else {
            data.sfJumpPhase ++;
            // TODO: Void-to-void: Rather handle unified somewhere else (!).
            if (to.getY() < 0.0 && cc.sfSetBackPolicyVoid
                || thisMove.hasLevitation) {
                data.setSetBack(to);
            }
        }
        
        // Adjust in-air counters.
        if (inAir) {
            if (yDistance == 0.0) {
                data.sfZeroVdistRepeat ++;
            }
            else data.sfZeroVdistRepeat = 0;
        }
        else {
            data.sfZeroVdistRepeat = 0;
            data.ws.resetConditions(WRPT.G_RESET_NOTINAIR);
            data.sfVLInAir = false;
        }

        // Update unused velocity tracking.
        // TODO: Hide and seek with API.
        // TODO: Pull down tick / timing data (perhaps add an API object for millis + source + tick + sequence count (+ source of sequence count).
        if (debug) {
            // TODO: Only update, if velocity is queued at all.
            data.getVerticalVelocityTracker().updateBlockedState(tick, 
                    // Assume blocked with being in web/water, despite not entirely correct.
                    thisMove.headObstructed || thisMove.from.resetCond,
                    // (Similar here.)
                    thisMove.touchedGround || thisMove.to.resetCond);
            // TODO: TEST: Check unused velocity here too. (Should have more efficient process, pre-conditions for checking.)
            UnusedVelocity.checkUnusedVelocity(player, type, data, cc);
        }
      
        // Adjust various speed/friction factors (both h/v).
        data.lastFrictionVertical = data.nextFrictionVertical;
        data.lastFrictionHorizontal = data.nextFrictionHorizontal;
        data.lastStuckInBlockVertical = data.nextStuckInBlockVertical;
        data.lastStuckInBlockHorizontal = data.nextStuckInBlockHorizontal;
        data.lastBlockSpeedMultiplier = data.nextBlockSpeedMultiplier;
        data.lastInertia = data.nextInertia;

        // Log tags added after violation handling.
        if (debug && tags.size() > tagsLength) {
            logPostViolationTags(player);
        }
        // Nothing to do, newTo (MovingListener) stays null
        return null;
    }
    




 
    /**
     * A check to prevent players from bed-flying.
     * To be called on PlayerBedLeaveEvent(s)
     * (This increases VL and sets tag only. Setback is done in MovingListener).
     * 
     * @param player
     *            the player
     * @param pData
     * @param cc
     * @param data
     * @return If to prevent action (use the set back location of survivalfly).
     */
    public boolean checkBed(final Player player, final IPlayerData pData, final MovingConfig cc, final MovingData data) {
        boolean cancel = false;
        // Check if the player had been in bed at all.
        if (!data.wasInBed) {
            // Violation ...
            tags.add("bedfly");
            data.survivalFlyVL += 100D;
            final ViolationData vd = new ViolationData(this, player, data.survivalFlyVL, 100D, cc.survivalFlyActions);
            if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            cancel = executeActions(vd).willCancel();
        } 
        // Nothing detected.
        else data.wasInBed = false;
        return cancel;
    }


    /**
     * Check for push/pull by pistons, alter data appropriately (blockChangeId).
     * 
     * @param yDistance
     * @param from
     * @param to
     * @param data
     * @return
     */
    private double[] getVerticalBlockMoveResult(final double yDistance, 
                                                final PlayerLocation from, final PlayerLocation to, 
                                                final int tick, final MovingData data) {
        /*
         * TODO: Pistons pushing horizontally allow similar/same upwards
         * (downwards?) moves (possibly all except downwards, which is hard to
         * test :p).
         */
        // TODO: Allow push up to 1.0 (or 0.65 something) even beyond block borders, IF COVERED [adapt PlayerLocation].
        // TODO: Other conditions/filters ... ?
        // Push (/pull) up.
        if (yDistance > 0.0) {
            if (yDistance <= 1.015) {
                /*
                 * (Full blocks: slightly more possible, ending up just above
                 * the block. Bounce allows other end positions.)
                 */
                // TODO: Is the air block wich the slime block is pushed onto really in? 
                if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, Direction.Y_POS, Math.min(yDistance, 1.0))) {
                    if (yDistance > 1.0) {
                        if (to.getY() - to.getBlockY() >= 0.015) {
                            // Exclude ordinary cases for this condition.
                            return null;
                        }
                    }
                    tags.add("blkmv_y_pos");
                    final double maxDistYPos = yDistance; //1.0 - (from.getY() - from.getBlockY()); // TODO: Margin ?
                    return new double[]{maxDistYPos, 0.0};
                }
            }
        }
        // Push (/pull) down.
        else if (yDistance < 0.0 && yDistance >= -1.0) {
            if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, Direction.Y_NEG, -yDistance)) {
                tags.add("blkmv_y_neg");
                final double maxDistYNeg = yDistance; // from.getY() - from.getBlockY(); // TODO: Margin ?
                return new double[]{maxDistYNeg, 0.0};
            }
        }
        // Nothing found.
        return null;
    }
    
    
   /** 
    * Estimate the player's horizontal speed.
    * @return 
    */ 
   private void estimateNextSpeed(final Player player, float movementSpeed, final IPlayerData pData, 
                                  final boolean sneaking, final Collection<String> tags, 
                                  final PlayerLocation to, final PlayerLocation from, final boolean debug,
                                  final boolean fromOnGround, final boolean toOnGround, 
                                  final boolean onGround, final PlayerMoveData lastMove, 
                                  final int tick, boolean useBlockChangeTracker) {
        final MovingData data = pData.getGenericInstance(MovingData.class);      
        final CombinedData cData = pData.getGenericInstance(CombinedData.class); 
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);   
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final boolean sneakingOnGround = onGround && player.isSneaking() && reallySneaking.contains(player.getName());
        /*
         * NOTES: Attack-slowdown is done in the FightListener (!).
         * Order of operations is essential. Do not shuffle things around unless you know what you're doing.
         * MISSING:
         *   - maybeBackOffFromEdge method
         *   - wall collision momentum reset
         *   - Push-out-of-block mechanic (moveTowardsClosestSpace)
         *      Actually, this should be handled by Passable instead: SurvivalFly won't run if Passable triggers, so we wouldn't have to deal with this function.
         *   - A proper sneaking handling, which seems to be broken somehow. 
         *   - We absolutely need to code a global latency-compensation system (not just LocationTrace) as well some framework against desync issues.
         * IMPORTANT: The 1.20 update fixes the 12 year old bug of walking/moving on the very edge blocks not applying the properties of said block
         *            - HoneyBlocks will now restrict jumping even if on the very edge
         *            - Same for slime and everything else
         * Order in mcp: 
         * -> LivingEntity.tick() 
         * -> Entity.tick() 
         *    -> Entity.baseTick()
         *    -> Entity.updateInWaterStateAndDoFluidPushing()
         * -> LivingEntity.aiStep()
         *    -> Negligible speed(0.003)
         *    -> LivingEntity.jumpFromGround()
         * -> LivingEntity.travel()
         * -> Entity.moveRelative 
         * -> Entity.move()
         *    -> maybeBackOffFromEdge, 
         *    -> wall collision(Entity.collide()), 
         *    (-> Complete the move, prepare next move)
         *    -> horizontalCollision - next move                   <--------------------
         *    -> checkFallDamage(do liquid puishing if was not previosly in water) - next move
         *    -> Block.updateEntityAfterFallOn() - next move
         *    -> Block.stepOn() (for slime block) - next move
         *    -> tryCheckInsideBlocks() (for honey block) - next move
         *    -> Entity.getBlockSpeedFactor() - next move
         * -> LivingEntity : Friction(inertia) - next move - complete running the travel function
         * -> Entity pushing - next move - complete running the aiStep() function
         * -> Complete running the LivingEntity.tick() function
         * -> Repeat
         * 
         * From the order above, we will start the order from horizontalCollision and going down then back up
         */

        // Initialize the allowed distance(s) with the previous speed. (Only if we have end-point coordinates)
        // This essentially represents the momentum of the player.
        thisMove.xAllowedDistance = lastMove.toIsValid ? lastMove.to.getX() - lastMove.from.getX() : 0.0;
        thisMove.zAllowedDistance = lastMove.toIsValid ? lastMove.to.getZ() - lastMove.from.getZ() : 0.0;

        // Because Minecraft does not offer any way to listen to player's inputs, we brute force through all combinations of movement and see which one matches the current speed of the player.
        InputDirection directions[] = new InputDirection[9];
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                // Minecraft multiplies the input values by 0.98 before passing them to the travel() function.
                directions[i] = new InputDirection(x * 0.98f, z * 0.98f);
                i++;
            }
        }

        // From KeyboardInput.java (MC-Reborn tool)
        if (sneaking) {
            tags.add("sneaking");
            // (Formula is from LocalPlayer aiStep)
            float SwiftSneakIncrement = MathUtil.clamp(BridgeEnchant.getSwiftSneakLevel(player) * 0.15f, 0.0f, 1.0f);
            for (i = 0; i < 9; i++) {
                // Multiply all combinations
                directions[i].calculateDir(Magic.SNEAK_MULTIPLIER + SwiftSneakIncrement, Magic.SNEAK_MULTIPLIER + SwiftSneakIncrement, 1);
            }
        }
        // From LocalPlayer.java.aiStep()
        if (cData.isUsingItem || player.isBlocking()) {
            tags.add("usingitem");
            for (i = 0; i < 9; i++) {
                directions[i].calculateDir(Magic.USING_ITEM_MULTIPLIER, Magic.USING_ITEM_MULTIPLIER, 1);
            }
        }

        // Collision reset.
        if (from.isNextToSolid(0.0001, 0.0)) {
            thisMove.xAllowedDistance = 0.0;
        }
        if (from.isNextToSolid(0.0, 0.0001)) {
            thisMove.zAllowedDistance = 0.0;
        }

        // (Calling from checkFallDamage() in vanilla)
        if (from.isInWater() && !lastMove.from.inWater) {
            Vector liquidFlowVector = from.getLiquidPushingVector(thisMove.xAllowedDistance, thisMove.zAllowedDistance, BlockFlags.F_WATER);
            thisMove.xAllowedDistance += liquidFlowVector.getX();
            thisMove.zAllowedDistance += liquidFlowVector.getZ();
        }
        
        // Slime speed
        // From BlockSlime.java
        // (Ground check is already included)
        if (from.isOnSlimeBlock()) {
            // (Not checking for client version here. If you're still using 1.7, what are you doing, my guy.)
            if (Math.abs(thisMove.yDistance) < 0.1 && !sneaking) { // -0.0784000015258789
                thisMove.xAllowedDistance *= 0.4 + Math.abs(thisMove.yDistance) * 0.2;
                thisMove.zAllowedDistance *= 0.4 + Math.abs(thisMove.yDistance) * 0.2;
            }
        }

        // Sliding speed (honey block)
        if (from.isSlidingDown()) {
            if (lastMove.yDistance < -Magic.SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD) {
                thisMove.xAllowedDistance *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
                thisMove.zAllowedDistance *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
            }
        }

        // Stuck speed reset (the game resets momentum each tick the player is in a stuck-speed block)
        //if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7D) {
        //    p_19974_ = p_19974_.multiply(this.stuckSpeedMultiplier);
        //    this.stuckSpeedMultiplier = Vec3.ZERO;
        //    this.setDeltaMovement(Vec3.ZERO);
        //}
        if (data.lastStuckInBlockHorizontal != 1.0) { // (Use inequality check. Powder snow has a 1.5 factor)
            thisMove.xAllowedDistance = thisMove.zAllowedDistance = 0.0;
        }

        // Block speed
        thisMove.xAllowedDistance *= (double) data.nextBlockSpeedMultiplier;
        thisMove.zAllowedDistance *= (double) data.nextBlockSpeedMultiplier;

        // Friction next.
        thisMove.xAllowedDistance *= (double) data.lastInertia;
        thisMove.zAllowedDistance *= (double) data.lastInertia;

        // If sneaking, the game moves the player by steps to check if they reach an edge to back them off.
        // From EntityHuman, maybeBackOffFromEdge
        // (Do this before the negligible speed threshold reset)
        // TODO: IMPLEMENT

        // Apply entity-pushing speed
        // From Entity.java.push()
        if (pData.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9) 
            && CollisionUtil.isCollidingWithEntities(player, true)) {
            for (Entity entity : player.getNearbyEntities(0.01, 0.0, 0.01)) {
                if (!entity.isValid() || MaterialUtil.isBoat(entity.getType()) 
                    || entity.getType() == EntityType.ARMOR_STAND) {
                    continue;
                    // There could be other/alive entities, don't break. (dead entities are taken into account in isCollidingWithEntities)
                }
                final Location eLoc = entity.getLocation(useLoc);
                double xDistToEntity = eLoc.getX() - from.getX();
                double zDistToEntity = eLoc.getZ() - from.getZ();
                double absDist = MathUtil.absMax(xDistToEntity, zDistToEntity);
                // Cleanup
                useLoc.setWorld(null);
                if (absDist >= 0.009) {
                    absDist = Math.sqrt(absDist);
                    xDistToEntity /= absDist;
                    zDistToEntity /= absDist;
                    double var8 = Math.min(1.f, 1.f / absDist);
                    xDistToEntity *= var8;
                    zDistToEntity *= var8;
                    xDistToEntity *= 0.05;
                    zDistToEntity *= 0.05;
                    // Push
                    thisMove.xAllowedDistance -= xDistToEntity;
                    thisMove.zAllowedDistance -= zDistToEntity;
                    break;
                }
            }
        }

        if (from.isInLiquid()) {
            // Apply liquid pushing speed (2nd call).
            Vector liquidFlowVector = from.getLiquidPushingVector(thisMove.xAllowedDistance, thisMove.zAllowedDistance, from.isInWater() ? BlockFlags.F_WATER : BlockFlags.F_LAVA);
            thisMove.xAllowedDistance += liquidFlowVector.getX();
            thisMove.zAllowedDistance += liquidFlowVector.getZ();
        }

        // Before calculating the acceleration, check if momentum is below the negligible speed threshold and cancel it.
        if (pData.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            if (Math.abs(thisMove.xAllowedDistance) < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                thisMove.xAllowedDistance = 0.0;
            }
            if (Math.abs(thisMove.zAllowedDistance) < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                thisMove.zAllowedDistance = 0.0;
            }
        } 
        else {
            // In 1.8 and lower, momentum is compared to 0.005 instead.
            if (Math.abs(thisMove.xAllowedDistance) < Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY) {
                thisMove.xAllowedDistance = 0.0;
            }
            if (Math.abs(thisMove.zAllowedDistance) < Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY) {
                thisMove.zAllowedDistance = 0.0;
            }
        }

        // Modifiers to be applied on normal ground only.
        if (!from.isInLiquid()) {
            if (PlayerEnvelopes.isBunnyhop(data, useBlockChangeTracker && from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick), player.isSprinting(), sneaking, fromOnGround, toOnGround)) {
                thisMove.xAllowedDistance += (double) (-TrigUtil.sin(to.getYaw() * TrigUtil.toRadians) * Magic.BUNNYHOP_ACCEL_BOOST); 
                thisMove.zAllowedDistance += (double) (TrigUtil.cos(to.getYaw() * TrigUtil.toRadians) * Magic.BUNNYHOP_ACCEL_BOOST); 
                data.bunnyhopDelay = Magic.BUNNYHOP_MAX_DELAY;
                thisMove.bunnyHop = true;
                tags.add("bunnyhop");
                // Keep up the Improbable, but don't feed anything.
                Improbable.update(System.currentTimeMillis(), pData);
            }
            if (from.isOnClimbable() && lastMove.from.onClimbable) {
                // Minecraft caps horizontal speed if on climbable, for whatever reason.
                thisMove.xAllowedDistance = MathUtil.clamp(thisMove.xAllowedDistance, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
                thisMove.zAllowedDistance = MathUtil.clamp(thisMove.zAllowedDistance, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
            }
        }
        
        // Transform the input to a speed vector and then add the acceleration. (getInputVector, entity.java)
        float sinYaw = TrigUtil.sin(to.getYaw() * TrigUtil.toRadians);
        float cosYaw = TrigUtil.cos(to.getYaw() * TrigUtil.toRadians);
        /** List of estimated X distances. Size is the number of possible inputs (left/right/backwards/forward etc...) */
        double xAllowedDistances[] = new double[9];
        /** List of estimated Z distances. Size is the number of possible inputs (left/right/backwards/forward etc...) */
        double zAllowedDistances[] = new double[9];
        for (i = 0; i < 9; i++) {
            // First, put the momentum that has been calculated thus far in each array slot.
            xAllowedDistances[i] = thisMove.xAllowedDistance;
            zAllowedDistances[i] = thisMove.zAllowedDistance;
            // Proceed to compute all possible accelerations with all input combos.
            double inputSq = MathUtil.square((double)directions[i].getStrafe()) + MathUtil.square((double)directions[i].getForward()); // Cast to a double because the client does it
            if (inputSq >= 1.0E-7) {
                if (inputSq > 1.0) {
                    double inputForce = Math.sqrt(inputSq);
                    if (inputForce < 1.0E-4) {
                        // Not enough force, reset.
                        directions[i].calculateDir(0, 0, 0);
                    }
                    else {
                        // Normalize
                        directions[i].calculateDir(inputForce, inputForce, 2);
                    }
                }
                // Multiply inputs by movement speed.
                directions[i].calculateDir(movementSpeed, movementSpeed, 1);
                // Add all accelerations to the momentum
                xAllowedDistances[i] += directions[i].getStrafe() * (double)cosYaw - directions[i].getForward() * (double)sinYaw;
                zAllowedDistances[i] += directions[i].getForward() * (double)cosYaw + directions[i].getStrafe() * (double)sinYaw;
            }
        }

        // Stuck speed after update for accuracy's sake.
        for (i = 0; i < 9; i++) {
            xAllowedDistances[i] *= (double) data.nextStuckInBlockHorizontal;
            zAllowedDistances[i] *= (double) data.nextStuckInBlockHorizontal;
        }

        /** 
         * True will check X/Z axis individually (against strafe-like cheats and anything of that sort that relies on the specific direction of the move).
         * Will also perform some auxiliary checks.
         * Otherwise, check using the horizontal distance.
         */
        boolean strict = true; 
        /** 
         * True, if the difference between estimated and actual speed is smaller than the accuracy margin (0.0001).
         * In this case, speed will be set in thisMove, otherwise, it will be left to 0.
         */
        boolean found = false;
        for (i = 0; i < 9; i++) {
            // Calculate all possible hDistances
            double hDistanceInList = MathUtil.dist(xAllowedDistances[i], zAllowedDistances[i]);
            if (strict) {
                if (Math.abs(to.getX() - from.getX() - xAllowedDistances[i]) < 0.0001 && Math.abs(to.getZ() - from.getZ() - zAllowedDistances[i]) < 0.0001) {
                    if (player.isSprinting() 
                        && (directions[i].getForwardDir() != ForwardDirection.FORWARD && directions[i].getStrafeDir() != StrafeDirection.NONE
                            || player.getFoodLevel() <= 5)) {
                        // Assume cheating; if sprinting sideways or backwards (or without enough stamina), this speed is no candidate to set in thisMove.
                        tags.add("illegalsprint");
                        Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
                    } 
                    else if (cData.isHackingRI) {
                        // Blatant cheat attempt, do not set speed.
                        cData.isHackingRI = false;
                        Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
                        tags.add("noslowpacket");
                    }
                    // TODO: Fix. Receiving blindness while already sprinting does not break sprint. Great job mojang.
                    /*else if (player.hasPotionEffect(PotionEffectType.BLINDNESS) 
                            && player.isSprinting() 
                            && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))) {
                        Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
                        tags.add("blindsprint");
                    }*/
                    else {
                        found = true;
                    }
                }
            } 
            else {
                // Simply compare the overall speed otherwise.
                if (Math.abs(hDistanceInList - thisMove.hDistance) < 0.0001) { 
                    found = true;
                }
            }
            if (found) {
                if (debug) {
                    player.sendMessage("[SurvivalFly] (estimateNextSpeed) Estimated direction: " + directions[i].getForwardDir() +" | "+ directions[i].getStrafeDir());
                }
                // Found a candidate.
                break;
            }
        }
        if (i > 8) {
            i = 4;    
            if (debug) {
                player.sendMessage("[SurvivalFly] (estimateNextSpeed) Can not find correct direction, set default: NONE | NONE");
            }
        }
        thisMove.xAllowedDistance = xAllowedDistances[i];
        thisMove.zAllowedDistance = zAllowedDistances[i];
   }


    /**
     * Core h-distance checks for media and all status
     * @return hAllowedDistance, hDistanceAboveLimit
     */
    private final double[] hDistRel(final PlayerLocation from, final PlayerLocation to, final IPlayerData pData, final Player player, 
                                       final MovingData data, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingConfig cc,
                                       final int tick, final boolean useBlockChangeTracker,
                                       final boolean fromOnGround, final boolean toOnGround, final boolean debug, final int multiMoveCount, 
                                       final boolean isNormalOrPacketSplitMove) {
        final boolean sneaking = player.isSneaking() && reallySneaking.contains(player.getName());
        // TODO: New problem here, the onGround does not always reflect correct state with the client, cause fp 
        // Do we need to add lost-ground/false-on-ground cases for horizontal speed as well !?
        final boolean onGround = thisMove.touchedGroundWorkaround || (lastMove.toIsValid && lastMove.from.onGround && lastMove.yDistance <= 0.0) || from.isOnGround() || from.isOnGroundDueToStandingOnAnEntity();
        double hDistanceAboveLimit = 0.0;

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Determine if the bunnyhop delay should be reset earlier (bunnyfly). These checks need to run before the estimation         //                  
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // (NCP mechanic, not vanilla. We decide when players can bunnyhop because we use our own collision system for on ground judgement)
        if (data.bunnyhopDelay > 0) {
            if (data.bunnyhopDelay <= 9) {
                // (10 represents a bunnyhop)
                tags.add("bunnyfly(" + data.bunnyhopDelay + ")");
            }
            
            // Reset cases:
            // (Do note that this uses a much lower leniency margin, unlike thisMove#headObstructed, it only considers the nearest point of collision)
            if (from.isHeadObstructed(0.005, false)) {
                // Reset the delay on head bump, because the player will end up back on ground sooner than usual.
                // Here (theoretically), a cheater could -after bumping head with a solid block- attempt to descend faster than legit to get a new bunnyhop boost. (thus increasing horizontal speed at faster rate than legit).
                // Catching irregular vertical motions is the job of vDistRel however. 
                data.bunnyhopDelay = 0;
            }
            else if (from.isAboveStairs() && to.isNextToBlock(0.3, 0.1, BlockFlags.F_STAIRS) && !lastMove.bunnyHop
                     && thisMove.to.getY() > data.playerMoves.getPastMove(Magic.BUNNYHOP_MAX_DELAY - data.bunnyhopDelay).from.getY()) {
                // Only if the player is next to a flight of stairs and current altitude is higher than the bunnyhop altitude
                // NOTE: Margins are random. Perhaps the horizontal one (0.3) is a bit much
                data.bunnyhopDelay = 0;
            }
            else if (fromOnGround && !lastMove.bunnyHop) {
                // Jump delay lasts less on/in these blocks
                if (from.isOnHoneyBlock() && data.bunnyhopDelay <= 4
                    || from.isInBerryBush() && data.bunnyhopDelay <= 3
                    || from.isInWeb() && data.bunnyhopDelay <= 7) {
                    data.bunnyhopDelay = 0;
                }
                else if (data.bunnyhopDelay <= 7 && !thisMove.headObstructed 
                        // The player actually jumped up a slope: this movement has a higher altitude than the bunnyhop move.
                        && thisMove.from.getY() > data.playerMoves.getPastMove(Magic.BUNNYHOP_MAX_DELAY - data.bunnyhopDelay).from.getY()) {
                    // (Ground check is checked above)
                    data.bunnyhopDelay = 0;
                }
            }
        }


        //////////////////////////////////////////////////////////////
        // Estimate the horizontal speed (per-move distance check)  //                      
        //////////////////////////////////////////////////////////////
        if (isNormalOrPacketSplitMove) { // || !tryingToGetOutOfBlock) {
            // Only check 'from' to spare some problematic transitions between media (i.e.: in 1.13+ with players being able to swim up to the surface and have 2 in-air moves)
            if (from.isInWater()) {
                data.nextInertia = Bridge1_13.isSwimming(player) ? Magic.HORIZONTAL_SWIMMING_INERTIA : Magic.WATER_HORIZONTAL_INERTIA;
                /** Per-tick speed gain. */      
                float acceleration = Magic.LIQUID_BASE_ACCELERATION;
                float StriderLevel = (float) BridgeEnchant.getDepthStriderLevel(player); 
                if (!onGround) {
                    StriderLevel *= Magic.STRIDER_OFF_GROUND_MULTIPLIER;
                }
                if (StriderLevel > 0.0) {
                    // (Less speed conservation (or in other words, more friction))
                    data.nextInertia += (0.54600006f - data.nextInertia) * StriderLevel / 3.0f;
                    // (More per-tick speed gain)
                    acceleration += (data.walkSpeed - acceleration) * StriderLevel / 3.0f;
                }
                if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
                    // (Much more speed conservation (or in other words, much less friction))
                    // (Overrides swimming AND depth strider friction)
                    data.nextInertia = Magic.DOLPHIN_GRACE_INERTIA; 
                }
                // Run through all operations
                estimateNextSpeed(player, acceleration, pData, sneaking, tags, to, from, debug, fromOnGround, toOnGround, onGround, lastMove, tick, useBlockChangeTracker);
            }
            else if (from.isInLava()) {
                data.nextInertia = Magic.LAVA_HORIZONTAL_INERTIA; 
                estimateNextSpeed(player, Magic.LIQUID_BASE_ACCELERATION, pData, sneaking, tags, to, from, debug, fromOnGround, toOnGround, onGround, lastMove, tick, useBlockChangeTracker);
            }
            else {
                data.nextInertia = onGround ? data.nextFrictionHorizontal * Magic.HORIZONTAL_INERTIA : Magic.HORIZONTAL_INERTIA;
                // 1.12 (and below) clients will use cubed inertia, not cubed friction here. The difference isn't significant except for blocking speed and bunnyhopping on soul sand, which are both slower on 1.8
                float acceleration = onGround ? data.walkSpeed * ((pData.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13) ? Magic.DEFAULT_FRICTION_CUBED : Magic.CUBED_INERTIA) / (data.nextFrictionHorizontal * data.nextFrictionHorizontal * data.nextFrictionHorizontal)) : Magic.AIR_ACCELERATION;
                if (player.isSprinting()) {//&& !sneaking) {
                    // NOTE: (Apparently players can now sprint while sneaking !?)
                    // (We don't use the attribute here due to desync issues, just detect when the player is sprinting and apply the multiplier manually)
                    // Multiplying by 1.3f seems to cause precision loss, so use the total multiply result.
                    acceleration += acceleration * 0.3f; // 0.3 is the effective sprinting speed (EntityLiving).
                }
                estimateNextSpeed(player, acceleration, pData, sneaking, tags, to, from, debug, fromOnGround, toOnGround, onGround, lastMove, tick, useBlockChangeTracker);
            }
        }
        else {
            // Bukkit-based split move: predicting the next speed is not possible due to coordinates not being reported correctly by Bukkit (and without ProtocolLib, it's nearly impossible to achieve precision here)
            // Besides, no need to predict speed for a move that has been slowed down so much to the point of being considered micro.
            thisMove.xAllowedDistance = thisMove.to.getX() - thisMove.from.getX();
            thisMove.zAllowedDistance = thisMove.to.getZ() - thisMove.from.getZ();
            if (debug) {
                debug(player, "(hDistRel): Missed PlayerMoveEvent(s) between 'from' and 'to' (Bukkit): skip prediction for this event.");
            }
        }
        
        /** Expected difference from current to allowed */
        final double hDistDiffEx = thisMove.hDistance - MathUtil.dist(thisMove.xAllowedDistance, thisMove.zAllowedDistance);
        if (hDistDiffEx < 0.0001) {
            // Only set the distance in this move, if speed is lower than estimated or within the accuracy margin.
            // (Judging from some testings, we could be even stricter)
            thisMove.hAllowedDistance = MathUtil.dist(thisMove.xAllowedDistance, thisMove.zAllowedDistance);
        }
        else {
            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, hDistDiffEx);
            tags.add("hdistrel");
        }
        if (debug) {
            // TODO: REMOVE 
            player.sendMessage((Math.abs(thisMove.hDistance - thisMove.hAllowedDistance) < 0.0001 ? "" : "[-----]") + "c/e: " + thisMove.hDistance + " / " + thisMove.hAllowedDistance);
        }
        return new double[]{thisMove.hAllowedDistance, hDistanceAboveLimit};
    }


    /**
     * Access method from outside.
     * 
     * @param player
     * @return
     */
    public boolean isReallySneaking(final Player player) {
        return reallySneaking.contains(player.getName());
    }


    /**
     * Core y-distance checks for in-air movement.
     */
    private double[] vDistRel(final long now, final Player player, final PlayerLocation from, 
                              final boolean fromOnGround, final boolean resetFrom, final PlayerLocation to, 
                              final boolean toOnGround, final boolean resetTo, 
                              final double hDistance, final double yDistance, 
                              final int multiMoveCount, final PlayerMoveData lastMove, 
                              final MovingData data, final MovingConfig cc, final IPlayerData pData) {
        double vDistanceAboveLimit = 0.0;
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData secondLastMove = data.playerMoves.getSecondPastMove();
        /** Change seen from last y distance */
        final double yDistChange = lastMove.toIsValid ? yDistance - lastMove.yDistance : Double.MAX_VALUE;
        final double minJumpGain = data.liftOffEnvelope.getJumpGain(data.jumpAmplifier, data.lastStuckInBlockVertical);
        final int maxJumpPhase = data.liftOffEnvelope.getMaxJumpPhase(data.jumpAmplifier);
        final double maxJumpHeight = data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier);
        
        ///////////////////////////////////////////////////////////////////////////////////
        // Estimate the allowed yDistance (per-move distance check)                      //
        ///////////////////////////////////////////////////////////////////////////////////
        /**
         * 
         * MISSING: Back-to-ground speed collision logic (not sure if it's even possible to do on NCP's side, would require to port Minecraft's collision logic...)
         * - Same could be told for bumping head in a 2-blocks high space (the player jumps with 0.1 speed). Likely tied to noobtowering up as well.
         * - Medium transitions ?
         * - Aim to finally be independent from oddGravity workarounds as well.
         * 
         * TODO: Lost-ground cases won't cut it if we're going to overhaul vdistrel as well. 
         * We need the *exact* ground collision. Or at least a really close estimate to Minecraft's to reduce workarounds to a bare minimum.
         * 
         * TODO: Not sure: re-code NCP's bounce handling and predict that too instead of relying to velocity?
         * 
         * 
         * 
         */
        // Handle ordinary moves.
        boolean hasLevitation = !Double.isInfinite(Bridge1_9.getLevitationAmplifier(player)) && pData.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9);
        if (PlayerEnvelopes.isStep(data, cc.sfStepHeight, tags.contains("lostground_couldstep"))
            || PlayerEnvelopes.isJump(data, hasLevitation, minJumpGain, from.isHeadObstructed(0.03, false))) {
            // Give priority to stepping.
            thisMove.vAllowedDistance = yDistance;
        }
        else if (from.isInPowderSnow() && thisMove.yDistance > 0.0 && BridgeMisc.hasLeatherBootsOn(player)) {
            // Climbing inside powder snow... Non vanilla. 
            thisMove.vAllowedDistance = Magic.snowClimbSpeedAscend;
        }
        else if (yDistance == 0.0 && thisMove.setBackYDistance == 0.0 && (thisMove.from.onGround || thisMove.touchedGroundWorkaround)) {
            // Leaving ground, but with negative motion: stepping down a slope, not actually jumping
            thisMove.vAllowedDistance = 0.0;
        }
        else if (!fromOnGround && toOnGround && thisMove.yDistance < 0.0) {
            // TODO: Handle this stupid move in a way that can prevent 1-block step cheats and also be reliable enough.
            // Allow the movement for the moment.
            thisMove.vAllowedDistance = thisMove.yDistance;
        }
        else {
            // Otherwise, a fully in-air move (friction).
            // Initialize with momentum.
            thisMove.vAllowedDistance = lastMove.toIsValid ? lastMove.yDistance : 0.0;
            // Honey block sliding mechanic (With levitation, the player will ascend)
            if (from.isSlidingDown() && !hasLevitation) {
                if (lastMove.yDistance < -Magic.SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD) {
                    // Speed is static in this case
                    thisMove.vAllowedDistance = -Magic.SLIDE_SPEED_THROTTLE;
                }
            }
            if (data.lastStuckInBlockVertical != 1.0) {
                // Stuck-speed works by resetting speed every tick the player is in such a block. Thus, players are expected to immediately descend after jumping (with static speed)
                thisMove.vAllowedDistance = 0.0;
            }
            if (Math.abs(thisMove.vAllowedDistance) < (pData.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9) ? Magic.NEGLIGIBLE_SPEED_THRESHOLD : Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY)) {
                // Negligible speed reset.
                thisMove.vAllowedDistance = 0.0;
            }
            if (hasLevitation) {
                // Levitation forces players to ascend and does not work in liquids, so thankfully we don't have to account for that, other than stuck-speed.
                // TODO: This estimate tends to break with higher levitation level for whatever reason.
                thisMove.vAllowedDistance += (0.05 * (Bridge1_9.getLevitationAmplifier(player) + 1) - lastMove.yDistance) * 0.2;
                if (AirWorkarounds.oddLevitation(data, player, from, fromOnGround)) {
                    thisMove.vAllowedDistance = thisMove.yDistance;
                }
            }
            if (BridgeMisc.hasGravity(player) && !hasLevitation) {
                // Only apply gravity if the player can be affected by it (slowfall simply reduces gravity)
                thisMove.vAllowedDistance -= thisMove.hasSlowfall && thisMove.yDistance <= 0.0 ? Magic.DEFAULT_SLOW_FALL_GRAVITY : Magic.DEFAULT_GRAVITY;
            }
            // Friction and stuck-speed
            thisMove.vAllowedDistance *= data.lastFrictionVertical;
            thisMove.vAllowedDistance *= (double) data.nextStuckInBlockVertical;
            // Head collision is done in AirWorkarounds for the moment.
        }

        /** Expected difference from current to allowed */       
        final double yDistDiffEx = yDistance - thisMove.vAllowedDistance; 
        /** More workarounds */
        final boolean gravityEffects = AirWorkarounds.oddJunction(from, to, yDistChange, yDistDiffEx, resetTo, data, cc, resetFrom, player, fromOnGround, toOnGround);
        if (Math.abs(yDistDiffEx) < 0.0001) {
            // Accuracy margin.
        }
        else if (gravityEffects) {
            // Non-predictable movement / non-ordinary moves.
        }
        else {
            // No match found (unexpected move): use velocity as compensation, if available.
            if (data.getOrUseVerticalVelocity(yDistance) == null) {
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(yDistance - thisMove.vAllowedDistance));
                tags.add("vdistrel");
            }
        }
        
    
        // (All the checks below are to be considered a corollary to vDistRel. Virtually, the cheat attempt will always get caught by vDistRel first)
        ///////////////////////////////////////////
        // Check on change of Y direction 
        ///////////////////////////////////////////
        final boolean InAirPhase = !resetFrom && !resetTo;
        final boolean ChangedYDir = lastMove.toIsValid && lastMove.yDistance != yDistance && (yDistance <= 0.0 && lastMove.yDistance >= 0.0 || yDistance >= 0.0 && lastMove.yDistance <= 0.0); 

        if (InAirPhase && ChangedYDir) {
            // (Does this account for velocity in a sufficient way?)
            if (yDistance > 0.0) {
                // TODO: Demand consuming queued velocity for valid change (!).
                if (lastMove.touchedGround || lastMove.to.extraPropertiesValid && lastMove.to.resetCond) {
                    // Change to increasing phase
                    tags.add("ychinc");
                }
                else {
                    // Moving upwards after falling without having touched the ground.
                    if (data.bunnyhopDelay < 9 && !((lastMove.touchedGround || lastMove.from.onGroundOrResetCond) && lastMove.yDistance == 0D) 
                        && data.getOrUseVerticalVelocity(yDistance) == null) {
                        vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(yDistance));
                        tags.add("airjump");
                    }
                    else tags.add("ychincair");
                }
            }
            else {
                // Change to decreasing phase.
                tags.add("ychdec");
                // (Previously: a low-jump check would have been performed here, let vDistRel catch it instead.)
            }
        }

        ///////////////////////////////////////////////////////////////////////////////////////
        // Air-stay-time: prevent players from ascending further than the maximum jump phase.//
        ///////////////////////////////////////////////////////////////////////////////////////
        // (I guess this check could stay: it does not require much maintenance nor many workarounds to account for.)
        if (data.sfJumpPhase > maxJumpPhase && !data.isVelocityJumpPhase()) {
            if (yDistance < Magic.GRAVITY_MIN) {
                // Ignore falling, and let accounting deal with it.
                // (Use minimum gravity as a mean of leniency)
            }
            else if (resetFrom || thisMove.touchedGroundWorkaround) {
                // Ignore bunny etc.
            }
            // Violation (Too high jumping or step).
            else if (data.getOrUseVerticalVelocity(yDistance) == null) {
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, data.sfJumpPhase - maxJumpPhase);
                tags.add("maxphase("+ data.sfJumpPhase +"/"+ maxJumpPhase + ")");
            }
        }
        return new double[]{thisMove.vAllowedDistance, vDistanceAboveLimit};
    }


    /**
     * After-failure checks for horizontal distance.
     * Velocity, block move and reset-item.
     * 
     * @param player
     * @param multiMoveCount
     * @param from
     * @param to
     * @param hAllowedDistance
     * @param hDistanceAboveLimit
     * @param thisMove
     * @param lastMove
     * @param debug
     * @param data
     * @param cc
     * @param pData
     * @param tick
     * @param useBlockChangeTracker
     * @param fromOnGround
     * @param toOnGround
     * @param isNormalOrPacketSplitMove
     * @return hAllowedDistance, hDistanceAboveLimit, hFreedom
     */
    private double[] hDistAfterFailure(final Player player, final int multiMoveCount,
                                       final PlayerLocation from, final PlayerLocation to, 
                                       double hAllowedDistance, double hDistanceAboveLimit, 
                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove, final boolean debug,
                                       final MovingData data, final MovingConfig cc, final IPlayerData pData, final int tick, 
                                       boolean useBlockChangeTracker, final boolean fromOnGround, final boolean toOnGround,
                                       final boolean isNormalOrPacketSplitMove) {
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        // 1: Attempt to release the item upon a NoSlow Violation, if set so in the configuration.
        //    This is less invasive than a direct set back as item-use is handled badly in this game.
        if (cc.survivalFlyResetItem && hDistanceAboveLimit > 0.0001 && (cData.isUsingItem || player.isBlocking())) {
            tags.add("itemreset");
            // Handle through nms
            if (mcAccess.getHandle().resetActiveItem(player)) {
                cData.isUsingItem = false;
                pData.requestUpdateInventory();
            }
            // Off hand (non nms)
            else if (Bridge1_9.hasGetItemInOffHand() && cData.offHandUse) {
                ItemStack stack = Bridge1_9.getItemInOffHand(player);
                if (stack != null) {
                    if (ServerIsAtLeast1_13) {
                        if (player.isHandRaised()) {
                            // Does nothing
                        }
                        // False positive
                        else cData.isUsingItem = false;
                    } 
                    else {
                        player.getInventory().setItemInOffHand(stack);
                        cData.isUsingItem = false;
                    }
                }
            }
            // Main hand (non nms)
            else if (!cData.offHandUse) {
                ItemStack stack = Bridge1_9.getItemInMainHand(player);
                if (ServerIsAtLeast1_13) {
                    if (player.isHandRaised()) {
                        //data.olditemslot = player.getInventory().getHeldItemSlot();
                        //if (stack != null) player.setCooldown(stack.getType(), 10);
                        //player.getInventory().setHeldItemSlot((data.olditemslot + 1) % 9);
                        //data.changeslot = true;
                        // Does nothing
                    }
                    // False positive
                    else cData.isUsingItem = false;
                } 
                else {
                    if (stack != null) {
                        Bridge1_9.setItemInMainHand(player, stack);
                    }
                }
                cData.isUsingItem = false;
            }
            if (!cData.isUsingItem) {
                double[] estimationRes = hDistRel(from, to, pData, player, data, thisMove, lastMove, cc, tick, useBlockChangeTracker, 
                                                  fromOnGround, toOnGround, debug, multiMoveCount, isNormalOrPacketSplitMove);
                hAllowedDistance = estimationRes[0];
                hDistanceAboveLimit = estimationRes[1];
            }
        }

        // 2: Check being moved by blocks.
        // 1.025 is a Magic value
        // Why the hell would block pushing be limited to distances lower than 1.025?? 
        // Yet another case of undocumented Magic left by asofold, great... Comment it out for now.
        if (cc.trackBlockMove && hDistanceAboveLimit > 0.0) { // && hDistanceAboveLimit < 1.025) {
            // Push by 0.49-0.51 in one direction. Also observed 1.02.
            // TODO: Better also test if the per axis distance is equal to or exceeds hDistanceAboveLimit?
            // TODO: The minimum push value can be misleading (blocked by a block?)
            final double xDistance = to.getX() - from.getX();
            final double zDistance = to.getZ() - from.getZ();
            if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, xDistance < 0 ? Direction.X_NEG : Direction.X_POS, 0.05)) {
                hAllowedDistance = thisMove.hDistance; 
                hDistanceAboveLimit = 0.0;
            }
            else if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, zDistance < 0 ? Direction.Z_NEG : Direction.Z_POS, 0.05)) {
                hAllowedDistance = thisMove.hDistance; 
                hDistanceAboveLimit = 0.0;
            }
        }

        // 3: Check velocity.
        // TODO: Implement Asofold's fix to prevent too easy abuse:
        // See: https://github.com/NoCheatPlus/Issues/issues/374#issuecomment-296172316
        double hFreedom = 0.0; // Horizontal velocity used.
        if (hDistanceAboveLimit > 0.0) {
            hFreedom = data.getHorizontalFreedom();
            if (hFreedom < hDistanceAboveLimit) {
                // Use queued velocity if possible.
                hFreedom += data.useHorizontalVelocity(hDistanceAboveLimit - hFreedom);
                // If using queued horizontal velocity, update Improbable's entries/buckets without adding anything.
                // (We want to keep the check 'vigilant' for potential abuses)
                Improbable.update(System.currentTimeMillis(), pData);
            }
            if (hFreedom > 0.0) {
                tags.add("hvel");
                hDistanceAboveLimit = Math.max(0.0, hDistanceAboveLimit - hFreedom);
            }
        }
        return new double[]{hAllowedDistance, hDistanceAboveLimit, hFreedom};
    }


    /**
     * Inside liquids vertical speed checking.<br> setFrictionJumpPhase must be set
     * externally.
     * TODO: Recode to a vDistRel-like implementation.
     * 
     * @param from
     * @param to
     * @param toOnGround
     * @param yDistance
     * @param data
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistLiquid(final PlayerMoveData thisMove, final PlayerLocation from, final PlayerLocation to, 
                                 final boolean toOnGround, final double yDistance, final PlayerMoveData lastMove, 
                                 final MovingData data, final Player player, final MovingConfig cc, final IPlayerData pData, final boolean fromOnGround) {
        final double yDistAbs = Math.abs(yDistance);
        /** If a server with version lower than 1.13 has ViaVer installed, allow swimming */
        final boolean swimmingInLegacyServer = !ServerIsAtLeast1_13 && player.isSprinting() && pData.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13);
        final double baseSpeed = thisMove.from.onGround ? Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player) || swimmingInLegacyServer) + 0.1 : Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player) || swimmingInLegacyServer);
        /** Slow fall gravity is applied only if the player is not sneaking (in that case, the player will descend in water with regular gravity) */
        // TODO: Rough... Needs a better modeling.
        final boolean Slowfall = !(player.isSneaking() && reallySneaking.contains(player.getName())) && thisMove.hasSlowfall;

        //////////////////////////////////////////////////////
        // 0: Checks for no gravity when moving in a liquid.//
        //////////////////////////////////////////////////////
        if (thisMove.yDistance == 0.0 && lastMove.yDistance == 0.0 && lastMove.toIsValid
            && thisMove.hDistance > 0.090 && lastMove.hDistance > 0.090 // Do not check lower speeds. The cheat would be purely cosmetic at that point, it wouldn't offer any advantage.
            && BlockProperties.isLiquid(to.getTypeId()) 
            && BlockProperties.isLiquid(from.getTypeId())
            && !fromOnGround && !toOnGround
            && !from.isHeadObstructed() && !to.isHeadObstructed() 
            && !Bridge1_13.isSwimming(player)) {
            Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
            tags.add("liquidwalk");
            return new double[]{0.0, yDistance};
        }
        
        ////////////////////////////
        // 1: Minimal speed.     //
        ///////////////////////////
        if (yDistAbs <= baseSpeed) {
            return new double[]{baseSpeed, 0.0};
        }
        
        /////////////////////////////////////////////////////////
        // 2: Vertical checking for waterlogged blocks 1.13+   //
        /////////////////////////////////////////////////////////
        if (from.isOnGround() && !BlockProperties.isLiquid(from.getTypeIdAbove())
            && from.isInWaterLogged()
            && !from.isInBubbleStream() && !thisMove.headObstructed
            && !from.isSubmerged(0.75)) {
            // (Envelope change shouldn't be done here but, eh.)
            data.liftOffEnvelope = LiftOffEnvelope.NORMAL;
            final double minJumpGain = data.liftOffEnvelope.getJumpGain(data.jumpAmplifier);
            // Allow stepping.
            final boolean step = (toOnGround || thisMove.to.resetCond) && yDistance > minJumpGain && yDistance <= cc.sfStepHeight;
            final double vAllowedDistance = step ? cc.sfStepHeight : minJumpGain;
            tags.add("liquidground");
            return new double[]{vAllowedDistance, yDistance - vAllowedDistance};
        }


        /////////////////////////////////////////////////////////
        // 3: Friction envelope (allow any kind of slow down). //
        ////////////////////////////////////////////////////////
        final double frictDist = lastMove.toIsValid ? Math.abs(lastMove.yDistance) * data.lastFrictionVertical : baseSpeed; // Bounds differ with sign.
        if (lastMove.toIsValid) {
            if (lastMove.yDistance < 0.0 && yDistance < 0.0 
                && yDistAbs < frictDist + (Slowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN)) {
                // (Descend speed depends on how fast one dives in)
                tags.add("frictionenv(desc)");
                return new double[]{-frictDist - (Slowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN), 0.0};
            }
            if (lastMove.yDistance > 0.0 && yDistance > 0.0 && yDistance < frictDist - Magic.GRAVITY_SPAN) {
                tags.add("frictionenv(asc)");
                return new double[]{frictDist - Magic.GRAVITY_SPAN, 0.0};
            }
            // ("== 0.0" is covered by the minimal speed check above.)
        }
        
        
        ////////////////////////////////////
        // 4: Handle bubble columns 1.13+ // 
        ////////////////////////////////////
        // TODO: bubble columns adjacent to each other: push prevails on drag. So do check adjacent blocks and see if they are draggable as well.
        //[^ Problem: we only get told if the column can drag players]
        // TODO (Still quite rough: Minecraft acceleration(s) is different actually, but it doesn't seem to work at all with NCP...)
        if (from.isInBubbleStream() || to.isInBubbleStream()) {
            // Minecraft distinguishes above VS inside, so we need to do it as well.
            // This is what it does to determine if the player is above the stream (check for air above)
            tags.add("bblstrm_asc");
            if (BlockProperties.isAir(from.getTypeIdAbove())) {
                double vAllowedDistance = Math.min(1.8, lastMove.yDistance + 0.2);
                return new double[]{vAllowedDistance, yDistAbs - vAllowedDistance};
            }
            // Inside.
            if (lastMove.yDistance < 0.0 && yDistance < 0.0
                && !thisMove.headObstructed) {
                // == 0.0 is covered by waterwalk
                // If inside a bubble column, and the player is getting pushed, they cannot descend
                return new double[]{0.0, yDistAbs};
            }
            double vAllowedDistance = Math.min(Magic.bubbleStreamAscend, lastMove.yDistance + 0.2);
            return new double[]{vAllowedDistance, yDistance - vAllowedDistance}; 
        }
        // Check for drag
        // (Seems to work OK, unlike ascending)
        if (from.isDraggedByBubbleStream() || to.isDraggedByBubbleStream()) {
            tags.add("bblstrm_desc");
            // Above
            if (BlockProperties.isAir(from.getTypeIdAbove())) {
                // -0.03 is the effective acceleration, capped at -0.9
                double vAllowedDistance = Math.max(-0.9, lastMove.yDistance - 0.03);
                return new double[]{vAllowedDistance, yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0}; 
            }
            // Inside
            if (lastMove.yDistance > 0.0 && yDistance > 0.0) {
                // If inside a bubble column, and the player is getting dragged, they cannot ascend
                return new double[]{0.0, yDistAbs};
            }
            double vAllowedDistance = Math.max(-0.3, lastMove.yDistance - 0.03);
            return new double[]{vAllowedDistance, yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0}; 
        }


        ///////////////////////////////////////
        // 5: Workarounds for special cases. // 
        ///////////////////////////////////////
        final Double wRes = LiquidWorkarounds.liquidWorkarounds(player, from, to, baseSpeed, frictDist, lastMove, data);
        if (wRes != null) {
            return new double[]{wRes, 0.0};
        }


        ///////////////////////////////////////////////
        // 6: Try to use velocity for compensation.  //
        ///////////////////////////////////////////////
        if (data.getOrUseVerticalVelocity(yDistance) != null) {
            return new double[]{yDistance, 0.0};
        }
        

        ///////////////////////////////////
        // 7: At this point a violation. //
        //////////////////////////////////
        tags.add(yDistance < 0.0 ? "swimdown" : "swimup");
        // Can't ascend in liquid if sneaking.
        if (player.isSneaking() && reallySneaking.contains(player.getName()) 
            // (Clearly ascending)
            && yDistance > 0.0 && lastMove.yDistance > 0.0 && yDistance >= lastMove.yDistance) {
            return new double[]{0.0, yDistance};
        }
        final double vl1 = yDistAbs - baseSpeed;
        final double vl2 = Math.abs(
                                      yDistAbs - frictDist - 
                                      (yDistance < 0.0 ? (Slowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN) : Magic.GRAVITY_MIN)
                                   );
        if (vl1 <= vl2) return new double[]{yDistance < 0.0 ? -baseSpeed : baseSpeed, vl1};
        return new double[]{yDistance < 0.0 ? -frictDist - (Slowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN) 
                           : frictDist - Magic.GRAVITY_SPAN, vl2};
    }


    /**
     * Simple (limit-based, non-predictiv) on-climbable vertical distance checking.
     * Handled in a separate method to reduce the complexity of vDistRel workarounds.
     * 
     * @param player
     * @param from
     * @param to
     * @param fromOnGround 
     * @param toOnGround 
     * @param pData
     * @param thisMove
     * @param lastMove
     * @param yDistance
     * @param data
     * @param cc
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistClimbable(final Player player, final PlayerLocation from, final PlayerLocation to,
                                    final boolean fromOnGround, final boolean toOnGround, final IPlayerData pData,
                                    final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                    final double yDistance, final MovingData data, final MovingConfig cc) {
        data.clearActiveHorVel(); // Might want to clear ALL horizontal vel.
        double vDistanceAboveLimit = 0.0;
        double yDistAbs = Math.abs(yDistance);
        final double maxJumpGain = data.liftOffEnvelope.getJumpGain(data.jumpAmplifier) + 0.0001;
        /** Climbing a ladder in water and exiting water for whatever reason speeds up the player a lot in that one transition ... */
        final boolean waterStep = lastMove.from.inLiquid && yDistAbs < Magic.swimBaseSpeedV(Bridge1_13.hasIsSwimming());
        // Quick, temporary fix for scaffolding block
        final boolean scaffolding = from.isOnGround() && from.getBlockY() == Location.locToBlock(from.getY()) && yDistance > 0.0 && yDistance < maxJumpGain;
        double vAllowedDistance = (waterStep || scaffolding) ? yDistAbs : yDistance < 0.0 ? Magic.climbSpeedDescend : Magic.climbSpeedAscend;
        final double maxJumpHeight = LiftOffEnvelope.NORMAL.getMaxJumpHeight(0.0) + (data.jumpAmplifier > 0 ? (0.6 + data.jumpAmplifier - 1.0) : 0.0);
        
        // Workaround for ladders that have a much bigger collision box, but much smaller hitbox (We do not distinguish the two). 
        if (yDistAbs > vAllowedDistance) {
            if (from.isOnGround(BlockFlags.F_CLIMBABLE) && to.isOnGround(BlockFlags.F_CLIMBABLE)) {
                // Stepping a up a block (i.e.: stepping up stairs with vines/ladders on the side), allow this movement.
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, yDistAbs - cc.sfStepHeight);
                tags.add("climbstep");
            }
            else if (from.isOnGround(maxJumpHeight, 0D, 0D, BlockFlags.F_CLIMBABLE)) {
                // If ground is found within the allowed jump height, we check speed against jumping motion not climbing motion, in order to still catch extremely fast / instant modules.
                // (We have to check for full height because otherwise the immediate move after jumping will yield a false positive, as air gravity will be applied, which is stll higher than climbing motion)
                // In other words, this means that as long as ground is found within the allowed height, players will be able to speed up to 0.42 on climbables.
                // The advantage is pretty insignificant while granting us some leeway with false positives and cross-versions compatibility issues.
                if (yDistance > maxJumpGain) {
                    // If the player managed to exceed the ordinary jumping motion while on a climbable, we know for sure they're cheating.
                    vDistanceAboveLimit = Math.max(vDistanceAboveLimit, yDistAbs - vAllowedDistance);
                    Improbable.check(player, (float)yDistAbs, System.currentTimeMillis(), "moving.survivalfly.instantclimb", pData);
                    tags.add("instantclimb");
                }
                // Ground is within reach and speed is lower than 0.42. Allow the movement.
                else tags.add("climbheight("+ StringUtil.fdec3.format(maxJumpHeight) +")");
            }
            else {
                // Ground is out reach and motion is still higher than legit. We can safely throw a VL at this point.
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, yDistAbs - vAllowedDistance);
                tags.add("climbspeed");
            }
        }
        
        // Can't climb up with vine not attached to a solid block (legacy).
        if (yDistance > 0.0 && !thisMove.touchedGround && !from.canClimbUp(maxJumpHeight)) {
            vDistanceAboveLimit = Math.max(vDistanceAboveLimit, yDistance);
            vAllowedDistance = 0.0;
            tags.add("climbdetached");
        }

        // Do allow friction with velocity.
        // TODO: Actual friction or limit by absolute y-distance?
        // TODO: Looks like it's only a problem when on ground?
        if (vDistanceAboveLimit > 0.0 && thisMove.yDistance > 0.0 
            && lastMove.yDistance - (Magic.GRAVITY_MAX + Magic.GRAVITY_MIN) / 2.0 > thisMove.yDistance) {
            vDistanceAboveLimit = 0.0;
            vAllowedDistance = yDistance;
            tags.add("vfrict_climb");
        }

        // Do allow vertical velocity.
        // TODO: Looks like less velocity is used here (normal hitting 0.361 of 0.462).
        if (vDistanceAboveLimit > 0.0 && data.getOrUseVerticalVelocity(yDistance) != null) {
            vDistanceAboveLimit = 0.0;
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }

    /**
     * Violation handling put here to have less code for the frequent processing of check.
     * @param now
     * @param result
     * @param player
     * @param from
     * @param to
     * @param data
     * @param cc
     * @return
     */
    private Location handleViolation(final long now, final double result, 
                                    final Player player, final PlayerLocation from, final PlayerLocation to, 
                                    final MovingData data, final MovingConfig cc) {

        // Increment violation level.
        data.survivalFlyVL += result;
        final ViolationData vd = new ViolationData(this, player, data.survivalFlyVL, result, cc.survivalFlyActions);
        if (vd.needsParameters()) {
            vd.setParameter(ParameterName.LOCATION_FROM, String.format(Locale.US, "%.2f, %.2f, %.2f", from.getX(), from.getY(), from.getZ()));
            vd.setParameter(ParameterName.LOCATION_TO, String.format(Locale.US, "%.2f, %.2f, %.2f", to.getX(), to.getY(), to.getZ()));
            vd.setParameter(ParameterName.DISTANCE, String.format(Locale.US, "%.2f", TrigUtil.distance(from, to)));
            vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
        }
        // Some resetting is done in MovingListener.
        if (executeActions(vd).willCancel()) {
            data.sfVLMoveCount = data.getPlayerMoveCount();
            // Set back + view direction of to (more smooth).
            return MovingUtil.getApplicableSetBackLocation(player, to.getYaw(), to.getPitch(), to, data, cc);
        }
        else {
            data.sfVLMoveCount = data.getPlayerMoveCount();
            data.sfJumpPhase = 0;
            // Cancelled by other plugin, or no cancel set by configuration.
            return null;
        }
    }

    
    /**
     * Hover violations have to be handled in this check, because they are handled as SurvivalFly violations (needs executeActions).
     * @param player
     * @param loc
     * @param blockCache 
     * @param cc
     * @param data
     */
    public final void handleHoverViolation(final Player player, final PlayerLocation loc, final MovingConfig cc, final MovingData data) {
        data.survivalFlyVL += cc.sfHoverViolation;

        // TODO: Extra options for set back / kick, like vl?
        data.sfVLMoveCount = data.getPlayerMoveCount();
        data.sfVLInAir = true;
        final ViolationData vd = new ViolationData(this, player, data.survivalFlyVL, cc.sfHoverViolation, cc.survivalFlyActions);
        if (vd.needsParameters()) {
            vd.setParameter(ParameterName.LOCATION_FROM, String.format(Locale.US, "%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ()));
            vd.setParameter(ParameterName.LOCATION_TO, "(HOVER)");
            vd.setParameter(ParameterName.DISTANCE, "0.0(HOVER)");
            vd.setParameter(ParameterName.TAGS, "hover");
        }
        if (executeActions(vd).willCancel()) {
            // Set back or kick.
            final Location newTo = MovingUtil.getApplicableSetBackLocation(player, loc.getYaw(), loc.getPitch(), loc, data, cc);
            if (newTo != null) {
                data.prepareSetBack(newTo);
                player.teleport(newTo, BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
            }
            else {
                // Solve by extra actions ? Special case (probably never happens)?
                player.kickPlayer("Hovering?");
            }
        }
        else {
            // Ignore.
        }
    }


    /**
     * This is set with PlayerToggleSneak, to be able to distinguish players that are really sneaking from players that are set sneaking by a plugin. 
     * @param player 
     * @param sneaking
     */
    public void setReallySneaking(final Player player, final boolean sneaking) {
        if (sneaking) reallySneaking.add(player.getName());
        else reallySneaking.remove(player.getName());
    }



    /**
     * Debug output.
     * @param player
     * @param to
     * @param data
     * @param cc
     * @param hDistance
     * @param hAllowedDistance
     * @param hFreedom
     * @param yDistance
     * @param vAllowedDistance
     * @param fromOnGround
     * @param resetFrom
     * @param toOnGround
     * @param resetTo
     */
    private void outputDebug(final Player player, final PlayerLocation to, final PlayerLocation from,
                             final MovingData data, final MovingConfig cc, 
                             final double hDistance, final double hAllowedDistance, final double hFreedom, 
                             final double yDistance, final double vAllowedDistance,
                             final boolean fromOnGround, final boolean resetFrom, 
                             final boolean toOnGround, final boolean resetTo,
                             final PlayerMoveData thisMove, double vDistanceAboveLimit) {

        // TODO: Show player name once (!)
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double yDistDiffEx = yDistance - vAllowedDistance;
        final double hDistDiffEx = thisMove.hDistance - thisMove.hAllowedDistance;
        final StringBuilder builder = new StringBuilder(500);
        builder.append(CheckUtils.getLogMessagePrefix(player, type));
        final String hVelUsed = hFreedom > 0 ? " / hVelUsed: " + StringUtil.fdec3.format(hFreedom) : "";
        builder.append("\nOnGround: " + (thisMove.headObstructed ? "(head obstr.) " : from.isSlidingDown() ? "(sliding down)" : "") + (thisMove.touchedGroundWorkaround ? "(lost ground) " : "") + (fromOnGround ? "onground -> " : (resetFrom ? "resetcond -> " : "--- -> ")) + (toOnGround ? "onground" : (resetTo ? "resetcond" : "---")) + ", jumpPhase: " + data.sfJumpPhase + ", LiftOff: " + data.liftOffEnvelope.name() + "(" + data.insideMediumCount + ")");
        final String dHDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(hDistance, lastMove.hDistance) + ")" : "";
        final String dYDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(yDistance, lastMove.yDistance)+ ")" : "";
        builder.append("\n" + " hDist: " + StringUtil.fdec6.format(hDistance) + dHDist + " / hDistDiffEx: " + hDistDiffEx + " / hAD: " + StringUtil.fdec6.format(hAllowedDistance) + hVelUsed +
                       "\n" + " vDist: " + StringUtil.fdec6.format(yDistance) + dYDist + " / yDistDiffEx: " + yDistDiffEx + " / vAD: " + StringUtil.fdec6.format(vAllowedDistance) + " , setBackY: " + (data.hasSetBack() ? (data.getSetBackY() + " (setBackYDist: " + StringUtil.fdec3.format(to.getY() - data.getSetBackY()) + " / MaxJumpHeight: " + data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier) + ")") : "?"));
        if (lastMove.toIsValid) {
            builder.append("\n fdsq: " + StringUtil.fdec3.format(thisMove.distanceSquared / lastMove.distanceSquared));
        }
        if (!lastMove.toIsValid) {
            builder.append("\n Invalid last move (data reset)");
        }
        if (!lastMove.valid) {
            builder.append("\n Invalid last move (missing data)");
        }
        if (thisMove.verVelUsed != null) {
            builder.append(" , vVelUsed: " + thisMove.verVelUsed + " ");
        }
        data.addVerticalVelocity(builder);
        data.addHorizontalVelocity(builder);
        if (player.isSleeping()) {
            tags.add("sleeping");
        }
        if (player.getFoodLevel() <= 5 && player.isSprinting()) {
            // Exception: does not take into account latency.
            tags.add("lowfoodsprint");
        }
        if (Bridge1_9.isWearingElytra(player)) {
            // Just wearing (not isGliding).
            tags.add("elytra_off");
        }
        if (!tags.isEmpty()) {
            builder.append("\n" + " Tags: " + StringUtil.join(tags, "+"));
        }
        if (!justUsedWorkarounds.isEmpty()) {
            builder.append("\n" + " Workarounds: " + StringUtil.join(justUsedWorkarounds, " , "));
        }
        builder.append("\n");
        NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, builder.toString());
    }

    
    private void logPostViolationTags(final Player player) {
        debug(player, "SurvivalFly Post violation handling tag update:\n" + StringUtil.join(tags, "+"));
    }
}