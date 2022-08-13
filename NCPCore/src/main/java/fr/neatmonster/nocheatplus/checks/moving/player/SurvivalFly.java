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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.magic.AirWorkarounds;
import fr.neatmonster.nocheatplus.checks.moving.magic.LiquidWorkarounds;
import fr.neatmonster.nocheatplus.checks.moving.magic.LostGround;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.Direction;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionAccumulator;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;

/**
 * The counterpart to the CreativeFly check. <br>People that are not allowed to fly get checked by this. <br>It will try to
 * identify when they are jumping, check if they aren't jumping too high or far, check if they aren't moving too fast on
 * normal ground, while sprinting, sneaking, swimming, etc.
 */
public class SurvivalFly extends Check {

    // TODO: One might also consider recoding vDistRel and vDistLiquid to slim down workarounds
    // (Estimation/Prediction-based, according to MC's formulas and equations)

    private final boolean ServerIsAtLeast1_13 = ServerVersion.compareMinecraftVersion("1.13") >= 0;
    /** Flag to indicate whether the buffer should be used for this move (only work inside setAllowedhDist). */
    private boolean bufferUse;
    /** To join some tags with moving check violations. */
    private final ArrayList<String> tags = new ArrayList<String>(15);
    private final ArrayList<String> justUsedWorkarounds = new ArrayList<String>();
    private final Set<String> reallySneaking = new HashSet<String>(30);
    private final BlockChangeTracker blockChangeTracker;
    /** Location for temporary use with getLocation(useLoc). Always call setWorld(null) after use. Use LocUtil.clone before passing to other API. */
    private final Location useLoc = new Location(null, 0, 0, 0);
    // TODO: handle
    private IGenericInstanceHandle<IAttributeAccess> attributeAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IAttributeAccess.class);


    /**
     * Some note for mcbe compatibility:
     * - New step pattern 0.42-0.58-0.001 ?
     * - Maximum step height 0.75 ?
     * - Ladder descends speed 0.2
     * - Jump on grass_path blocks will result in jump height 0.42 + 0.0625
     *   but next move friction still base on 0.42 ( not sure this does happen
     *   on others )
     * - honey block: yDistance < -0.118 && yDistance > -0.128 ?
     */

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
     * @return
     */
    public Location check(final Player player, final PlayerLocation from, final PlayerLocation to, 
                          final int multiMoveCount, 
                          final MovingData data, final MovingConfig cc, final IPlayerData pData,
                          final int tick, final long now, final boolean useBlockChangeTracker) {

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
        final boolean sprinting = now <= data.timeSprinting + cc.sprintingGrace;

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
            final Location loc = player.getLocation(useLoc);
            data.adjustMediumProperties(loc, cc, player, thisMove);
            data.adjustLiftOffEnvelope(from);
            // Cleanup
            useLoc.setWorld(null);
        }
        
        /** From a reset-condition location, lostground is accounted for here. */
        // TODO: This isn't correct, needs redesign.
        final boolean resetFrom = fromOnGround || from.isResetCond() 
                                || isSamePos && lastMove.toIsValid && LostGround.lostGroundStill(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags)
                                || LostGround.lostGround(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, useBlockChangeTracker ? blockChangeTracker : null, tags);
        // Alter some data before checking anything
        setHorVerDataExAnte(thisMove, from, to, data, yDistance, pData, player, cc, xDistance, zDistance, lastMove, multiMoveCount, debug); 




        /////////////////////////////////////
        // Horizontal move                ///
        /////////////////////////////////////
        bufferUse = true;
        double hAllowedDistance = 0.0, hDistanceAboveLimit = 0.0, hFreedom = 0.0;

        // Run through all hDistance checks if the player has actually some horizontal distance
        if (HasHorizontalDistance) {

            // Set the allowed distance and determine the distance above limit
            double[] estimationRes = hDistChecks(from, to, pData, player, data, thisMove, lastMove, cc, sprinting, true, tick, useBlockChangeTracker, fromOnGround, toOnGround);
            hAllowedDistance = estimationRes[0];
            hDistanceAboveLimit = estimationRes[1];
            // The player went beyond the allowed limit, execute the after failure checks.
            if (hDistanceAboveLimit > 0.0) {
                double[] failureResult = hDistAfterFailure(player, from, to, hAllowedDistance, hDistanceAboveLimit, sprinting, thisMove, lastMove, data, cc, pData, tick, useBlockChangeTracker, fromOnGround, toOnGround);
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
        // No horizontal distance present
        else {
            data.clearActiveHorVel();
            thisMove.hAllowedDistance = 0.0;
            hFreedom = 0.0;
        }
        // Adjust some data after horizontal checking but before vertical
        data.setHorDataExPost();



        /////////////////////////////////////
        // Vertical move                  ///
        /////////////////////////////////////
        double vAllowedDistance = 0, vDistanceAboveLimit = 0;
        // Step wild-card: allow step height from ground to ground, if not on/in a medium already.
        if (yDistance >= 0.0 && yDistance <= cc.sfStepHeight 
            && toOnGround && fromOnGround && !from.isResetCond()) {
            vAllowedDistance = cc.sfStepHeight;
            thisMove.allowstep = true;
            tags.add("groundstep");
        }
        // Handle levitation (has priority on everything except liquid)
        else if (thisMove.hasLevitation && !from.isInLiquid()) {
            final double[] resultLevitation = vDistLevitation(pData, player, data, from, to, cc, fromOnGround);
            vAllowedDistance = resultLevitation[0];
            vDistanceAboveLimit = resultLevitation[1];
        }
        // Climbable blocks
        // NOTE: this check needs to be before isInWeb, because otherwise it can lead to false positive
        else if (from.isOnClimbable()) {
            final double[] resultClimbable = vDistClimbable(player, from, to, fromOnGround, toOnGround, thisMove, lastMove, yDistance, data);
            vAllowedDistance = resultClimbable[0];
            vDistanceAboveLimit = resultClimbable[1];
        }
        // Powder snow (1.17+)
        else if (from.isInPowderSnow()) {
            final double[] resultSnow = vDistPowderSnow(yDistance, from, to, cc, data, player, pData, now);
            vAllowedDistance = resultSnow[0];
            vDistanceAboveLimit = resultSnow[1];
        }
        // Webs 
        else if (from.isInWeb()) {
            final double[] resultWeb = vDistWeb(player, thisMove, fromOnGround, toOnGround, hDistanceAboveLimit, now, data, cc, from, to, pData);
            vAllowedDistance = resultWeb[0];
            vDistanceAboveLimit = resultWeb[1];
        }
        // Berry bush (1.15+)
        else if (from.isInBerryBush()) {
            final double[] resultBush = vDistBush(player, thisMove, toOnGround, hDistanceAboveLimit, now, data, cc, from, to, fromOnGround, pData);
            vAllowedDistance = resultBush[0];
            vDistanceAboveLimit = resultBush[1];
        }
        // HoneyBlock (1.14+)
        else if (from.isOnHoneyBlock()) {
            vAllowedDistance = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);
            if (data.getOrUseVerticalVelocity(thisMove.yDistance) == null) {
                vDistanceAboveLimit = yDistance - vAllowedDistance;
                if (vDistanceAboveLimit > 0.0) {
                    tags.add("honeyasc");
                }
            }
        }
        // In liquid
        else if (from.isInLiquid()) { 
            final double[] resultLiquid = vDistLiquid(thisMove, from, to, toOnGround, yDistance, lastMove, data, player, cc);
            vAllowedDistance = resultLiquid[0];
            vDistanceAboveLimit = resultLiquid[1];

            // The friction jump phase has to be set externally.
            if (vDistanceAboveLimit <= 0.0 && yDistance > 0.0 
                && Math.abs(yDistance) > Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player))) {
                data.setFrictionJumpPhase();
            }
        }
        // Fallback to in-air checks
        else {
            final double[] resultAir = vDistAir(now, player, from, fromOnGround, resetFrom, 
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
            outputDebug(player, to, data, cc, hDistance, hAllowedDistance, hFreedom, 
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
                // Finally check horizontal buffer regain.
                if (hDistanceAboveLimit < 0.0 && result <= 0.0 && !isSamePos && data.sfHorizontalBuffer < cc.hBufMax
                    && !data.sfLowJump) {
                    // TODO: max min other conditions ?
                    hBufRegain(hDistance, Math.min(0.2, Math.abs(hDistanceAboveLimit)), data, cc);
                }
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
            // Minecraft 1.13 allows players to swim up to the surface and have two consecutive in-air moves with higher jump height.
            // (Moving near ground takes precedence)
            else if (Magic.inAir(lastMove) && Magic.intoWater(thisMove) && data.liftOffEnvelope == LiftOffEnvelope.LIMIT_SURFACE
                    && BlockProperties.isAir(to.getTypeIdAbove()) && !thisMove.headObstructed 
                    && !thisMove.inWaterfall) {
                // KEEP
            }
            // Fallback to default liquid lift-off limit.
            else data.liftOffEnvelope = LiftOffEnvelope.LIMIT_LIQUID;
        }
        else if (thisMove.to.inPowderSnow) {
            data.liftOffEnvelope = LiftOffEnvelope.POWDER_SNOW;
        }
        else if (thisMove.to.inWeb) {
            data.liftOffEnvelope = LiftOffEnvelope.NO_JUMP; 
        }
        else if (thisMove.to.inBerryBush) {
            data.liftOffEnvelope = LiftOffEnvelope.BERRY_JUMP;
        }
        else if (thisMove.to.onHoneyBlock) {
            data.liftOffEnvelope = LiftOffEnvelope.HALF_JUMP;
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
            // Minecraft 1.13 allows players to swim up to the surface and have two consecutive in-air moves.
            // (Moving near ground takes precedence)
            else if (Magic.inWater(lastMove) && Magic.leavingWater(thisMove) 
                    // && BlockProperties.isLiquid(blockUnder)
                    && !thisMove.headObstructed && !Magic.recentlyInWaterfall(data, 10)) {// && BlockProperties.isAir(from.getTypeIdAbove())
                data.liftOffEnvelope = LiftOffEnvelope.LIMIT_SURFACE;
            }
            // Fallback to default liquid lift-off limit.
            else data.liftOffEnvelope = LiftOffEnvelope.LIMIT_LIQUID;

        }
        else if (thisMove.from.inPowderSnow) {
            data.liftOffEnvelope = LiftOffEnvelope.POWDER_SNOW;
        }
        else if (thisMove.from.inWeb) {
            data.liftOffEnvelope = LiftOffEnvelope.NO_JUMP; 
        }
        else if (thisMove.from.inBerryBush) {
            data.liftOffEnvelope = LiftOffEnvelope.BERRY_JUMP;
        }
        else if (thisMove.from.onHoneyBlock) {
            data.liftOffEnvelope = LiftOffEnvelope.HALF_JUMP;
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
            // The player has moved onto ground.
            if (toOnGround) {
                // Moving onto ground but still ascending (jumping next to a block).
                if (yDistance > 0.0 && to.getY() > data.getSetBackY() + 0.13 // 0.15 ?
                    && !from.isResetCond() && !to.isResetCond()) { 
                    // Schedule a no low jump flag, because this low descending phase is legit
                    data.sfNoLowJump = true;
                    // End earlier this bunnyfly phase.
                    data.bunnyhopDelay = 0;
                    if (debug) {
                        debug(player, "Slope: schedule sfNoLowJump and reset bunnyfly.");
                    }
                }
                // Ordinary
                else data.sfNoLowJump = false;
            }
            // Lost ground or reset condition
            else data.sfNoLowJump = false;
            // Reset data.
            data.setSetBack(to);
            data.sfJumpPhase = 0;
            data.clearAccounting();
            if (data.sfLowJump && resetFrom) {
                // Prevent reset if coming from air (purpose of the flag).
                data.sfLowJump = false;
            }
            if (hFreedom <= 0.0 && thisMove.verVelUsed == null) {
                data.resetVelocityJumpPhase(tags);
            }
        }
        // The player moved from ground.
        else if (resetFrom) {
            // Keep old setback if coming from a 1 block high slope.
            data.setSetBack(from);
            data.sfJumpPhase = 1; // This event is already in air.
            data.clearAccounting();
            data.sfLowJump = false;
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
        data.lastBlockFrictionVertical = data.nextBlockFrictionVertical;

        // Log tags added after violation handling.
        if (debug && tags.size() > tagsLength) {
            logPostViolationTags(player);
        }
        // Nothing to do, newTo (MovingListener) stays null
        return null;
    }
    




 
    /**
     * A check to prevent players from bed-flying.
     * (This increases VL and sets tag only. Setback is done in MovingListener).
     * TODO: Move to packet-level, perhaps
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
     * Data to be set/adjusted before checking (h/v).
     * 
     * @param thisMove
     * @param from
     * @param to
     * @param data
     * @param pData
     * @param player
     * @param cc
     */
    private void setHorVerDataExAnte(final PlayerMoveData thisMove, final PlayerLocation from, final PlayerLocation to, final MovingData data, final double yDistance,
                                     final IPlayerData pData, final Player player, final MovingConfig cc, final double xDistance, final double zDistance,
                                     final PlayerMoveData lastMove, int multiMoveCount, boolean debug) {

        final Location loc = player.getLocation(useLoc);
        data.adjustMediumProperties(loc, cc, player, thisMove);
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
                // Schedule a no low jump flag because the setback update will then cause a low-jump with the subsequent descending phase
                data.sfNoLowJump = true;
                if (debug) {
                    debug(player, "Ground appeared due to a block-place: schedule sfNoLowJump and adjust set-back location.");
                }
            }
        }
        
        // Renew the "dirty"-flag (in-air phase affected by velocity).
        // (Reset is done after checks run.) 
        if (data.isVelocityJumpPhase() || data.resetVelocityJumpPhase(tags)) {
            tags.add("dirty");
        }

        // HACK: Force sfNoLowJump by a flag.
        // TODO: Might remove that flag, as the issue for trying this has been resolved differently (F_HEIGHT8_1).
        // TODO: Consider setting on ground_height always?
        // TODO: Specialize - test for foot region?
        if ((from.getBlockFlags() & BlockFlags.F_ALLOW_LOWJUMP) != 0) {
            data.sfNoLowJump = true;
        } 

        // Decrease bunnyhop delay counter (bunnyfly)
        data.bunnyhopDelay--; 
        
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
     * Core h-distance checks for media and all status
     * Most values and mechanics can be found in LivingEntity.java.travel()
     * @return hAllowedDistance, hDistanceAboveLimit
     */
    private final double[] hDistChecks(final PlayerLocation from, final PlayerLocation to, final IPlayerData pData, final Player player, 
                                       final MovingData data, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingConfig cc,
                                       final boolean sprinting, boolean checkPermissions, final int tick, final boolean useBlockChangeTracker,
                                       final boolean fromOnGround, final boolean toOnGround) {

        // NOTE: About the old "infinte violation level" issue:
        // Could be due to the attribute actually, since it does return Double.MAX_VALUE if it cannot be determined.
        // TODO: Workarounds ARE needed:
        // 1) First move(s) after landing will generate a false positive. Re-introduce the bunnyhoptick thing? -> No, let's not abuse counters... (!)
        // 2) Swimming on the surface: false positives on the first two moves when leaving liquid (1.13)
        // 3) Turning around a lot will generate sporadic false positives
        // 4) Sneaking is possibly even more broken than sprinting. Start/Stop sneaking will always yield false positives.
        // 5) Liquid pushing is missing.
        // 6) Sneaking in water slows down too much
        // 7) Sprinting and walking speed is too high (0.253 when walking, VS actual 0.215 | Sprinting: 0.35+- VS actual: 0.287)
        // 8) Almost any resetCond block has too high speed. (Despite using the same NMS formulas(!?))

        double hDistanceAboveLimit = 0.0;
        double hAllowedDistance = 0.0;
        /** Always set to true unless the player is in a bunnyfly phase */
        boolean allowHop = true;
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        final double minJumpGain = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier);
        final PlayerMoveData pastMove2 = data.playerMoves.getSecondPastMove();
        final PlayerMoveData pastMove3 = data.playerMoves.getThirdPastMove();
        final double jumpGainMargin = 0.005;
        final boolean sneaking = player.isSneaking() && reallySneaking.contains(player.getName());
        /** Takes into account everything: from, to, from-past-ground, to-past-ground, lost ground */
        final boolean onGround = fromOnGround || toOnGround || thisMove.touchedGroundWorkaround;
        /** Only takes into account From. This is due to false positives on the first move(s) when landing (To). Apparently players conserve more speed for a couple of ticks in that one transition */
        // TODO: Not enough! Still a single false positive when landing on ground.
        final boolean fromOnGroundOrLostGround = fromOnGround || thisMove.touchedGroundWorkaround && lastMove.toIsValid;
        /** The movement_speed attribute of the player. Includes all effects except sprinting [see below] (!) */
        double speedAttribute = attributeAccess.getHandle().getMovementSpeed(player);


        //////////////////////////////////////////////////
        // After hop checks (bunnfly)                   //
        //////////////////////////////////////////////////
        // (NCP mechanic, not vanilla. We decide when players can can bunnyhop because we use our own collision system for on ground judgement)
        if (data.bunnyhopDelay > 0) {
            allowHop = false;
            // Rough estimation of bunnyfly speed (assuming two consecutive hops)
            //    final int hopTime = Magic.BUNNYHOP_MAX_DELAY - data.bunnyhopDelay
            //    final double bunnyFlySpeed = 
            //    (Magic.AIR_MOVEMENT_SPEED_ATTRIBUTE / 0.09) + 
            //    (0.6 * Math.pow(data.horizontalFrictionFactor, hopTime)) *
            //    (
            //       lastOnGroundSpeed +
            //       (Magic.BUNNYHOP_JUMP_BONUS / data.horizontalFrictionFactor) - 
            //       (Magic.AIR_MOVEMENT_SPEED_ATTRIBUTE / (0.6 * data.horizontalFrictionFactor * 0.09))
            //    );
            // Do prolong bunnyfly if the player is yet to touch the ground
            if (data.bunnyhopDelay == 1 && !toOnGround && !to.isResetCond()) {
                data.bunnyhopDelay++;
                tags.add("bunnyfly(keep)");
                // Jumping is lasting longer than usual, feed the improbable.
                // This can happen legitimately, especially on ice for a couple of ticks, but never more than 6/5 ticks. Usually.
                Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
            }
            else tags.add("bunnyfly(" + data.bunnyhopDelay + ")");
            // (Currently, no early reset case)
        }


        //////////////////////////////////////////////////////////////
        // Estimate the horizontal speed (per-move distance check)  //                      
        //////////////////////////////////////////////////////////////
        // TODO: Liquid pushing (!)
        // NOTE: Order is as intended: the game checks in this order: water, else if lava, else if gliding, else no medium
        if (!from.isInWater()) {

            if (!from.isInLava()) {
                // No medium  
                /** How much speed should be conserved on the next tick[Air= more speed conservation]. */
                double inertia = onGround ? data.horizontalFrictionFactor * Magic.HORIZONTAL_INERTIA : Magic.HORIZONTAL_INERTIA; 
                /** Per-tick speed gain[Air= less speed] */      
                double acceleration = onGround ? speedAttribute * (Magic.BASE_MOVEMENT_SPEED / (inertia * inertia * inertia)) : Magic.AIR_MOVEMENT_SPEED_ATTRIBUTE;
                if (sprinting) {
                    // (We don't use the attribute here due to desync issues, just detect when the player is sprinting and apply the multiplier manually)
                    acceleration *= Magic.SPRINT_MULTIPLIER;
                    // Count in NCP base sprinting speed modifier
                    acceleration *= cc.survivalFlySprintingSpeed / 100;
                }
                // (Works similar to vDistRel, apply friction first, then add the new distance)
                hAllowedDistance = (lastMove.hDistance * inertia * data.stuckInBlockMultiplier * data.blockSpeedMultiplier) 
                                 + MovingUtil.getInputForce(player, acceleration, from, pData, cc, cData, sneaking, checkPermissions, tags);
                // Bunnyhop (aka: sprint-jump) detection (Adds 0.2 towards the player's current facing)
                if (allowHop && Magic.isBunnyhop(data, fromOnGroundOrLostGround, sprinting)) {
                    hAllowedDistance += Magic.BUNNYHOP_ACCEL_BOOST;
                    data.bunnyhopDelay = Magic.BUNNYHOP_MAX_DELAY; 
                    thisMove.bunnyHop = true;
                    tags.add("bunnyhop");
                    // Keep up the Improbable, but don't feed anything.
                    Improbable.update(System.currentTimeMillis(), pData);
                }
                // (This is called from entityInside(). Technically, it should also apply in any medium, but the honey block's speed-factor is not. So.)
                // TODO: Research if this applies in media as well.
                if (MovingUtil.isSlidingDown(from, mcAccess.getHandle().getWidth(player), thisMove, player)) {
                    if (thisMove.yDistance < -Magic.SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD) {
                        hAllowedDistance *= -Magic.SLIDE_SPEED_THROTTLE / thisMove.yDistance;
                        tags.add("honeyslide");
                    }
                }
                if (from.isOnClimbable() && lastMove.from.onClimbable) {
                    // (Climbable speed is capped, for whatever reason)
                    hAllowedDistance = Math.min(hAllowedDistance, Magic.CLIMBABLE_MAX_HORIZONTAL_SPEED);
                }
                if (onGround) {
                   // Account for bukkit's /walkspeed command (applies only on ground)
                   hAllowedDistance *= data.walkSpeed / Magic.CB_DEFAULT_WALKSPEED;
                   // Account for NCP base speed modifiers.
                   hAllowedDistance *= cc.survivalFlyWalkingSpeed / 100;
                }
            }
            else {
                // Medium lava
                hAllowedDistance = (lastMove.hDistance * Magic.LAVA_HORIZONTAL_INERTIA * data.stuckInBlockMultiplier * data.blockSpeedMultiplier) 
                                 + MovingUtil.getInputForce(player, Magic.LIQUID_BASE_ACCELERATION, from, pData, cc, cData, sneaking, checkPermissions, tags); 
                hAllowedDistance *= cc.survivalFlySwimmingSpeed / 100;
            }
        }
        else {
            // Medium water
            double waterInertia = (Bridge1_13.isSwimming(player) || sprinting) ? Magic.HORIZONTAL_SWIMMING_INERTIA : Magic.WATER_HORIZONTAL_INERTIA;
            double acceleration = Magic.LIQUID_BASE_ACCELERATION;
            double StriderLevel = BridgeEnchant.getDepthStriderLevel(player); // NCP already caps Strider to 3
            if (!onGround) {
                StriderLevel *= Magic.STRIDER_OFF_GROUND_PENALTY_MULTIPLIER;
            }
            // Account for depth strider (Less speed conservation[More friction], but more per-tick speed)
            if (StriderLevel > 0.0) {
                waterInertia += (0.54600006D - waterInertia) * StriderLevel / 3.0;
                acceleration += (speedAttribute * 1.0 - acceleration) * StriderLevel / 3.0; 
            }
            // Account for dolphin's grace (More speed conservation[less friction], no change in the per-tick speed)
            // (This overrides swimming AND depth strider friction)
            // Should note that this is the only reference about dolphin's grace in the code that I could find. 
            if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
                waterInertia = Magic.DOLPHIN_GRACE_INERTIA; 
            }
            hAllowedDistance = (lastMove.hDistance * waterInertia * data.stuckInBlockMultiplier * data.blockSpeedMultiplier) 
                             + MovingUtil.getInputForce(player, acceleration, from, pData, cc, cData, sneaking, checkPermissions, tags);
            hAllowedDistance *= cc.survivalFlySwimmingSpeed / 100;
        }
        
        // Global speed modifiers
        MovingUtil.applyGlobalSpeedModifiers(player, from, data, to, sneaking, hAllowedDistance, onGround, tags);
        // Global speed bypass modifier (can be combined with the other bypass modifiers)
        if (checkPermissions && pData.hasPermission(Permissions.MOVING_SURVIVALFLY_SPEEDING, player)) {
            hAllowedDistance *= cc.survivalFlySpeedingSpeed / 100;
        }
        
        // Now, set the estimated distance in this move
        thisMove.hAllowedDistance = hAllowedDistance;

        // Finally, throw a violation if speed is higher than estimated.
        player.sendMessage("Current speed: " + StringUtil.fdec3.format(thisMove.hDistance));
        player.sendMessage("Allowed speed: " + StringUtil.fdec3.format(thisMove.hAllowedDistance));
        if (MathUtil.equal(thisMove.hDistance, thisMove.hAllowedDistance)) {
            // Ignore
        }
        else {
            if (thisMove.hDistance > thisMove.hAllowedDistance) {
                // hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance - thisMove.hAllowedDistance);
                player.sendMessage("VL: " + StringUtil.fdec3.format(thisMove.hDistance - thisMove.hAllowedDistance));
                tags.add("hdistrel");
            }
        }



        // (Auxiliary checks, these are rather aimed at catching specific cheat implementations.)
        //////////////////////////////////////////////////////
        // Check for no slow down cheats.                   //
        //////////////////////////////////////////////////////
        if (cData.isHackingRI && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))) {
            cData.isHackingRI = false;
            bufferUse = false;
            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
            // Maybe request a check here, instead of feeding.
            Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
            tags.add("noslowpacket");
        }
        
        
        ////////////////////////////////////////////////////////////////////////
        // Prevent players from sprinting with blindness (vanilla mechanic)   // 
        ////////////////////////////////////////////////////////////////////////
        // TODO: Fix this check (receiving blindness while already sprinting allows to still sprint !)
        //   if (player.hasPotionEffect(PotionEffectType.BLINDNESS) && player.isSprinting() 
        //   	 && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))) {
        //   	 bufferUse = false;
        //       Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
        //       hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);     
        //       tags.add("blindsprint");
        //   }
        

        ///////////////////////////////////////////////////////////////////////
        // Checks for no gravity when falling through stuck-in-block blocks. //
        ///////////////////////////////////////////////////////////////////////
        if (thisMove.from.resetCond && lastMove.to.resetCond
            // Exclude liquids and climbables from this check.
            && !thisMove.from.inLiquid && !thisMove.to.inLiquid
            && !thisMove.from.onClimbable && !thisMove.to.onClimbable
            && thisMove.yDistance == 0.0 && lastMove.yDistance == 0.0
            && !onGround && lastMove.toIsValid && !from.isHeadObstructed() 
            && !to.isHeadObstructed()) {
            // (Assume micro-gravity changes to be catched by the respective methods)
            bufferUse = false;
            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
            Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
            tags.add("nogravityblock");
        }


        /////////////////////////////////////////////////////////
        // Checks for micro y deltas when moving above liquid. //
        /////////////////////////////////////////////////////////
        if ((!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_WATERWALK, player))) {
            
            // TODO: With the new liquid (re)modeling, this one is deprecated and subject to removal.
            final Material blockUnder = from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - 0.3), from.getBlockZ());
            final Material blockAbove = from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() + 0.1), from.getBlockZ());
            if (blockUnder != null && BlockProperties.isLiquid(blockUnder) && BlockProperties.isAir(blockAbove)) {
            
                if (thisMove.hDistance > 0.11 && thisMove.yDistance <= LiftOffEnvelope.LIMIT_LIQUID.getMaxJumpGain(0.0) 
                    && !onGround
                    && lastMove.toIsValid && lastMove.yDistance == thisMove.yDistance 
                    || lastMove.yDistance == thisMove.yDistance * -1 && lastMove.yDistance != 0.0
                    && !from.isHeadObstructed() && !to.isHeadObstructed() 
                    && !Bridge1_13.isSwimming(player)) {

                    // Prevent being flagged if a player transitions from a block to water and the player falls into the water.
                    if (!(thisMove.yDistance < 0.0 && thisMove.yDistance != 0.0 && lastMove.yDistance < 0.0 && lastMove.yDistance != 0.0)) {
                        hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
                        bufferUse = false;
                        tags.add("liquidmove");
                    }
                }
            }

            //////////////////////////////////////////////////////
            // Checks for no gravity when moving in a liquid.   //
            //////////////////////////////////////////////////////
            if (thisMove.yDistance == 0.0 && lastMove.yDistance == 0.0 && lastMove.toIsValid
                && thisMove.hDistance > 0.090 && lastMove.hDistance > 0.090 // Do not check lower speeds. The cheat would be purely cosmetic at that point, it wouldn't offer any advantage.
                && BlockProperties.isLiquid(to.getTypeId()) 
                && BlockProperties.isLiquid(from.getTypeId())
                && !onGround
                && !from.isHeadObstructed() && !to.isHeadObstructed() 
                && !Bridge1_13.isSwimming(player)) {
                hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
                bufferUse = false;
                Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
                tags.add("liquidwalk");
            }
        }
        

        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // The vDistSBLow subcheck: after taking a height difference, ensure setback distance did decrease properly. //
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // More context: when stepping up next to a block, legit clients will collide with ground with two moves (1 air->ground, 2 ground->air), while still ascending.
        // After that, there will be a final 0.15 dist ascension, then the player will proceed to descend and land on the block.   
        /*      
         *        __
         *       |  -_  _   _
         *    _ _|[ ][ ][ ][ ][ ]
         *  [ ][ ]
         */
        // Now, cheat clients that employ "legit" step modules, will jump with legit motion and then ignore the 0.15 distance mentioned above, which will make the cheater end up on ground faster (thus faster step up).
        /*        ___ _  _   _
         *    _ _|[ ][ ][ ][ ][ ]
         *  [ ][ ]
         */
        // Normally, such abprut deceleration in air would be catched by vDistRel, and the lower jump by the low-jump detection,
        // HOWEVER:
        // 1) We have a couple of workarounds that allow players to decelerate unexpectedly upon landing on ground (only with the first move);
        // 2) NCP's low-jump check relies on the in-air change of y direction (ascend->descend) to work, and as shown in the figure above, the player lands on the ground
        // without having to descend first, so we never get to pick up the lower jump.
        // Having said that, with NoCheatPlus' past-move-tracking system, we can attempt to judge ex-post if a player jumped unexpectedly
        if (cc.survivalFlyAccountingStep && !data.isVelocityJumpPhase() && data.hasSetBack() && !(thisMove.from.aboveStairs || lastMove.from.aboveStairs)
            && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_STEP, player))) {
            
            // Ensure a (valid) past lift-off state can be found within the last 10 moves
            if (Magic.isValidLiftOffAvailable(10, data)) {
                
                // We have a past lift-off state. Verify if setback distances are too low 
                if (
                    // 0: Keeping the same speed/too little deceleration between this and last move after an initial legit ascending phase.
                    thisMove.setBackYDistance < data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                    && (
                        lastMove.setBackYDistance == thisMove.setBackYDistance 
                        || lastMove.setBackYDistance - pastMove2.setBackYDistance < minJumpGain / 1.7
                    )
                    && pastMove2.setBackYDistance > pastMove3.setBackYDistance && pastMove3.setBackYDistance <= minJumpGain + jumpGainMargin
                    && pastMove3.setBackYDistance >= minJumpGain - (Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN)
                    // 0: Keeping the same speed on the first move, then too little deceleration
                    || thisMove.setBackYDistance == 0.0 && lastMove.setBackYDistance < data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                    && pastMove2.setBackYDistance > lastMove.setBackYDistance && pastMove2.setBackYDistance - lastMove.setBackYDistance < jumpGainMargin
                    // 0: Sharp deceleration 
                    // (Not observed nor tested though. This is just an educated guess.)
                    || thisMove.setBackYDistance > 0.0 && lastMove.yDistance > 0.0 && thisMove.yDistance <= 0.0
                    && thisMove.setBackYDistance < data.liftOffEnvelope.getMinJumpHeight(data.jumpAmplifier)
                    // 0: Similar case.
                    || thisMove.setBackYDistance == 0.0 && lastMove.setBackYDistance == 1.0 && thisMove.yDistance == 0.0
                    && lastMove.yDistance > 0.0) {
                    
                    // The player ended up on ground sooner than expected. 
                    if (!lastMove.from.onGround && lastMove.to.onGround && toOnGround) { // from.onGround is ignored on purpose.
                        if (data.getOrUseVerticalVelocity(thisMove.yDistance) == null) {
                            // TODO: Redesign setback handling: allow queuing setbacks and use past setback locations, instead of invaliding hDistance.
                            // Mitigate the advantage by invalidating this hDistance
                            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
                            bufferUse = false;
                            // Feed the Improbable.
                            Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
                            // Experimental: this movement was a lowjump, although catched at a later stage. Set the flag to invalidate the next jump attempt.
                            data.sfLowJump = true;
                            tags.add("lowvdistsb");
                        }
                    }
                }
            }
        }
        return new double[]{thisMove.hAllowedDistance, hDistanceAboveLimit};
    }


    /**
     * Access method from outside.
     * @param player
     * @return
     */
    public boolean isReallySneaking(final Player player) {
        return reallySneaking.contains(player.getName());
    }


    /**
     * Core y-distance checks for in-air movement (may include air -> other).<br>
     * See AirWorkarounds to check (most of) the exemption rules.
     *
     * @return
     */
    private double[] vDistAir(final long now, final Player player, final PlayerLocation from, 
                              final boolean fromOnGround, final boolean resetFrom, final PlayerLocation to, 
                              final boolean toOnGround, final boolean resetTo, 
                              final double hDistance, final double yDistance, 
                              final int multiMoveCount, final PlayerMoveData lastMove, 
                              final MovingData data, final MovingConfig cc, final IPlayerData pData) {

        double vAllowedDistance = 0.0;
        double vDistanceAboveLimit = 0.0;
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData secondLastMove = data.playerMoves.getSecondPastMove();
        final double yDistChange = lastMove.toIsValid ? yDistance - lastMove.yDistance : Double.MAX_VALUE; // Change seen from last yDistance.
        final double maxJumpGain = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);
        final int maxJumpPhase = data.liftOffEnvelope.getMaxJumpPhase(data.jumpAmplifier);
        final double jumpGainMargin = 0.005; 
        final boolean strictVdistRel;


        ///////////////////////////////////////////////////////////////////////////////////
        // Estimate the allowed relative yDistance (per-move distance check)             //
        ///////////////////////////////////////////////////////////////////////////////////
        // This is what the game checks for to apply slowfalling.
        if (thisMove.hasSlowfall && yDistance <= 0.0) {
            // Slowfalling simply reduces gravity
            vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.SLOW_FALL_GRAVITY;
            // Not sure what this is about, @Xaw3ep? :)
            if (!secondLastMove.toIsValid && vAllowedDistance < -0.035) {
               vAllowedDistance = -0.035;
            }
            strictVdistRel = true;
        }
        else if (lastMove.toIsValid && Magic.fallingEnvelope(yDistance, lastMove.yDistance, data.lastFrictionVertical, 0.0)) {
             // Ordinary falling envelope
             vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_MIN; // Minecraft base gravity is actually 0.08, why did asofold decide to use minimum and where does the magic value come from?
             strictVdistRel = true;
        }
        // Moving off from something (resetFrom accounts for everything: ground, lost ground, past on ground, media).
        else if (resetFrom || thisMove.touchedGroundWorkaround) { // Isn't touchedGroundWorkaround already included in resetFrom!?
            
            // Moving onto ground: this considers only regular on ground and past on ground states.
            if (toOnGround) {

                // Hack for boats (coarse: allows minecarts too): allow staying on the entity
                if (yDistance > cc.sfStepHeight && yDistance - cc.sfStepHeight < 0.00000003 
                    && to.isOnGroundDueToStandingOnAnEntity()) {
                    vAllowedDistance = yDistance;
                }
                // This move is fully on ground (from->to) due to a lost ground case or due to block-change activity
                // ^ Ordinary ground-to-ground movements are handled by the groundstep wildcard; vDistAir wouldn't run at all in that case.
                else {
                    vAllowedDistance = Math.max(cc.sfStepHeight, maxJumpGain + jumpGainMargin);
                    thisMove.allowstep = true;
                    thisMove.allowjump = true;
                }
            }
            // The player didn't land on ground (toOnGround returned false for both cases). Ordinary jump
            else {

                // Code duplication with the absolute limit below.
                // Allow jumping motion
                if (yDistance < 0.0 || yDistance > cc.sfStepHeight || !tags.contains("lostground_couldstep")) {
                    vAllowedDistance = maxJumpGain + jumpGainMargin;
                    thisMove.allowjump = true;
                }
                // Lostground_couldstep was applied.
                else vAllowedDistance = yDistance;
            }
            strictVdistRel = false;
        }
        // Not in the falling envelope and not moving from ground;
        else if (lastMove.toIsValid) {
            
            // TODO: Not too sure about the logic/reasoning of this condition here.
            if (lastMove.yDistance >= -Math.max(Magic.GRAVITY_MAX / 2.0, 1.3 * Math.abs(yDistance)) 
                && lastMove.yDistance <= 0.0 
                && (lastMove.touchedGround || lastMove.to.extraPropertiesValid && lastMove.to.resetCond)) {
                
                // The player yDistance fits into the envelope above and the last move covered ground or media, 
                // with the player now moving into resetcond (lost ground, media, past to on ground states, ordinary ground)
                // (ground->ground, allow stepping motion)
                if (resetTo) {
                    vAllowedDistance = cc.sfStepHeight;
                    thisMove.allowstep = true;
                }
                // last move from ground/media and this into air. Allow jumping motion
                else {
                    vAllowedDistance = maxJumpGain + jumpGainMargin;
                    thisMove.allowjump = true;
                } 
                strictVdistRel = false;
            }
            // Fully in-air move (yDistance didn't fit in the envelope above or last move wasn't from ground/media)
            else {
                // Friction.
                vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_ODD; // Upper bound.// Minecraft base gravity is actually 0.08, why did asofold decide to use minimum and where does the magic value come from?
                strictVdistRel = true;
            }
        }
        // Teleport/join/respawn.
        else {
            tags.add(lastMove.valid ? "data_reset" : "data_missing");
            // Allow falling.
            if (yDistance > -(Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN) && yDistance < 0.0) {
                vAllowedDistance = yDistance; 
                // vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_MIN;
            }
            // Allow jumping.
            else if (fromOnGround || (lastMove.valid && lastMove.to.onGround)) {
                // TODO: Is (lastMove.valid && lastMove.to.onGround) safe?
                vAllowedDistance = maxJumpGain + jumpGainMargin;
                if (lastMove.to.onGround && vAllowedDistance < 0.1) {
                    vAllowedDistance = maxJumpGain + jumpGainMargin;
                }
                // Allow stepping
                if (toOnGround) {
                    vAllowedDistance = Math.max(cc.sfStepHeight, vAllowedDistance);
                }
            }
            // Double arithmetics, moving up after join/teleport/respawn. Edge case in PaperMC/Spigot 1.7.10
            else if (Magic.skipPaper(thisMove, lastMove, data)) {
                vAllowedDistance = Magic.PAPER_DIST;
                tags.add("skip_paper");
            }
            // Do not allow any distance.
            else vAllowedDistance = 0.0;
            strictVdistRel = false;
        }

        // Compare yDistance to expected and search for an existing match. Use velocity on violations, if nothing has been found.
        /** Relative vertical distance violation. Set to true, if no workaround applies. */
        boolean vDistRelVL = false;
        /** Expected difference from current to allowed */       
        final double yDistDiffEx = yDistance - vAllowedDistance; 
        final boolean honeyBlockCollision = MovingUtil.honeyBlockSidewayCollision(from, to, data) && (MathUtil.between(-0.128, yDistance, -0.125) || thisMove.hasSlowfall && MathUtil.between(-Magic.GRAVITY_MIN, yDistance, -Magic.GRAVITY_ODD));
        final boolean GravityEffects = AirWorkarounds.oddJunction(from, to, yDistChange, yDistDiffEx, resetTo, data, cc, resetFrom);
        final boolean TooBigMove = AirWorkarounds.outOfEnvelopeExemptions(yDistDiffEx, data, from, to, now, yDistChange, player, resetTo, resetFrom, cc);
        final boolean TooShortMove = AirWorkarounds.shortMoveExemptions(yDistDiffEx, data, from, to, now, strictVdistRel, vAllowedDistance, player);
        final boolean TooFastFall = AirWorkarounds.fastFallExemptions(yDistDiffEx, data, from, to, now, strictVdistRel, yDistChange, resetTo, fromOnGround, toOnGround, player, resetFrom);
        final boolean VEnvHack = AirWorkarounds.venvHacks(from, to, yDistChange, data, resetFrom, resetTo, yDistDiffEx);
        final boolean TooBigMoveNoData = AirWorkarounds.outOfEnvelopeNoData(from, to, resetTo, data, yDistDiffEx, resetFrom, yDistChange, cc);

        if (VEnvHack || yDistDiffEx <= 0.0 && yDistDiffEx > -Magic.GRAVITY_SPAN && data.ws.use(WRPT.W_M_SF_ACCEPTED_ENV)) {
            // Accepted envelopes first
        }
        // Upper bound violation: bigger move than expected
        else if (yDistDiffEx > 0.0) { 
            
            if (TooBigMove || TooBigMoveNoData || GravityEffects 
                || (lastMove.toIsValid && honeyBlockCollision)) {
                // Several types of odd in-air moves, mostly with gravity near its maximum, friction and medium change.
            }
            else vDistRelVL = true;
        } 
        // Smaller move than expected (yDistDiffEx <= 0.0 && yDistance >= 0.0)
        else if (yDistance >= 0.0) { 

            if (TooShortMove || GravityEffects) {
                // Several types of odd in-air moves, mostly with gravity near its maximum, friction and medium change.
            }
            else vDistRelVL = true;
        }
        // Too fast fall (yDistDiffEx <= 0.0 && yDistance < 0.0)
        else { 

            if (TooFastFall || GravityEffects || honeyBlockCollision) {
                // Several types of odd in-air moves, mostly with gravity near its maximum, friction and medium change.
            }
            else vDistRelVL = true;
        }
        
        // No match found (unexpected move): use velocity as compensation, if available.
        if (vDistRelVL) {
            if (data.getOrUseVerticalVelocity(yDistance) == null) {
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(yDistance - vAllowedDistance));
                tags.add("vdistrel");
            }
        }


        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Vertical absolute distance to setback: prevent players from jumping higher than maximum jump height.           //
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        if (!pData.hasPermission(Permissions.MOVING_SURVIVALFLY_STEP, player) && yDistance > 0.0 
            && !data.isVelocityJumpPhase() && data.hasSetBack()) {
            
            final double maxJumpHeight = data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier);
            final double totalVDistViolation = to.getY() - data.getSetBackY() - maxJumpHeight;
            if (totalVDistViolation > 0.0) {
        
                if (AirWorkarounds.vDistSBExemptions(toOnGround, data, cc, now, player, totalVDistViolation, fromOnGround, tags, to, from)) {
                    // Edge cases.
                }
                // Attempt to use velocity.
                else if (data.getOrUseVerticalVelocity(yDistance) == null) {
                    vDistanceAboveLimit = Math.max(vDistanceAboveLimit, totalVDistViolation);
                    tags.add("vdistsb(" + StringUtil.fdec3.format((to.getY()-data.getSetBackY())) +"/"+ maxJumpHeight + ")");
                }
            }
        }
        

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Check on change of Y direction: prevent players from jumping lower than normal, also includes an air-jump check. //
        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        final boolean InAirPhase = !VEnvHack && !resetFrom && !resetTo;
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
                // Catch low jump between last ascending phase and current descend phase
                // NOTE: this only checks jump height, not motion speed.
                if (!data.sfLowJump && !data.sfNoLowJump && lastMove.toIsValid && lastMove.yDistance > 0.0
                    && !data.isVelocityJumpPhase()) {                    
                    /** Only count it if the player has actually been jumping (higher than setback). */
                    final double setBackYDistance = from.getY() - data.getSetBackY();
                    /** Estimation of minimal jump height */
                    final double minJumpHeight = data.liftOffEnvelope.getMinJumpHeight(data.jumpAmplifier);
                    if (setBackYDistance > 0.0 && setBackYDistance < minJumpHeight) {
                        
                        if (
                            // Head obstruction obviously allows to lowjump
                            thisMove.headObstructed 
                            || yDistance <= 0.0 && lastMove.headObstructed && lastMove.yDistance >= 0.0
                            // Skip if the player is just jumping out of powder snow
                            || BridgeMisc.hasLeatherBootsOn(player) && data.liftOffEnvelope == LiftOffEnvelope.POWDER_SNOW && lastMove.from.inPowderSnow
                            && (
                            	 // 1: Ordinary gravity envelope.
                                 lastMove.yDistance <= Magic.GRAVITY_MAX * 2.36 && lastMove.yDistance > 0.0 
                                 // 1: With slow fall
                                 || MathUtil.between(-Magic.DEFAULT_GRAVITY, thisMove.yDistance, 0.0)
                                 && lastMove.yDistance < data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier) / 2.0
                                 && thisMove.hasSlowfall
                             )) {
                            // Exempt.
                            tags.add("lowjump_skip");
                        }
                        else {
                            // Attempt to use velocity
                            if (data.getOrUseVerticalVelocity(yDistance) == null) {
                                // Violation
                                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(minJumpHeight - setBackYDistance));
                                // Set a flag to tell us that from here, this whole descending phase is due to a lowjump
                                data.sfLowJump = true;
                                // This will cost quite a bit for the player :)
                                Improbable.feed(player, (float)(minJumpHeight - setBackYDistance), System.currentTimeMillis());
                            }
                        }
                    }
                } 
            }
        }
        

        ////////////////////////////////////////////////////////
        // The Vertical Accounting subcheck: gravity enforcer //
        ////////////////////////////////////////////////////////
        if (InAirPhase && cc.survivalFlyAccountingV) {

            // Currently only for "air" phases.
            if (MovingUtil.honeyBlockSidewayCollision(from, to, data) && thisMove.yDistance < 0.0 && thisMove.yDistance > -0.21) {
                data.vDistAcc.clear();
                data.vDistAcc.add((float) -0.2033);
            }
            else if (ChangedYDir && lastMove.yDistance > 0.0) { // lastMove.toIsValid is checked above. 
                // Change to descending phase.
                data.vDistAcc.clear();
                // Allow adding 0.
                data.vDistAcc.add((float) yDistance);
            }
            else if (!thisMove.hasSlowfall && lastMove.hasSlowfall) {
                data.vDistAcc.clear();
                data.vDistAcc.add((float) yDistance);
                // Gravity transition, clear current data because it is definitely invalid now.
            }
            else if (thisMove.verVelUsed == null // Only skip if just used.
                     && !(
                          lastMove.from.inLiquid && Math.abs(yDistance) < 0.31 
                          || data.timeRiptiding + 1000 > now
                          || thisMove.hasSlowfall // TODO: Actually adapt vAcc with slowfall.
                     )) { 
                
                // Here yDistance can be negative and positive.
                data.vDistAcc.add((float) yDistance);
                final double accAboveLimit = verticalAccounting(yDistance, thisMove, data.vDistAcc, tags, "vacc" + (data.isVelocityJumpPhase() ? "dirty" : ""));
                if (accAboveLimit > vDistanceAboveLimit) {
                    if (data.getOrUseVerticalVelocity(yDistance) == null) {
                        vDistanceAboveLimit = accAboveLimit;
                    }
                }         
            }
            // Just to exclude source of error, might be redundant.
            else data.vDistAcc.clear(); 
        } 
        
        
        // (Do this check last, because it's the least useful)
        ///////////////////////////////////////////////////////////////////////////////////////
        // Air-stay-time: prevent players from ascending further than the maximum jump phase.//
        ///////////////////////////////////////////////////////////////////////////////////////
        if (!VEnvHack && data.sfJumpPhase > maxJumpPhase && !data.isVelocityJumpPhase()) {

            if (yDistance < Magic.GRAVITY_MIN) {
                // Ignore falling, and let accounting deal with it.
            }
            else if (resetFrom) {
                // Ignore bunny etc.
            }
            // Violation (Too high jumping or step).
            else if (data.getOrUseVerticalVelocity(yDistance) == null) {
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(yDistance));
                tags.add("maxphase("+ data.sfJumpPhase +"/"+ maxJumpPhase + ")");
            }
        }


        // Add lowjump tag for the whole descending phase.
        if (data.sfLowJump) {
            tags.add("lowjump");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }
    

    /**
     * Demand that with time the values decrease.<br>
     * The ActionAccumulator instance must have 3 buckets, bucket 1 is checked against
     * bucket 2, 0 is ignored. [Vertical accounting: applies to both falling and jumping]<br>
     * NOTE: This just checks and adds to tags, no change to acc.
     * 
     * @param yDistance
     * @param acc
     * @param tags
     * @param tag Tag to be added in case of a violation of this sub-check.
     * @return A violation value > 0.001, to be interpreted like a moving violation.
     */
    private static final double verticalAccounting(final double yDistance, final PlayerMoveData thisMove,
                                                   final ActionAccumulator acc, final ArrayList<String> tags, 
                                                   final String tag) {
        final int count0 = acc.bucketCount(0);
        if (count0 > 0) {
            final int count1 = acc.bucketCount(1);
            if (count1 > 0) {
                final int cap = acc.bucketCapacity();
                final float sc0;
                sc0 = (count0 == cap) ? acc.bucketScore(0) : 
                                        // Catch extreme changes quick.
                                        acc.bucketScore(0) * (float) cap / (float) count0 - Magic.GRAVITY_VACC * (float) (cap - count0);
                final float sc1 = acc.bucketScore(1);
                if (sc0 > sc1 - 3.0 * Magic.GRAVITY_VACC) {
                    // TODO: Velocity downwards fails here !!!
                    if (yDistance <= -1.05 && sc1 < -8.0 && sc0 < -8.0) {
                        // High falling speeds may pass.
                        // TODO: ^ Maybe fix high falling speeds.
                        tags.add(tag + "grace");
                        return 0.0;
                    }
                    tags.add(tag);
                    return sc0 - (sc1 - 3.0 * Magic.GRAVITY_VACC);
                }
            }
        }
        return 0.0;
    }


    /**
     * After-failure checks for horizontal distance.
     * Buffer, velocity, block move and reset-item.
     * 
     * @param player
     * @param from
     * @param to
     * @param hAllowedDistance
     * @param hDistanceAboveLimit
     * @param sprinting
     * @param thisMove
     * @param lastMove
     * @param data
     * @param cc
     * @param skipPermChecks
     * @return hAllowedDistance, hDistanceAboveLimit, hFreedom
     */
    private double[] hDistAfterFailure(final Player player, 
                                       final PlayerLocation from, final PlayerLocation to, 
                                       double hAllowedDistance, double hDistanceAboveLimit, final boolean sprinting, 
                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                       final MovingData data, final MovingConfig cc, final IPlayerData pData, final int tick, 
                                       boolean useBlockChangeTracker, final boolean fromOnGround, final boolean toOnGround) {
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        // 1: Attempt to release the item upon a NoSlow Violation, if set so in the configuration.
        if (cc.survivalFlyResetItem && hDistanceAboveLimit > 0.0 && data.sfHorizontalBuffer <= 0.5 && tags.contains("usingitem")) {
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
                double[] estimationRes = hDistChecks(from, to, pData, player, data, thisMove, lastMove, cc, sprinting, false, tick, useBlockChangeTracker, fromOnGround, toOnGround);
                hAllowedDistance = estimationRes[0];
                hDistanceAboveLimit = estimationRes[1];
            }
        }

        // 2: Check being moved by blocks.
        // 1.025 is a Magic value
        if (cc.trackBlockMove && hDistanceAboveLimit > 0.0 && hDistanceAboveLimit < 1.025) {
            // Push by 0.49-0.51 in one direction. Also observed 1.02.
            // TODO: Better also test if the per axis distance is equal to or exceeds hDistanceAboveLimit?
            // TODO: The minimum push value can be misleading (blocked by a block?)
            final double xDistance = to.getX() - from.getX();
            final double zDistance = to.getZ() - from.getZ();
            if (Math.abs(xDistance) > 0.485 && Math.abs(xDistance) < 1.025
                && from.matchBlockChange(blockChangeTracker, data.blockChangeRef, xDistance < 0 ? Direction.X_NEG : Direction.X_POS, 0.05)) {
                hAllowedDistance = thisMove.hDistance; // MAGIC
                hDistanceAboveLimit = 0.0;
            }
            else if (Math.abs(zDistance) > 0.485 && Math.abs(zDistance) < 1.025
                     && from.matchBlockChange(blockChangeTracker, data.blockChangeRef, zDistance < 0 ? Direction.Z_NEG : Direction.Z_POS, 0.05)) {
                hAllowedDistance = thisMove.hDistance; // MAGIC
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
            }
            if (hFreedom > 0.0) {
                tags.add("hvel");
                bufferUse = false; // Ensure players don't consume the buffer if velocity is present.
                hDistanceAboveLimit = Math.max(0.0, hDistanceAboveLimit - hFreedom);
            }
        }

        // 4: Finally, check for the Horizontal buffer if the hDistance is still above limit.
        if (hDistanceAboveLimit > 0.0 && data.sfHorizontalBuffer > 0.0 && bufferUse) {
            final double amount = Math.min(data.sfHorizontalBuffer, hDistanceAboveLimit);
            hDistanceAboveLimit -= amount;
            data.sfHorizontalBuffer = Math.max(0.0, data.sfHorizontalBuffer - amount); // Ensure we never end up below zero.
            tags.add("hbufuse("+ StringUtil.fdec3.format(data.sfHorizontalBuffer) + "/" + cc.hBufMax +")" );
        }
        return new double[]{hAllowedDistance, hDistanceAboveLimit, hFreedom};
    }


    /**
     * Legitimate move: increase horizontal buffer somehow.
     * @param hDistance
     * @param amount Positive amount.
     * @param data
     */
    private void hBufRegain(final double hDistance, final double amount, final MovingData data, final MovingConfig cc) {
        /*
         * TODO: Consider different concepts: 
         *          - full resetting with harder conditions.
         *          - maximum regain amount.
         *          - reset or regain only every x blocks h distance.
         */
        // TODO: Confine general conditions for buffer regain further (regain in air, whatever)?
        data.sfHorizontalBuffer = Math.min(cc.hBufMax, data.sfHorizontalBuffer + amount);
    }


    /**
     * Inside liquids vertical speed checking.<br> setFrictionJumpPhase must be set
     * externally.
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
                                 final MovingData data, final Player player, final MovingConfig cc) {

        data.sfNoLowJump = true;
        final double yDistAbs = Math.abs(yDistance);
        final double baseSpeed = thisMove.from.onGround ? Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player)) + 0.1 : Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player));
        final PlayerMoveData pastMove2 = data.playerMoves.getSecondPastMove();
        /** Slow fall gravity is applied only if the player is not sneaking (in that case, the player will descend in water with regular gravity) */
        // TODO: Rough... Needs a better modeling.
        final boolean Slowfall = !(player.isSneaking() && reallySneaking.contains(player.getName())) && thisMove.hasSlowfall;
        
        ////////////////////////
        // Minimal speed.     //
        ////////////////////////
        if (yDistAbs <= baseSpeed) {
            return new double[]{baseSpeed, 0.0};
        }
        
        
        /////////////////////////////////////////////////////////
        // 1: Vertical checking for waterlogged blocks 1.13+   //
        /////////////////////////////////////////////////////////
        // TODO: Handle this with vDistRel?
        if (from.isOnGround() && !BlockProperties.isLiquid(from.getTypeIdAbove())
            && from.isInWaterLogged()
            && !from.isInBubbleStream() && !thisMove.headObstructed
            && !from.isSubmerged(0.75)) {
            // (Envelope change shouldn't be done here but, eh.)
            data.liftOffEnvelope = LiftOffEnvelope.NORMAL;
            final double minJumpGain = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier);
            // Allow stepping.
            final boolean step = (toOnGround || thisMove.to.resetCond) && yDistance > minJumpGain && yDistance <= cc.sfStepHeight;
            final double vAllowedDistance = step ? cc.sfStepHeight : minJumpGain;
            tags.add("liquidground");
            return new double[]{vAllowedDistance, yDistance - vAllowedDistance};
        }


        /////////////////////////////////////////////////////////
        // 2: Friction envelope (allow any kind of slow down). //
        ////////////////////////////////////////////////////////
        final double frictDist = lastMove.toIsValid ? Math.abs(lastMove.yDistance) * data.lastFrictionVertical : baseSpeed; // Bounds differ with sign.
        if (lastMove.toIsValid) {
            // (Descend speed depends on how fast one dives in)
            if (lastMove.yDistance < 0.0 && yDistance < 0.0 
                && yDistAbs < frictDist + (Slowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN)) {
                tags.add("frictionenv(desc)");
                return new double[]{-frictDist - (Slowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN), 0.0};
            }
            if (lastMove.yDistance > 0.0 && yDistance > 0.0 && yDistance < frictDist - Magic.GRAVITY_SPAN) {
                tags.add("frictionenv(asc)");
                return new double[]{frictDist - Magic.GRAVITY_SPAN, 0.0};
            }
            // 1.13+ clients can bunnyhop in waterfalls and conserve more speed than ordinary ascending friction. (Observed/i.e.: 0.208 -> 0.202, almost no gravity gets applied)
            if (Bridge1_13.hasIsSwimming() && data.insideMediumCount < 19
                && yDistance > 0.0 && lastMove.yDistance > 0.0 && yDistance <= lastMove.yDistance * 0.99 // (Don't care about gravity here)
                && thisMove.from.inWater && (thisMove.inWaterfall || pastMove2.inWaterfall)
                && yDistance < LiftOffEnvelope.NORMAL.getMaxJumpGain(0.0)) {
                tags.add("waterfall(asc)");
                return new double[]{lastMove.yDistance * 0.99, 0.0};
            }
            // ("== 0.0" is covered by the minimal speed check above.)
        }
        
        
        ////////////////////////////////////
        // 3: Handle bubble columns 1.13+ // 
        ////////////////////////////////////
        // TODO: bubble columns adjacent to each other: push prevails on drag. So do check adjacent blocks and see if they are draggable as well.
        //[^ Problem: we only get told if the column can drag players]
        // TODO (Still quite rough: Minecraft acceleration(s) is different actually, but it doesn't seem to work at all with NCP...)
        if (from.isInBubbleStream() || to.isInBubbleStream()) {
            // Minecraft distinguishes above VS inside, so we need to do it as well.
            // This is what it does to determine if the player is above the stream (check for air above)
            if (BlockProperties.isAir(from.getTypeIdAbove())) {
                tags.add("abovestreamasc");
                double vAllowedDistance = Math.min(1.8, lastMove.yDistance + 0.2);
                return new double[]{vAllowedDistance, yDistAbs - vAllowedDistance};
            }
            // Inside.
            tags.add("insidecolumnasc");
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
        // (Seems to work OK)
        if (from.isDraggedByBubbleStream() || to.isDraggedByBubbleStream()) {
            if (BlockProperties.isAir(from.getTypeIdAbove())) {
                tags.add("abovestreamdesc");
                // -0.03 is the effective acceleration, capped at -0.9
                double vAllowedDistance = Math.max(-0.9, lastMove.yDistance - 0.03);
                return new double[]{vAllowedDistance, yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0}; 
            }
            // Inside
            tags.add("insidestreamdesc");
            if (lastMove.yDistance > 0.0 && yDistance > 0.0
                && !thisMove.headObstructed) {
                // If inside a bubble column, and the player is getting dragged, they cannot ascend
                return new double[]{0.0, yDistAbs};
            }
            double vAllowedDistance = Math.max(-0.3, lastMove.yDistance - 0.03);
            return new double[]{vAllowedDistance, yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0}; 
        }


        ///////////////////////////////////////
        // 4: Workarounds for special cases. // 
        ///////////////////////////////////////
        final Double wRes = LiquidWorkarounds.liquidWorkarounds(from, to, baseSpeed, frictDist, lastMove, data);
        if (wRes != null) {
            return new double[]{wRes, 0.0};
        }


        ///////////////////////////////////////////////
        // 5: Try to use velocity for compensation.  //
        ///////////////////////////////////////////////
        if (data.getOrUseVerticalVelocity(yDistance) != null) {
            return new double[]{yDistance, 0.0};
        }
        

        ///////////////////////////////////
        // 6: At this point a violation. //
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
     * On-climbable vertical distance checking.
     * @param from
     * @param fromOnGround
     * @param toOnGround
     * @param lastMove 
     * @param thisMove 
     * @param yDistance
     * @param data
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistClimbable(final Player player, final PlayerLocation from, final PlayerLocation to,
                                    final boolean fromOnGround, final boolean toOnGround, 
                                    final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                    final double yDistance, final MovingData data) {
        data.sfNoLowJump = true;
        data.clearActiveHorVel(); // Might want to clear ALL horizontal vel.
        double vDistanceAboveLimit = 0.0;
        double yDistAbs = Math.abs(yDistance);
        /** Climbing a ladder in water and exiting water for whatever reason speeds up the player a lot in that one transition ... */
        boolean waterStep = lastMove.from.inLiquid && yDistAbs < Magic.swimBaseSpeedV(Bridge1_13.hasIsSwimming());
        double vAllowedDistance = waterStep ? yDistAbs : yDistance < 0.0 ? Magic.climbSpeedDescend : Magic.climbSpeedAscend;
        final double jumpHeight = LiftOffEnvelope.NORMAL.getMaxJumpHeight(data.jumpAmplifier);
        final double maxJumpGain = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);
        // Quick, temporary fix for scaffolding block
        boolean scaffolding = from.isOnGround() && from.getBlockY() == Location.locToBlock(from.getY()) 
                              && yDistance > 0.0 && yDistance < maxJumpGain;
        if (yDistAbs > vAllowedDistance) {
            // Speed is higher than legit motion
            if (from.isOnGround(jumpHeight, 0D, 0D, BlockFlags.F_CLIMBABLE)) {
            	// We don't throw an immediate violation here, unless the player's motion is higher than ordinary (0.42):
            	// that means the player is trying to "step up" way too fast / instantly climb the block.
                if (yDistance > maxJumpGain) {
                    vDistanceAboveLimit = Math.max(vDistanceAboveLimit, yDistAbs - vAllowedDistance);
                    tags.add("climbstep");
                }
                else tags.add("climbheight("+ StringUtil.fdec3.format(yDistance) +")");
            }
            else if (!scaffolding) {
            	// Ground is out of jump height reach, violation.
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, yDistAbs - vAllowedDistance);
                tags.add("climbspeed");
            }
        }
        
        // Can't climb up with vine not attached to a solid block (legacy).
        if (yDistance > 0.0 && !thisMove.touchedGround && !from.canClimbUp(jumpHeight)) {
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
     * In-web vertical distance checking.
     * @param player
     * @param from
     * @param to
     * @param toOnGround
     * @param hDistanceAboveLimit
     * @param yDistance
     * @param now
     * @param data
     * @param cc
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistWeb(final Player player, final PlayerMoveData thisMove, final boolean fromOnGround,
                              final boolean toOnGround, final double hDistanceAboveLimit, final long now, 
                              final MovingData data, final MovingConfig cc, final PlayerLocation from, final PlayerLocation to,
                              final IPlayerData pData) {
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double yDistance = thisMove.yDistance;
        double vAllowedDistance, vDistanceAboveLimit;
        data.sfNoLowJump = true;
        data.jumpAmplifier = 0; 
        
        if (fromOnGround) {
            // Handle ground movement
            if (toOnGround) {
                // Allow stepping motion.
                vAllowedDistance = cc.sfStepHeight;
            }
            // Allow jumping motion
            else vAllowedDistance = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier) + Magic.GRAVITY_MAX; 
            vDistanceAboveLimit = yDistance - vAllowedDistance;
        }
        else if (yDistance > 0.0) {
            // Handle ascending
            if (!lastMove.from.inWeb) {
                // For leniency: in the case players hop right into a web while still ascending.
                vAllowedDistance = lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR - Magic.GRAVITY_MIN;
            }
            else if ((from.getBlockFlags() & BlockFlags.F_BUBBLECOLUMN) != 0 && !from.isDraggedByBubbleStream() && yDistance < Magic.bubbleStreamAscend) {
                // Bubble columns can slowly push the player upwards through the web.
                vAllowedDistance = lastMove.yDistance * Magic.FRICTION_MEDIUM_WATER;
            }
            // Cannot ascend in webs otherwise.
            else vAllowedDistance = 0.0;
            vDistanceAboveLimit = yDistance - vAllowedDistance;
        }
        else {
            // Handle descending
        	// NOTE: Descend speed is a massive pain to model here due to movements not reaching the player move event thresholds and split moves.
        	// Player can fall faster/slower if: they spam WASD; they stand still while falling through; are sneaking and such.
        	// Not worth the hassle to account for each and every bullshit.
            // NOTE: falling speed is static
        	if (!thisMove.to.inWeb && lastMove.yDistance < 0.0) {
        		// Falling from below.
        		// Falling speed varies here: -0.11 / -0.07(+-) observed
        		vAllowedDistance = -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN;
        	}
        	else if (!lastMove.from.inWeb && lastMove.yDistance < 0.0) {
                // Leniency for falling from high enough places.
                vAllowedDistance = lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR - (thisMove.hasSlowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MIN);
            }
        	else if (thisMove.hasSlowfall) {
                // Usuallly, the movement here is smaller than what the PlayerMoveEvent can handle.
                // Enforce slower falling speed.
                vAllowedDistance = -Magic.GRAVITY_SPAN * Magic.FRICTION_MEDIUM_AIR + Magic.SLOW_FALL_GRAVITY;
            }
            // Ordinary. Can't be stricter here because spamming WASD changes falling speed. Because reasons.
            else vAllowedDistance = -Magic.GRAVITY_MIN * Magic.FRICTION_MEDIUM_AIR;
            // (We only care about players falling faster than legit, don't care about falling too slowly)
            vDistanceAboveLimit = yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0;
        }
        
        if (data.getOrUseVerticalVelocity(yDistance) != null && vDistanceAboveLimit > 0.0) {
            vDistanceAboveLimit = 0.0;
        }
        if (vDistanceAboveLimit > 0.0) {
        	tags.add(yDistance >= 0.0 ? "vwebasc" : "vwebdesc");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }


    /**
     * Berry bush vertical distance checking (1.14+)
     * @param player
     * @param from
     * @param to
     * @param toOnGround
     * @param hDistanceAboveLimit
     * @param yDistance
     * @param now
     * @param data
     * @param cc
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistBush(final Player player, final PlayerMoveData thisMove, 
                               final boolean toOnGround, final double hDistanceAboveLimit, final long now, 
                               final MovingData data, final MovingConfig cc, final PlayerLocation from, final PlayerLocation to,
                               final boolean fromOnGround, final IPlayerData pData) {
        final double yDistance = thisMove.yDistance;
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double jumpGainMargin = 0.002;
        double vAllowedDistance, vDistanceAboveLimit;
        if (fromOnGround) {
            // Handle ground movement
            if (toOnGround) {
                // Allow stepping motion.
                vAllowedDistance = cc.sfStepHeight;
            }
            // Allow jumping motion
            else vAllowedDistance = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier, data.lastBlockFrictionVertical) + jumpGainMargin; 
            vDistanceAboveLimit = yDistance - vAllowedDistance;
        }
        else if (yDistance > 0.0) {
            // Handle ascending
            if (!lastMove.from.inBerryBush) {
                // For leniency: in the case players hop right into a bush while still ascending.
                vAllowedDistance = lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR - Magic.GRAVITY_SPAN;
            }
            // Cannot ascend in a bush otherwise.
            else vAllowedDistance = 0.0;
            vDistanceAboveLimit = yDistance - vAllowedDistance;
        }
        else {
            // Handle descending
            // Note that falling speed is static
            if (!thisMove.to.inBerryBush && lastMove.yDistance < 0.0) {
            	// Exiting a bush from below, technically not possible because bushes need a dirt block to support them, just in case plugins get funny ideas.
            	vAllowedDistance = -Magic.GRAVITY_MAX; // Arbitrary. Fuck it.
            }
            else if (!lastMove.from.inBerryBush && lastMove.yDistance < 0.0) {
                // Leniency for falling from a high enough place onto a bush
                vAllowedDistance = lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR - (thisMove.hasSlowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MAX);
            }
            else if (thisMove.hasSlowfall
            		// Spare transition from ascending to descending
            		&& !(lastMove.yDistance > 0.0 && thisMove.yDistance < 0.0)) {
            	// Why, WHY do players fall faster if moving horizontally. This game...
            	// Technically, sneaking affects motion as well here...
            	vAllowedDistance = thisMove.hDistance > 0.0 ? -Magic.GRAVITY_SPAN * 1.43 : -Magic.GRAVITY_VACC * 0.99;
            }
            // Ordinary
            // (Falling speed seems to be kept reliably, unlike webs here)
            else vAllowedDistance = -Magic.GRAVITY_MIN * Magic.FRICTION_MEDIUM_AIR - 0.0005;
            // (We only care about players falling faster than legit, don't care about falling too slowly)
            vDistanceAboveLimit = yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0;
        }
 
        if (data.getOrUseVerticalVelocity(yDistance) != null
        	// Don't ignore PVP and block-move velocity, do ignore other sources.
        	&& thisMove.verVelUsed != null 
            && (thisMove.verVelUsed.flags & (VelocityFlags.ORIGIN_BLOCK_MOVE | VelocityFlags.ORIGIN_PVP)) != 0
            && vDistanceAboveLimit > 0.0) {
            vDistanceAboveLimit = 0.0;
        }
        if (vDistanceAboveLimit > 0.0) {
        	tags.add(yDistance >= 0.0 ? "vbushasc" : "vbushdesc");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }


   /**
    * Powder snow vertical distance checking (1.17+): behaves similarly to a climbable block.<br>
    * This does not concern if the player can actually stand on the block (See MovingListener#checkPlayerMove for that)
    * @param yDistance
    * @param form
    * @param to
    * @param cc
    * @param data
    * @return vAllowedDistance, vDistanceAboveLimit
    * 
    */
    private double[] vDistPowderSnow(final double yDistance, final PlayerLocation from, final PlayerLocation to, 
                                     final MovingConfig cc, final MovingData data, final Player player, final IPlayerData pData,
                                     final long now) {
        double vAllowedDistance; 
        double vDistanceAboveLimit = 0.0;
        final double yToBlock = from.getY() - from.getBlockY();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData secondlastMove = data.playerMoves.getSecondPastMove();
        boolean jump = false;
   
        if (yToBlock < Magic.GRAVITY_ODD) {
        	// Close enough to ground below, allow jumping motion.
            vAllowedDistance = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier, data.lastBlockFrictionVertical);
            jump = true;
        }
        else {
        	// Too far from the block, enforce climbing motion
            if (BridgeMisc.hasLeatherBootsOn(player)) {
            	// If the player has leather boots, they are allowed to climb this block
            	if (thisMove.hasSlowfall && yDistance > 0.0 && lastMove.yDistance < 0.0
            		&& !thisMove.to.inPowderSnow && (!lastMove.from.inPowderSnow || !secondlastMove.from.inPowderSnow) && player.isSneaking() && reallySneaking.contains(player.getName())) {
            		// Shift+Space bar on top of powder snow: yDistance inversion, from a negastive to a positive amount. Appears only with slowfall. (-> -0.095 -> 0.279 (+0.374 gain, sometimes even more)
            		vAllowedDistance = Math.abs(lastMove.yDistance * data.lastBlockFrictionVertical * data.lastFrictionVertical - 0.25);

            	}
                else if (thisMove.yDistance < 0.0 && lastMove.yDistance < 0.0 && !lastMove.from.inPowderSnow) {
            		// Hopping onto a snow block and sinking in.
            		vAllowedDistance = lastMove.yDistance * data.lastBlockFrictionVertical - Magic.GRAVITY_MIN - Magic.GRAVITY_VACC;
            	}
            	else if (thisMove.hasSlowfall && yDistance < 0.0 && lastMove.yDistance < 0.0) { 
            		// Enforce slower falling speed.
            		// (Both descending moves: when jumping the player keeps ordinary falling speed on the first descending move 0.63 -> -0.118)
                	vAllowedDistance = thisMove.hDistance > 0.0 ? -Magic.GRAVITY_ODD - Magic.SLOW_FALL_GRAVITY - 0.0002  : -Magic.GRAVITY_ODD; 
                }
            	// Ordinary climbing motion
            	else vAllowedDistance = yDistance < 0.0 ? -Magic.snowClimbSpeedDescend : Magic.snowClimbSpeedAscend;
            	tags.add("bootson");
            } 
            else {
            	if (lastMove.yDistance > 0.0 && thisMove.yDistance > 0.0 
            		&& thisMove.to.inPowderSnow && lastMove.from.inPowderSnow) {
            		// One cannot ascend in powder snow without boots
            		// (== 0.0 is covered by nogravityblock)
            		vAllowedDistance = 0.0;
            		tags.add("nobootsasc");
            	}
            	else if (thisMove.yDistance < 0.0 && lastMove.yDistance < 0.0 && !lastMove.from.inPowderSnow) {
            		// Hopping onto a snow block and sinking in.
            		vAllowedDistance = lastMove.yDistance * data.lastBlockFrictionVertical * data.lastFrictionVertical - Magic.GRAVITY_MAX - Magic.GRAVITY_VACC;
            	}
            	else if (thisMove.hasSlowfall && (yDistance < 0.0 && lastMove.yDistance < 0.0
            			// Odd in-air stop (neg -> 0 -> neg (with Distance being the same as the second last move)
            			|| thisMove.yDistance == 0.0 && lastMove.yDistance < 0.0 && lastMove.yDistance - thisMove.yDistance == lastMove.yDistance)) {
            		// Enforce slower falling speed 
                	vAllowedDistance = thisMove.hDistance > 0.0 ? -Magic.GRAVITY_ODD - Magic.SLOW_FALL_GRAVITY - 0.0002  : -Magic.GRAVITY_ODD; 
            	}
            	// Descend motion.
            	else vAllowedDistance = -Magic.snowClimbSpeedDescend;
                tags.add("bootsoff");
            }
        }
        /** Expected difference from actual distance to allowed. */
        final double yDistDiffEx = yDistance - vAllowedDistance;
        final boolean violation = jump ? yDistDiffEx > 0.0 : Math.abs(yDistance) > Math.abs(vAllowedDistance);
        vDistanceAboveLimit = violation ? Math.abs(yDistDiffEx) : 0.0;
        if (data.getOrUseVerticalVelocity(yDistance) != null
        	// Don't ignore PVP and block-move velocity, do ignore other sources.
        	&& thisMove.verVelUsed != null 
            && (thisMove.verVelUsed.flags & (VelocityFlags.ORIGIN_BLOCK_MOVE | VelocityFlags.ORIGIN_PVP)) != 0
            && vDistanceAboveLimit > 0.0) {
            vDistanceAboveLimit = 0.0;
        }
        if (vDistanceAboveLimit > 0.0) {
        	tags.add(yDistance >= 0.0 ? "vsnowasc" : "vsnowdesc");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }
    
    
    /**
     * Levitation vertical distance checking.<br>
     * Put in its own method because it applies on/in any medium except liquid.
     * @param pData
     * @param player
     * @param data
     * @param from
     * @param to
     * @param cc
     * @return
     */
    private double[] vDistLevitation(final IPlayerData pData, final Player player, final MovingData data, final PlayerLocation from, 
                                     final PlayerLocation to, final MovingConfig cc, final boolean fromOnGround) {
        double vAllowedDistance;
        double vDistanceAboveLimit = 0.0;
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double yDistance = thisMove.yDistance;
        final long now = System.currentTimeMillis();
        final double LevitationLevel = Bridge1_9.getLevitationAmplifier(player) + 1;
        final double velocityAmount = (yDistance - 0.01 * Bridge1_9.getLevitationAmplifier(player)) / 0.8 + 0.221;
        data.sfNoLowJump = true;
        
        // TODO: Refine checking at a later time. Prevent too slow ascend as well
        ///////////////////////////////////////////////////////////
        // Estimate the allowed motion (per-move distance check) //
        ///////////////////////////////////////////////////////////
        // Account for a Minecraft bug: sbyte overflow with levitation level at or above 127
        if (LevitationLevel > 126) {
            // Using /effect minecraft:levitation 255 negates all sort of gravitational force on the player
            // Levitation level over 127 = fall down at a fast or slow rate, depending on the value.
            vAllowedDistance = yDistance; // Not ideal, but what can we do. We can't account for each and every vanilla bullshit... Let EXTREME_MOVE catch absurd distances and call it a day.
            vDistanceAboveLimit = 0.0;
        }
        else {
            /** Workaround. Speed is slowed down by too much with stuck-speed factors */
            double extraGainForStuckInBlock = lastMove.from.inWeb || lastMove.to.inWeb ? 0.1 : data.liftOffEnvelope.getMinJumpGain(0.0) / 2.5;
            // Levitation forces the player to ascend (speed is dependant on the level). This is the NMS formula
            vAllowedDistance = (Magic.resetCond(lastMove) ? lastMove.yDistance + extraGainForStuckInBlock : lastMove.yDistance) * Magic.FRICTION_MEDIUM_AIR * data.lastBlockFrictionVertical;
            vAllowedDistance += (Magic.GRAVITY_ODD * LevitationLevel - lastMove.yDistance) * 0.2;
            vDistanceAboveLimit = Math.max(vDistanceAboveLimit, yDistance - vAllowedDistance);
            if (vDistanceAboveLimit > 0.0) {
                tags.add("vdistlev");
            }
        } 
          
        
        // (Auxiliary check)
        //////////////////////////////////////////////////////////////////////////////////////////
        // The antilevitation subcheck: prevent players from ignoring the levitation effect     //
        //////////////////////////////////////////////////////////////////////////////////////////
        if (Bridge1_9.getLevitationAmplifier(player) < 127 && lastMove.toIsValid && lastMove.hasLevitation) { 
        	final double allowY = (lastMove.yDistance + (0.05D * LevitationLevel - lastMove.yDistance) * 0.2D) * Magic.FRICTION_MEDIUM_AIR;
            // TODO: Wrong friction
            if (thisMove.headObstructed
                // Exempt check for 10 seconds after joined
                && !(now > pData.getLastJoinTime() && pData.getLastJoinTime() + 10000 > now)
                // THis condition allows to descend (!)
                // && !(thisMove.yDistance < 0.0 && lastMove.yDistance - thisMove.yDistance < 0.0001)
                ) {
 
                if (lastMove.yDistance < 0.0 && thisMove.yDistance < allowY
                    || from.getY() >= to.getY() && !(thisMove.yDistance == 0.0 && allowY < 0.0)) {
                    vDistanceAboveLimit = Math.max(vDistanceAboveLimit, 0.1);
                    tags.add("antilevitate");
                }
            }
        }
        // Attempt to consume velocity
        if (vDistanceAboveLimit > 0.0 && data.getOrUseVerticalVelocity(velocityAmount) != null) {
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
                                    final MovingData data, final MovingConfig cc){

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
            // TODO: Evaluate how data resetting can be done minimal (skip certain things flags)?
            data.clearAccounting();
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
    private void outputDebug(final Player player, final PlayerLocation to, 
                             final MovingData data, final MovingConfig cc, 
                             final double hDistance, final double hAllowedDistance, final double hFreedom, 
                             final double yDistance, final double vAllowedDistance,
                             final boolean fromOnGround, final boolean resetFrom, 
                             final boolean toOnGround, final boolean resetTo,
                             final PlayerMoveData thisMove, double vDistanceAboveLimit) {

        // TODO: Show player name once (!)
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double yDistDiffEx = yDistance - vAllowedDistance;
        final StringBuilder builder = new StringBuilder(500);
        builder.append(CheckUtils.getLogMessagePrefix(player, type));
        final String hBuf = (data.sfHorizontalBuffer < cc.hBufMax ? ((" / Buffer: " + StringUtil.fdec3.format(data.sfHorizontalBuffer))) : "");
        final String hVelUsed = hFreedom > 0 ? " / hVelUsed: " + StringUtil.fdec3.format(hFreedom) : "";
        builder.append("\nOnGround: " + (thisMove.headObstructed ? "(head obstr.) " : "") + (thisMove.touchedGroundWorkaround ? "(lost ground) " : "") + (fromOnGround ? "onground -> " : (resetFrom ? "resetcond -> " : "--- -> ")) + (toOnGround ? "onground" : (resetTo ? "resetcond" : "---")) + ", jumpPhase: " + data.sfJumpPhase + ", LiftOff: " + data.liftOffEnvelope.name() + "(" + data.insideMediumCount + ")");
        final String dHDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(hDistance, lastMove.hDistance) + ")" : "";
        final String dYDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(yDistance, lastMove.yDistance)+ ")" : "";
        final String frictionTick = ("keepFrictionTick= " + data.keepfrictiontick + " , ");
        builder.append("\n Tick counters: " + frictionTick);
        builder.append("\n" + " hDist: " + StringUtil.fdec3.format(hDistance) + dHDist + " / hAD: " + StringUtil.fdec3.format(hAllowedDistance) + hBuf + hVelUsed +
                       "\n" + " vDist: " + StringUtil.fdec3.format(yDistance) + dYDist + " / yDistDiffEx: " + StringUtil.fdec3.format(yDistDiffEx) + " / vAD: " + StringUtil.fdec3.format(vAllowedDistance) + " , setBackY: " + (data.hasSetBack() ? (data.getSetBackY() + " (setBackYDist: " + StringUtil.fdec3.format(to.getY() - data.getSetBackY()) + " / MaxJumpHeight: " + data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier) + ")") : "?"));
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
        if (!resetFrom && !resetTo) {
            if (cc.survivalFlyAccountingV && data.vDistAcc.count() > data.vDistAcc.bucketCapacity()) {
                builder.append("\n" + " vAcc: " + data.vDistAcc.toInformalString());
            }
        }
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