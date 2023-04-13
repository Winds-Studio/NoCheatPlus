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
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;
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
    private final boolean ServerIsAtLeast1_9 = ServerVersion.compareMinecraftVersion("1.9") >= 0;
    /** To join some tags with moving check violations. */
    private final ArrayList<String> tags = new ArrayList<String>(15);
    private final ArrayList<String> justUsedWorkarounds = new ArrayList<String>();
    private final Set<String> reallySneaking = new HashSet<String>(30);
    private final BlockChangeTracker blockChangeTracker;
    /** Location for temporary use with getLocation(useLoc). Always call setWorld(null) after use. Use LocUtil.clone before passing to other API. */
    private final Location useLoc = new Location(null, 0, 0, 0);
    private IGenericInstanceHandle<IAttributeAccess> attributeAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IAttributeAccess.class);
    // TODO: handle
    

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
     * @param isModernSplitMove
     *           Flag to indicate if the packet-based split move mechanic is used instead of the Bukkit-based one.
     * @return
     */
    public Location check(final Player player, final PlayerLocation from, final PlayerLocation to, 
                          final int multiMoveCount, final MovingData data, final MovingConfig cc, 
                          final IPlayerData pData, final int tick, final long now, 
                          final boolean useBlockChangeTracker, final boolean isModernSplitMove) {
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
            data.adjustLiftOffEnvelope(from);
        }
        
        /** From a reset-condition location, lostground is accounted for here. */
        // TODO: This isn't correct, needs redesign.
        final boolean resetFrom = fromOnGround || from.isResetCond() 
                                || isSamePos && lastMove.toIsValid && LostGround.lostGroundStill(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags)
                                || LostGround.lostGround(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, useBlockChangeTracker ? blockChangeTracker : null, tags);

        // Alter some data before checking anything
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
            double[] estimationRes = hDistChecks(from, to, pData, player, data, thisMove, lastMove, cc, sprinting, true, tick, 
                                                 useBlockChangeTracker, fromOnGround, toOnGround, debug, multiMoveCount, isModernSplitMove);
            hAllowedDistance = estimationRes[0];
            hDistanceAboveLimit = estimationRes[1];
            // The player went beyond the allowed limit, execute the after failure checks.
            if (hDistanceAboveLimit > 0.0) {
                double[] failureResult = hDistAfterFailure(player, multiMoveCount, from, to, hAllowedDistance, hDistanceAboveLimit, sprinting, 
                                                           thisMove, lastMove, debug, data, cc, pData, tick, useBlockChangeTracker, fromOnGround, 
                                                           toOnGround, isModernSplitMove);
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
        // Step wild-card: allow step height from ground to ground, if not on/in a medium already.
        if (yDistance >= 0.0 && yDistance <= cc.sfStepHeight 
            && toOnGround && fromOnGround && !from.isResetCond()
            && !thisMove.hasLevitation) {
            vAllowedDistance = cc.sfStepHeight;
            thisMove.canStep = true;
            tags.add("groundstep");
        }
        else if (thisMove.hasLevitation && !from.isInLiquid() && !to.isInLiquid()) {
            // Levitation cannot work in liquids
            final double[] resultLevitation = vDistLevitation(pData, player, data, from, to, cc, fromOnGround, multiMoveCount, isModernSplitMove);
            vAllowedDistance = resultLevitation[0];
            vDistanceAboveLimit = resultLevitation[1];
        }
        else if (from.isOnClimbable()) {
            // Climbable speed has priority over stuck speed: else false positives.
            // They can technically be placed inside liquids.
            final double[] resultClimbable = vDistClimbable(player, from, to, fromOnGround, toOnGround, pData, thisMove, lastMove, yDistance, data, cc);
            vAllowedDistance = resultClimbable[0];
            vDistanceAboveLimit = resultClimbable[1];
        }
        else if (from.isInPowderSnow()) {
            // Powder snow cannot be placed in liquids.
            final double[] resultSnow = vDistPowderSnow(yDistance, from, to, cc, data, player, pData, now);
            vAllowedDistance = resultSnow[0];
            vDistanceAboveLimit = resultSnow[1];
        }
        else if (from.isInWeb()) {
            // Webs can be placed in liquids.
            final double[] resultWeb = vDistWeb(player, thisMove, fromOnGround, toOnGround, data, cc, from, to, pData, isModernSplitMove, multiMoveCount);
            vAllowedDistance = resultWeb[0];
            vDistanceAboveLimit = resultWeb[1];
        }
        else if (from.isInBerryBush()) {
            // Berry bushes cannot be placed in liquids.
            final double[] resultBush = vDistBush(player, thisMove, toOnGround, now, data, cc, from, to, fromOnGround, pData, isModernSplitMove, multiMoveCount);
            vAllowedDistance = resultBush[0];
            vDistanceAboveLimit = resultBush[1];
        }
        else if (from.isInLiquid()) { 
            // Minecraft checks for liquids first, then for air.
            final double[] resultLiquid = vDistLiquid(thisMove, from, to, toOnGround, yDistance, lastMove, data, player, cc);
            vAllowedDistance = resultLiquid[0];
            vDistanceAboveLimit = resultLiquid[1];

            // The friction jump phase has to be set externally.
            if (vDistanceAboveLimit <= 0.0 && yDistance > 0.0 
                && Math.abs(yDistance) > Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player))) {
                data.setFrictionJumpPhase();
            }
        }
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
                    if (debug) {
                        debug(player, "Slope: schedule sfNoLowJump.");
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
                                  final boolean sneaking, boolean checkPermissions, final Collection<String> tags, 
                                  final PlayerLocation to, final PlayerLocation from, final boolean debug,
                                  final boolean fromOnGround, final boolean toOnGround, 
                                  final boolean onGround, final boolean sprinting, final PlayerMoveData lastMove, 
                                  final int tick, boolean useBlockChangeTracker) {
        final MovingData data = pData.getGenericInstance(MovingData.class);      
        final CombinedData cData = pData.getGenericInstance(CombinedData.class); 
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);   
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final boolean sneakingOnGround = thisMove.touchedGround && player.isSneaking() && reallySneaking.contains(player.getName());
        /*
         * NOTES: Attack-slowdown is done in the FightListener (!).
         * Order of operations is essential. Do not shuffle things around unless you know what you're doing.
         */
        // Initialize the allowed distance(s) with the previous speed. (Only if we have end-point coordinates)
        thisMove.xAllowedDistance = lastMove.toIsValid ? lastMove.to.getX() - lastMove.from.getX() : 0.0;
        thisMove.zAllowedDistance = lastMove.toIsValid ? lastMove.to.getZ() - lastMove.from.getZ() : 0.0;
        // When a WASD key is pressed, these are set to 1 or -1, depending on the movement direction.
        // They are then multiplied by 0.98 before being passed to the travel() method.
        /** xXa: left = 0.98, right = -0.98 */
        float strafe = 0.0f;
        String strafeDir = "";
        /** zZa: forward = 0.98, backwards = -0.98) */
        float forward = 0.0f;
        String forwardDir = "";
        
        // 1: Attempt to calculate the player's input (placeholder method, needs to be replaced)
        // TODO: Input vector (press ASWD) is independent on how player look(a.k.a yaw) so this one likely wrong
        // (Inspired from this thread: https://www.spigotmc.org/threads/player-movement-direction.389134/)
        Vector movementDirection = new Vector(to.getX()-from.getX(), 0.0, to.getZ()-from.getZ());
        Vector yawDirection = TrigUtil.getHorizontalDirection(to.getYaw());
        // Differentiate left from right / forward from backwards
        final boolean isVectorDir = movementDirection.clone().crossProduct(yawDirection).dot(new Vector(0, 1, 0)) >= 0;
        final double angle = ((isVectorDir ? -1 : 1) * TrigUtil.angle(movementDirection, yawDirection));
        if (debug) {
            player.sendMessage("[SurvivalFly] (updateHorizontalSpeed) Input angle: " + angle);
        }
        if (MathUtil.inRange(0.0, angle, Math.PI / 2.5)
            || MathUtil.inRange(-(Math.PI / 2.5), angle, 0.0)) {
            forward = 0.98f;
            forwardDir = "FORWARD";
        }
        else if (angle > Math.PI / 1.8 || angle < -(Math.PI / 1.8)) {
            forward = -0.98f;
            forwardDir = "BACKWARDS";
        }

        if (MathUtil.inRange(0.2, angle, Math.PI / 1.8)
            || MathUtil.inRange(Math.PI / 1.8, angle, Math.PI / 1.2)) {
            strafe = 0.98f;
            strafeDir = "LEFT";
        }
        else if (MathUtil.inRange(-(Math.PI / 1.8), angle, -0.2)
                || MathUtil.inRange(-(Math.PI / 1.2), angle, -(Math.PI / 1.8))) {
            strafe = -0.98f;
            strafeDir = "RIGHT";
        }
        if (debug) {
            player.sendMessage("[SurvivalFly] (updateHorizontalSpeed) Estimated direction: " + forwardDir +" | "+ strafeDir);
        }
        // TODO: Brute-force through all possible combinations of movements (8 - left/right/backwards/forward/b-left/b-right/f-left/f-right)



        // From KeyboardInput.java (MC-Reborn tool)
        if (sneaking && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_SNEAKING, player))) {
            tags.add("sneaking");
            // Take care of swift sneak (Formula is from LocalPlayer aiStep)
            float SwiftSneakIncrement = MathUtil.clamp(BridgeEnchant.getSwiftSneakLevel(player) * 0.15f, 0.0f, 1.0f);
            strafe *= (Magic.SNEAK_MULTIPLIER + SwiftSneakIncrement);
            forward *= (Magic.SNEAK_MULTIPLIER + SwiftSneakIncrement);
            // Account for NCP base speed modifiers.
            strafe *= cc.survivalFlySneakingSpeed / 100;
            forward *= cc.survivalFlySneakingSpeed / 100;
        }
        // From LocalPlayer.java.aiStep()
        if ((cData.isUsingItem || player.isBlocking()) 
            && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))) {
            tags.add("usingitem");
            strafe *= Magic.USING_ITEM_MULTIPLIER;
            forward *= Magic.USING_ITEM_MULTIPLIER;
            strafe *= cc.survivalFlyBlockingSpeed / 100;
            forward *= cc.survivalFlyBlockingSpeed / 100;
        } 
        
        // Friction first.
        thisMove.xAllowedDistance *= (double) data.lastInertia;
        thisMove.zAllowedDistance *= (double) data.lastInertia;
        
        // Block speed (after friction suits best)
        thisMove.xAllowedDistance *= (double) data.lastBlockSpeedMultiplier;
        thisMove.zAllowedDistance *= (double) data.lastBlockSpeedMultiplier;
        
        // Sliding speed
        if (MovingUtil.isSlidingDown(from, mcAccess.getHandle().getWidth(player), thisMove, player)
            && MovingUtil.honeyBlockSidewayCollision(from, to, data)) {
            if (thisMove.yDistance < -Magic.SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD) {
                thisMove.xAllowedDistance *= -Magic.SLIDE_SPEED_THROTTLE / thisMove.yDistance;
                thisMove.zAllowedDistance *= -Magic.SLIDE_SPEED_THROTTLE / thisMove.yDistance;
                tags.add("honeyslide");
            }
        }

        // Slime speed
        // From BlockSlime.java
        // (Ground check is already included)
        if (from.isOnSlimeBlock()) {
            if (Math.abs(thisMove.yDistance) < 0.1 && !sneaking) {
                thisMove.xAllowedDistance *= 0.4 + Math.abs(thisMove.yDistance) * 0.2;
                thisMove.zAllowedDistance *= 0.4 + Math.abs(thisMove.yDistance) * 0.2;
                tags.add("hslimeblock");
            }
        }

        // If sneaking, the game moves the player by steps to check if they reach an edge to back them off.
        // From EntityHuman, maybeBackOffFromEdge
        // (Do this before the negligible speed threshold reset)
        // TODO: IMPLEMENT

        // Apply pushing speed
        // From Entity.java.push()
        if (ServerIsAtLeast1_9 && CollisionUtil.isCollidingWithEntities(player, true)) {
            for (Entity entity : player.getNearbyEntities(0.01, 0.0, 0.01)) {
                if (!entity.isValid() || entity.getType() == EntityType.BOAT 
                    || entity.getType() == EntityType.ARMOR_STAND) {
                    continue;
                    // There could be other/alive entities, don't break.
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
                    tags.add("hpush");
                    break;
                }
            }
        }

        // Modifiers to be applied on normal ground only.
        if (!from.isInLiquid()) {
            if (Magic.isBunnyhop(data, useBlockChangeTracker && from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick), sprinting, sneaking, fromOnGround, toOnGround)) {
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
            if (onGround) {
                // Ground speed modifier.
                thisMove.xAllowedDistance *= cc.survivalFlyWalkingSpeed / 100;
                thisMove.zAllowedDistance *= cc.survivalFlyWalkingSpeed / 100;
            }
        }
        else {
            // Apply liquid pushing speed.
            final Vector liquidFlowVector = from.getLiquidPushingVector(player, thisMove.xAllowedDistance, thisMove.zAllowedDistance);
            thisMove.xAllowedDistance += liquidFlowVector.getX();
            thisMove.zAllowedDistance += liquidFlowVector.getZ();
            // NCP liquid speed modifier.
            thisMove.xAllowedDistance *= cc.survivalFlySwimmingSpeed / 100;
            thisMove.zAllowedDistance *= cc.survivalFlySwimmingSpeed / 100;
        }     
        
        // Before calculating the acceleration, check if momentum is below the negligible speed threshold and cancel it.
        if (ServerIsAtLeast1_9) {
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
        
        // Finally, add the acceleration.
        // getInputVector, entity.java
        // This is the vanilla equation to set the player (new) horizontal speed (cast to a double because the client does it).
        double inputSq = MathUtil.square((double)strafe) + MathUtil.square((double)forward);
        if (inputSq >= 1.0E-7) {
            // Vec3d normalization
            if (inputSq > 1.0) {
                double distance = Math.sqrt(inputSq);
                if (distance < 1.0E-4) {
                    strafe = 0.0f;
                    forward = 0.0f;
                }
                else {
                    strafe /= distance;
                    forward /= distance;
                }
            }
            strafe *= movementSpeed;
            forward *= movementSpeed;
            float SinYaw = TrigUtil.sin(to.getYaw() * TrigUtil.toRadians);
            float CosYaw = TrigUtil.cos(to.getYaw() * TrigUtil.toRadians);
            thisMove.xAllowedDistance += strafe * (double)CosYaw - forward * (double)SinYaw;
            thisMove.zAllowedDistance += forward * (double)CosYaw + strafe * (double)SinYaw;
        } 

        // Stuck speed after update for accuracy's sake. 
        thisMove.xAllowedDistance *= (double) data.lastStuckInBlockHorizontal;
        thisMove.zAllowedDistance *= (double) data.lastStuckInBlockHorizontal;
   }


    /**
     * Core h-distance checks for media and all status
     * @return hAllowedDistance, hDistanceAboveLimit
     */
    private final double[] hDistChecks(final PlayerLocation from, final PlayerLocation to, final IPlayerData pData, final Player player, 
                                       final MovingData data, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingConfig cc,
                                       final boolean sprinting, boolean checkPermissions, final int tick, final boolean useBlockChangeTracker,
                                       final boolean fromOnGround, final boolean toOnGround, final boolean debug, final int multiMoveCount, 
                                       final boolean isModernSplitMove) {
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        final double minJumpGain = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier);
        final double maxJumpHeight = data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier);
        final PlayerMoveData pastMove2 = data.playerMoves.getSecondPastMove();
        final PlayerMoveData pastMove3 = data.playerMoves.getThirdPastMove();
        // NOTE: This is used by lowjump-post (!)
        final double jumpGainMargin = 0.005;
        final boolean sneaking = player.isSneaking() && reallySneaking.contains(player.getName());
        // TODO: New problem here, the onGround does not always reflect correct state with the client, cause fp 
        // Do we need to add lost-ground/false-on-ground cases for horizontal speed as well !?
        final boolean onGround = fromOnGround || thisMove.touchedGroundWorkaround;
        double hDistanceAboveLimit = 0.0;

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Determine if the bunnyhop delay should be reset or prolonged (bunnyfly). These checks need to be run before the estimation //                  
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // (NCP mechanic, not vanilla. We decide when players can bunnyhop because we use our own collision system for on ground judgement)
        if (data.bunnyhopDelay > 0) {
            // Do prolong bunnyfly if the player is yet to touch the ground
            if (data.bunnyhopDelay == 1 && !toOnGround && !to.isResetCond()) {
                data.bunnyhopDelay++;
                tags.add("bunnyfly(keep)");
                // NOTE: Later, feed the extra ticks to the Improbable to punish long-jumping.
            }
            else if (data.bunnyhopDelay <= 9) {
                // (10 represents a bunnyhop)
                tags.add("bunnyfly(" + data.bunnyhopDelay + ")");
            }
            
            final double speedAttributeMultiplier = attributeAccess.getHandle().getSpeedAttributeMultiplier(player) == Double.MAX_VALUE ? 1.0f : attributeAccess.getHandle().getSpeedAttributeMultiplier(player);
            // Catch obviously illegal hops, despite being detected by the estimation anyway. Might get removed.
            if (lastMove.hDistance > thisMove.hDistance) {
                final double deceleration = lastMove.hDistance - thisMove.hDistance;
                if (thisMove.hDistance < thisMove.hAllowedDistance
                    // 0.287 is the base sprinting speed taken from the previous hSpeed limit.
                    && data.bunnyhopDelay == 9 && deceleration < Magic.bunnySlopeSlowdown(data) * (lastMove.hDistance - (0.287 * speedAttributeMultiplier * data.lastBlockSpeedMultiplier * data.lastStuckInBlockHorizontal))
                    && !(thisMove.headObstructed || lastMove.headObstructed && lastMove.toIsValid && thisMove.yDistance > 0.0)) {
                    tags.add("bunnyslope");
                    // Not decelerating enough.
                    // Feed the lower deceleration to Improbable and request a check
                    Improbable.check(player, (float)(lastMove.hDistance - thisMove.hDistance), System.currentTimeMillis(), "moving.survivalfly.bunnyslope", pData);
                }
            }
            else {
                // Assume hDistRel to take care of illegal accelerations in-air
            }
            
            // Reset cases:
            // (Do note that this uses a much lower leniency margin, unlike thisMove#headObstructed, it only considers the nearest point of collision)
            if (from.isHeadObstructed(jumpGainMargin, false) && !data.sfLowJump) {
                // Reset the delay on head bump, because the player will end up back on ground sooner than usual.
                // Here (theoretically), a cheater could -after bumping head with a solid block- attempt to descend faster than legit to get a new bunnyhop boost. (thus increasing horizontal speed at faster rate than legit).
                // Catching irregular vertical motions is the job of vDistRel and VAcc however. 
                data.bunnyhopDelay = 0;
                if (debug) {
                    debug(player, "(hDistRel): Head collision detected. Reset bunnyfly (should expect a new bunnyhop soon after).");
                }
            }
            else if (from.isAboveStairs() && to.isNextToBlock(0.3, 0.1, BlockFlags.F_STAIRS) && !lastMove.bunnyHop
                    && !data.sfLowJump && thisMove.to.getY() > data.playerMoves.getPastMove(Magic.BUNNYHOP_MAX_DELAY - data.bunnyhopDelay).from.getY()) {
                // Only if the player is next to a flight of stairs and current altitude is higher than the bunnyhop altitude
                // NOTE: Margins are random. Perhaps the horizontal one (0.3) is a bit much
                data.bunnyhopDelay = 0;
                if (debug) {
                    debug(player, "(hDistRel): Reset bunnyfly after 1 tick due to the player being on stairs (double hop due to auto-jump).");
                }
            }
            else if (fromOnGround && !lastMove.bunnyHop && !data.sfLowJump) {
                if (from.isOnHoneyBlock() && data.bunnyhopDelay <= 4) {
                    data.bunnyhopDelay = 0;
                    if (debug) {
                        debug(player, "(hDistRel): Early reset of bunnyfly due to the player jumping on a honey block.");
                    }
                }
                else if (from.isInWeb() && data.bunnyhopDelay <= 7) {
                    data.bunnyhopDelay = 0;
                    if (debug) {
                        debug(player, "(hDistRel): Early reset of bunnyfly due to the player jumping in webs.");
                    }
                }
                else if (data.bunnyhopDelay <= 6 && !thisMove.headObstructed 
                        // The player actually jumped up a slope: this movement has a higher altitude than the bunnyhop move.
                        && thisMove.from.getY() > data.playerMoves.getPastMove(Magic.BUNNYHOP_MAX_DELAY - data.bunnyhopDelay).from.getY()) {
                    // (Ground check is checked above)
                    data.bunnyhopDelay = 0;
                    if (debug) {
                        debug(player, "(hDistRel): Early reset of bunnyfly due to the player jumping up a slope.");
                    }
                }
            }
        }


        //////////////////////////////////////////////////////////////
        // Estimate the horizontal speed (per-move distance check)  //                      
        //////////////////////////////////////////////////////////////
        if (multiMoveCount == 0 || isModernSplitMove) {
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
                estimateNextSpeed(player, acceleration, pData, sneaking, checkPermissions, tags, to, from, debug, fromOnGround, toOnGround, onGround, sprinting, lastMove, tick, useBlockChangeTracker);
            }
            else if (from.isInLava()) {
                data.nextInertia = Magic.LAVA_HORIZONTAL_INERTIA; 
                estimateNextSpeed(player, Magic.LIQUID_BASE_ACCELERATION, pData, sneaking, checkPermissions, tags, to, from, debug, fromOnGround, toOnGround, onGround, sprinting, lastMove, tick, useBlockChangeTracker);
            }
            else {
                data.nextInertia = onGround ? data.lastFrictionHorizontal * Magic.HORIZONTAL_INERTIA : Magic.HORIZONTAL_INERTIA;
                // 1.8 (and below) clients will use cubed inertia, not cubed friction here. The difference isn't significant except for blocking speed and bunnyhopping on soul sand, which are both slower on 1.8
                float acceleration = onGround ? data.walkSpeed * ((ServerIsAtLeast1_13 ? Magic.DEFAULT_FRICTION_CUBED : Magic.CUBED_INERTIA) / (data.lastFrictionHorizontal * data.lastFrictionHorizontal * data.lastFrictionHorizontal)) : Magic.AIR_ACCELERATION;
                if (sprinting) {//&& !sneaking) {
                    // NOTE: (Apparently players can now sprint while sneaking !?)
                    // (We don't use the attribute here due to desync issues, just detect when the player is sprinting and apply the multiplier manually)
                    // Multiplying by 1.3f seems to cause precision loss, so use the total multiply result.
                    acceleration += acceleration * 0.3f; // 0.3 is the effective sprinting speed (EntityLiving).
                    acceleration *= cc.survivalFlySprintingSpeed / 100;
                }
                estimateNextSpeed(player, acceleration, pData, sneaking, checkPermissions, tags, to, from, debug, fromOnGround, toOnGround, onGround, sprinting, lastMove, tick, useBlockChangeTracker);
            }
        }
        else {
            // Bukkit-based split move: predicting the next speed is not possible due to coordinates not being reported correctly by Bukkit (and without ProtocolLib, it's nearly impossible to achieve precision here)
            // Besides, no need to predict speed for a move that has been slowed down so much to the point of being considered micro..
            thisMove.xAllowedDistance = thisMove.to.getX() - thisMove.from.getX();
            thisMove.zAllowedDistance = thisMove.to.getZ() - thisMove.from.getZ();
            if (debug) {
                debug(player, "(hDistRel): Skip speed prediction on micro move (legacy).");
            }
        }
        
        // Set the estimated distance in this move.
        thisMove.hAllowedDistance = MathUtil.dist(thisMove.xAllowedDistance, thisMove.zAllowedDistance);

        // Global speed bypass modifier (can be combined with the other bypass modifiers. Only applies to hDist!)
        if (checkPermissions && pData.hasPermission(Permissions.MOVING_SURVIVALFLY_SPEEDING, player)) {
            thisMove.hAllowedDistance *= cc.survivalFlySpeedingSpeed / 100;
        }
        
        /** Expected difference from current to allowed */
        final double hDistDiffEx = thisMove.hDistance - thisMove.hAllowedDistance;
        if (debug) {
            // TODO: REMOVE 
            player.sendMessage("c/e: " + thisMove.hDistance + " / " + thisMove.hAllowedDistance);
        }
        if (hDistDiffEx <= 0.0) {
            // Speed is lower than estimated.
        }
        else if (hDistDiffEx < 0.000009) {
            // Accuracy margin
            // (Too ambitious? :) )
        }
        else {
            // At this point, a violation.
            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, hDistDiffEx);
            tags.add("hdistrel");
        }


        // (Auxiliary checks, these are rather aimed at catching specific cheat implementations or patterns of cheating.)
        //////////////////////////////////////////////////////
        // Check for no slow down cheats.                   //
        //////////////////////////////////////////////////////
        if (cData.isHackingRI && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))) {
            cData.isHackingRI = false;
            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
            Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
            tags.add("noslowpacket");
        }
        
        
        ////////////////////////////////////////////////////////////////////////
        // Prevent players from sprinting with blindness (vanilla mechanic)   // 
        ////////////////////////////////////////////////////////////////////////
        // TODO: Fix this check (receiving blindness while already sprinting allows to still sprint !)
        //   if (player.hasPotionEffect(PotionEffectType.BLINDNESS) && player.isSprinting() 
        //       && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))) {
        //       Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
        //       hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);     
        //       tags.add("blindsprint");
        //   }
        

        ///////////////////////////////////////////////////////////////////////
        // Checks for no gravity when falling through certain blocks.        //
        ///////////////////////////////////////////////////////////////////////
        // (Stuck speed blocks force players to always descend)
        if (thisMove.from.resetCond && lastMove.to.resetCond
            // Exclude liquids and climbables from this check.
            && !thisMove.from.inLiquid && !thisMove.to.inLiquid
            && !thisMove.from.onClimbable && !thisMove.to.onClimbable
            && thisMove.yDistance == 0.0 && lastMove.yDistance == 0.0
            && !fromOnGround && !toOnGround && lastMove.toIsValid && !from.isHeadObstructed() 
            && !to.isHeadObstructed()) {
            // (Assume micro-gravity changes to be catched by the respective methods)
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
                && !fromOnGround && !toOnGround
                && !from.isHeadObstructed() && !to.isHeadObstructed() 
                && !Bridge1_13.isSwimming(player)) {
                hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
                Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
                tags.add("liquidwalk");
            }
            // (Let vDistRel and vDistLiquid catch other waterwalk implementations that mess with vertical motion)
            // (Let hDistRel deal with going above water speed)
        }
        

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // The lowjump-post subcheck: after taking a height difference, ensure setback distance did decrease properly. //
        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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
        // Having said that, with NoCheatPlus' past-move-tracking system, we can attempt to judge ex-post if a player jumped lower than possible
        // TODO: Detect past lower jump with less hard-coded magic.
        if (cc.survivalFlyAccountingStep && !data.isVelocityJumpPhase() && data.hasSetBack()
            && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_STEP, player))) {
            
            // Ensure a (valid) past lift-off state can be found within the last 10 moves
            // NOTE: It strictly needs to monitor 10 moves max, else false positives will show up
            if (Magic.isValidLiftOffAvailable(10, data)) {
                
                // We have a past lift-off state. Verify if setback distances are too low 
                if (
                    // 0: Jumping lower than expected after keeping the same altitude for one event (or having very little decrease), after an initial legit motion
                    thisMove.setBackYDistance < data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                    && (
                        lastMove.setBackYDistance == thisMove.setBackYDistance 
                        || lastMove.setBackYDistance - pastMove2.setBackYDistance < minJumpGain / 1.7
                    )
                    && pastMove2.setBackYDistance > pastMove3.setBackYDistance && pastMove3.setBackYDistance <= minJumpGain + jumpGainMargin
                    && pastMove3.setBackYDistance >= minJumpGain - (Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN)
                    // 0: This move still moving up but with negative motion (yDirection change)
                    // (Not observed nor tested though. This is just an educated guess.)
                    || thisMove.setBackYDistance > 0.0 && lastMove.yDistance > 0.0 && thisMove.yDistance <= 0.0
                    && thisMove.setBackYDistance < data.liftOffEnvelope.getMinJumpHeight(data.jumpAmplifier)
                    // 0: First move having no distance.
                    || thisMove.setBackYDistance == 0.0 
                    && (
                        // 1: Jumping *exactly* 1 block up with fewer events than legit.
                        lastMove.setBackYDistance == 1.0 && thisMove.yDistance == 0.0 && lastMove.yDistance > 0.0
                        // 1: Keeping the same 1-block altitude with the last two moves
                        || lastMove.setBackYDistance == pastMove2.setBackYDistance
                        // 1:
                        || lastMove.setBackYDistance < data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                        && pastMove2.setBackYDistance > lastMove.setBackYDistance && pastMove2.setBackYDistance - lastMove.setBackYDistance < jumpGainMargin
                    )
                    || thisMove.setBackYDistance <= 0.0 && lastMove.setBackYDistance > 0.0
                    && thisMove.yDistance < 0.0 && lastMove.yDistance > 0.0) { // "Your grandma reads code" cit.
                    
                    // The player ended up on ground sooner than expected. 
                    if (!lastMove.from.onGround && lastMove.to.onGround && toOnGround) { // from.onGround is ignored on purpose.
                        // Mitigate the advantage by invalidating this hDistance
                        hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
                        // Feed the Improbable.
                        Improbable.feed(player, (float) thisMove.hDistance, System.currentTimeMillis());
                        tags.add("lowjump-post");
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
        final double minJumpGain = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier);
        final int maxJumpPhase = data.liftOffEnvelope.getMaxJumpPhase(data.jumpAmplifier);
        final double maxJumpHeight = data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier);
        final boolean strictVdistRel;


        // Note that this prediction does not strictly follow Minecraft's formula.
        // The actual friction formula would be (lastYDist - 0.08) * 0.98; but asofold decided to opt for (lastYDist * 0.98 - 0.0624/0.05).
        // As of now, we can't really tell the reason behind this choice.
        ///////////////////////////////////////////////////////////////////////////////////
        // Estimate the allowed relative yDistance (per-move distance check)             //
        ///////////////////////////////////////////////////////////////////////////////////
        if (thisMove.hasSlowfall && yDistance <= 0.0 && !resetFrom) { // <= 0.0 is what the game does.
            // Slowfalling simply reduces gravity
            vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.SLOW_FALL_GRAVITY;
            // Not sure what this is about, @Xaw3ep? :)
            if (!secondLastMove.toIsValid && vAllowedDistance < -0.035) {
               vAllowedDistance = -0.035;
            }
            strictVdistRel = true;
        }
        else if (!thisMove.hasSlowfall && lastMove.toIsValid && Magic.fallingEnvelope(yDistance, lastMove.yDistance, data.lastFrictionVertical, 0.0)) {
            // Test for falling earlier.
            vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_MIN;
            strictVdistRel = true;
        }
        // Moving off from something: ground/lost-ground/medium/past from-on-ground
        else if (resetFrom || thisMove.touchedGroundWorkaround) { // Isn't touchedGroundWorkaround already included in resetFrom!?
            
            // Moving onto ground: this considers only "thisMove.to.onGround" and past toOnGround states due to block change activity.
            if (toOnGround) {

                // Hack for boats (coarse: allows minecarts too): allow staying on the entity
                if (yDistance > cc.sfStepHeight && yDistance - cc.sfStepHeight < 0.00000003 
                    && to.isOnGroundDueToStandingOnAnEntity()) {
                    vAllowedDistance = yDistance;
                }
                else {
                    // This move is fully on ground (from->to) due to a lost ground case / block-change activity / coming from a differnt medium (i.e.: from webs to ground)
                    // Otherwise regular from-ground-to-ground cases would be handled by the "groundstep" wildcard.
                    vAllowedDistance = Math.max(cc.sfStepHeight, minJumpGain);
                    thisMove.canStep = true;
                    thisMove.canJump = true;
                }
                strictVdistRel = false;
            }
            // The player didn't land on ground (toOnGround returned false for both cases mentioned above).
            // Player might have jumped.
            else {
                // (Previously if (yDistance < 0.0 || yDistance > cc.sfStepHeight || tags.contains("lostground_couldstep")) -> enforce jumping motion, even if the player was actually preparing to descend)
                if (yDistance == 0.0 && thisMove.setBackYDistance == 0.0) {
                    // Stepping down a block, not actually jumping (this condition patches reverse step and the like)
                    // No distance because we get the absolute difference for vDistanceAboveLimit.
                    // Anything else would throw a false positive
                    vAllowedDistance = 0.0;
                    strictVdistRel = true;
                }
                else if (!tags.contains("lostground_couldstep")) {
                    // Otherwise, a jump.
                    // Allow jumping motion
                    vAllowedDistance = minJumpGain;
                    thisMove.canJump = true;
                    strictVdistRel = false;
                }
                else {
                    // Special case: lostground_couldstep was applied.
                    vAllowedDistance = yDistance;
                    strictVdistRel = false;
                }
            }
        }
        // The player is not in the falling envelope and hasn't moved off from anything.
        // They likely have just started to fall / have had a really small air time (not gained enough acceleration)
        else if (lastMove.toIsValid) {
        
            // This condition is probably meant for lenience:
            // (Corroborated by the range check of the last yDistance)
            if (MathUtil.inRange(-Math.max(Magic.GRAVITY_MAX / 2.0, 1.3 * Math.abs(yDistance)), lastMove.yDistance, 0.0)
                && (lastMove.touchedGround || lastMove.to.extraPropertiesValid && lastMove.to.resetCond)) {

                if (resetTo) {
                    // Player had a single air tick; the previous move was to/from ground and yDistance hasn't changed enough from the last one.
                    // Assume they've tried to somehow step.
                    // thisMove: --- -> resetTo
                    // lastMove: onGround -> onGround/resetCond 
                    //           OR 
                    //           onGround -> ---- 
                    //           OR 
                    //           --- -> onGround/resetCond
                    vAllowedDistance = cc.sfStepHeight;
                    thisMove.canStep = true;
                }
                else {
                    // Player had more than a single air tick, we can assume the player jumped (or at least, attempted to)
                    // thisMove: --- -> ---
                    // lastMove: " " 
                    vAllowedDistance = minJumpGain;
                    thisMove.canJump = true;
                } 
                strictVdistRel = false;
            }
            else {
                // Fully in-air at this point.
                // Enforce friction.
                vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_ODD; // Upper bound.
                strictVdistRel = true;
            }
        }
        // Last move doesn't have end-point coordinates (Teleport/Join/Respawn/Fly check switch/Other)
        else {
            tags.add(lastMove.valid ? "data_reset" : "data_missing");
            // Allow falling.
            if (MathUtil.between((-Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN), yDistance, 0.0)) {
                vAllowedDistance = yDistance; 
            }
            // Allow jumping.
            else if (fromOnGround || (lastMove.valid && lastMove.to.onGround)) {
                // TODO: Is (lastMove.valid && lastMove.to.onGround) safe?
                vAllowedDistance = minJumpGain;
                if (lastMove.to.onGround && vAllowedDistance < 0.1) {
                    vAllowedDistance = minJumpGain;
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
                // Because the vertical detection needs to prevent: too fast falling, too slow falling, too fast ascend and too slow ascend,
                // we need to throw a violation for the smallest deviation from our estimation (hence why we get the absolute difference here and the need for all the workarounds above)
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(yDistance - vAllowedDistance));
                tags.add("vdistrel");
            }
        }


        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Absolute vertical distance to setback: prevent players from jumping higher than maximum jump height.           //
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        if (!pData.hasPermission(Permissions.MOVING_SURVIVALFLY_STEP, player) && yDistance > 0.0 
            && !data.isVelocityJumpPhase() && data.hasSetBack()) {
            
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
                // ^: checking for jump height and not jump speed has the advantage of not needing as many workarounds as vDistRel, at the cost of the disadvantages explained in the lowjump-post check.
                // Also, jumping with less speed than estimated is already prevented by vDistRel (disregard abuse of workarounds), so adding a motion speed check would be redundant
                if (!data.sfLowJump && !data.sfNoLowJump && lastMove.toIsValid && lastMove.yDistance > 0.0
                    && !data.isVelocityJumpPhase()) {                    
                    /** Only count it if the player has actually been jumping (higher than setback). */
                    final double setBackYDistance = from.getY() - data.getSetBackY();
                    /** Estimation of minimal jump height */
                    final double minJumpHeight = data.liftOffEnvelope.getMinJumpHeight(data.jumpAmplifier);
                    if (setBackYDistance > 0.0 && setBackYDistance < minJumpHeight) {
                        
                        if (
                            // Head obstruction obviously allows to lowjump
                            from.isHeadObstructed(0.005, false) // Much less leniency
                            || yDistance <= 0.0 && lastMove.headObstructed && lastMove.yDistance >= 0.0
                            && !thisMove.headObstructed // Much higher leniency
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
                                // The flag stays true until landing back on ground or otherwise.
                                data.sfLowJump = true;
                                // This will cost quite a bit for the player :)
                                Improbable.feed(player, (float)(minJumpHeight - setBackYDistance), System.currentTimeMillis());
                            }
                        }
                    }
                } 
            }
        }
        

        // This check is just a left-over/remainder of the old y-axis handling (pre-vDistRel [Sept-2015]), basically outclassed by vDistRel.
        // Should get a revamp: track vertical speed VS fallen distance as per asofold's plans.
        // To fill in the gaps left by vDistRel's workarounds, it might still be useful to keep it as is. 
        // (i.e.: vDistRel has quite a lot of workarounds for head obstruction which could be abused (potentially) without this check still running)
        ///////////////////////////////////////////////////////////////////////////////
        // The Vertical Accounting subcheck: monitors gravity changes within 3 moves //
        //////////////////////////////////////////////////////////////////////////////
        if (InAirPhase && cc.survivalFlyAccountingV) {

            // Currently only for "air" phases.
            if (MovingUtil.honeyBlockSidewayCollision(from, to, data) 
                && thisMove.yDistance < 0.0 && thisMove.yDistance > -0.21) {
                data.clearAccounting();
                data.vDistAcc.add((float) -0.2033);
            }
            else if (ChangedYDir && lastMove.yDistance > 0.0) { // lastMove.toIsValid is checked above. 
                // Change to descending phase.
                data.clearAccounting();
                // Allow adding 0.
                data.vDistAcc.add((float) yDistance);
            }
            else if (yDistance <= 0.0 && lastMove.toIsValid && !lastMove.touchedGroundWorkaround
                && MathUtil.between(lastMove.yDistance, yDistance, 0.0) && data.sfJumpPhase > 1
                && MathUtil.inRange(0.0002, thisMove.hDistance, 1.5) && !to.isOnGround() && lastMove.yDistance < 0.0
                && thisMove.setBackYDistance < 0.0
                && (from.isOnGround(0.4, 0.2, 0) || to.isOnGround(0.4, Math.min(0.2, 0.01 + thisMove.hDistance), Math.min(0.1, 0.01 + -yDistance)))) {
                data.clearAccounting();
                data.vDistAcc.add((float) yDistance);
                // Clear if landing on the edge of blocks. Former lost ground case
            }
            else if (!thisMove.hasSlowfall && lastMove.hasSlowfall
                || thisMove.hasSlowfall && (!lastMove.hasSlowfall || !secondLastMove.hasSlowfall)) {
                data.clearAccounting();
                data.vDistAcc.add((float) yDistance);
                // Gravity transition, clear current data because it is definitely invalid now.
            }
            else if (thisMove.verVelUsed == null // Only skip if just used.
                    && !(lastMove.from.inLiquid && Math.abs(yDistance) < 0.31 || data.timeRiptiding + 1000 > now)) { 
                // Here yDistance can be negative and positive.
                data.vDistAcc.add((float) yDistance);
                final double accAboveLimit = verticalAccounting(data, lastMove, thisMove, data.vDistAcc, tags, "vacc" + (data.isVelocityJumpPhase() ? "dirty" : ""));
                if (accAboveLimit > vDistanceAboveLimit) {
                    if (data.getOrUseVerticalVelocity(yDistance) == null) {
                        vDistanceAboveLimit = accAboveLimit;
                    }
                }         
            }
            // Just to exclude source of error, might be redundant.
            else data.clearAccounting(); 
        } 
        
        
        // (Do this check last, because it's the least useful)
        ///////////////////////////////////////////////////////////////////////////////////////
        // Air-stay-time: prevent players from ascending further than the maximum jump phase.//
        ///////////////////////////////////////////////////////////////////////////////////////
        if (!VEnvHack && data.sfJumpPhase > maxJumpPhase && !data.isVelocityJumpPhase()) {

            if (yDistance < Magic.GRAVITY_MIN) {
                // Ignore falling, and let accounting deal with it.
                // (Use minimum gravity as a mean of leniency)
            }
            else if (resetFrom) {
                // Ignore bunny etc.
            }
            // Violation (Too high jumping or step).
            else if (data.getOrUseVerticalVelocity(yDistance) == null) {
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, data.sfJumpPhase - maxJumpPhase);
                tags.add("maxphase("+ data.sfJumpPhase +"/"+ maxJumpPhase + ")");
            }
        }


        // Add lowjump tag for the whole descending phase.
        if (data.sfLowJump) {
            tags.add("lowjump(" + StringUtil.fdec3.format((to.getY()-data.getSetBackY())) +"/"+ data.liftOffEnvelope.getMinJumpHeight(data.jumpAmplifier) + ")");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }
    

    /**
     * Old-style gravity check (before vDistRel, sept-2015). <br>
     * Demand that with time the values decrease by a minimum amount.<br>
     * The ActionAccumulator instance must have as many buckets as the number of checked moves (3, currently, which matches the GRAVITY_VACC value).<br>
     * Bucket 1 is checked against bucket 2, 0 is ignored. Applies to both falling and jumping<br>
     * NOTE: This just checks and adds to tags, no change to the accumulator.
     * 
     * @param yDistance
     * @param thisMove
     * @param acc
     * @param tags
     * @param tag Tag to be added in case of a violation of this sub-check.
     * @return A violation value > 0.001, to be interpreted like a moving violation.
     */
    private static final double verticalAccounting(final MovingData data, final PlayerMoveData lastMove, final PlayerMoveData thisMove,
                                                   final ActionAccumulator acc, final ArrayList<String> tags, 
                                                   final String tag) {
        final int count0 = acc.bucketCount(0);
        if (count0 > 0) {
            // 1st bucket has at least one accumulated event
            final int count1 = acc.bucketCount(1);
            if (count1 > 0) {
                // 2nd bucket has at least one accumulated event. We can proceed to calculate the score for the 1st bucket
                final int cap = acc.bucketCapacity();
                final float sc0;
                sc0 = (count0 == cap) ? acc.bucketScore(0) : acc.bucketScore(0) * (float)cap / (float)count0 - (!thisMove.hasSlowfall ? Magic.GRAVITY_VACC : Magic.GRAVITY_SLOW_FALL_VACC) * (float)(cap - count0);
                // For the 2nd bucket, we just get its score.
                final float sc1 = acc.bucketScore(1);
                if (sc0 > sc1 - 3.0 * (!thisMove.hasSlowfall ? Magic.GRAVITY_VACC : Magic.GRAVITY_SLOW_FALL_VACC)) {
                    // This gravity check can't really handle high falling speed that well (false positives).
                    // So instead of relying on the classic value accumulation method, try to predict the next speed.
                    // (Quite the redudancy with vDistRel. However the detection uses a non-vanilla friction formula, while this one follows MC's)
                    if (thisMove.yDistance <= -1.05 && sc1 < -8.0 && sc0 < -8.0
                        || thisMove.hasSlowfall && thisMove.yDistance <= -1.05 && sc1 < -1.0 && sc0 < -1.0
                        // Still use this estimation if velocity was recently used (leniency, less risk of false positives)
                        || lastMove.verVelUsed != null) {
                        tags.add(tag + "gravity");
                        double estimation = (lastMove.yDistance - (thisMove.hasSlowfall ? Magic.DEFAULT_SLOW_FALL_GRAVITY : Magic.DEFAULT_GRAVITY)) * data.lastFrictionVertical;
                        boolean violation = Math.abs(thisMove.yDistance - estimation) > 0.0001;
                        return violation ? Math.abs(thisMove.yDistance - estimation) : 0.0;
                    }
                    tags.add(tag);
                    return sc0 - sc1 - 3.0 * (!thisMove.hasSlowfall ? Magic.GRAVITY_VACC : Magic.GRAVITY_SLOW_FALL_VACC);
                }
            }
        }
        return 0.0;
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
     * @param sprinting
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
     * @param isModernSplitMove
     * @return hAllowedDistance, hDistanceAboveLimit, hFreedom
     */
    private double[] hDistAfterFailure(final Player player, final int multiMoveCount,
                                       final PlayerLocation from, final PlayerLocation to, 
                                       double hAllowedDistance, double hDistanceAboveLimit, final boolean sprinting, 
                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove, final boolean debug,
                                       final MovingData data, final MovingConfig cc, final IPlayerData pData, final int tick, 
                                       boolean useBlockChangeTracker, final boolean fromOnGround, final boolean toOnGround,
                                       final boolean isModernSplitMove) {
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        // 1: Attempt to release the item upon a NoSlow Violation, if set so in the configuration.
        if (cc.survivalFlyResetItem && hDistanceAboveLimit > 0.000009 && (cData.isUsingItem || player.isBlocking())) {
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
                double[] estimationRes = hDistChecks(from, to, pData, player, data, thisMove, lastMove, cc, sprinting, false, tick, useBlockChangeTracker, 
                                                     fromOnGround, toOnGround, debug, multiMoveCount, isModernSplitMove);
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
        // (Perhaps it's time to have an estimation-based check for liquids as well)
        data.sfNoLowJump = true;
        final double yDistAbs = Math.abs(yDistance);
        final double baseSpeed = thisMove.from.onGround ? Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player)) + 0.1 : Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player));
        /** Slow fall gravity is applied only if the player is not sneaking (in that case, the player will descend in water with regular gravity) */
        // TODO: Rough... Needs a better modeling.
        final boolean Slowfall = !(player.isSneaking() && reallySneaking.contains(player.getName())) && thisMove.hasSlowfall;
        
        ////////////////////////////
        // 0: Minimal speed.     //
        ///////////////////////////
        if (yDistAbs <= baseSpeed) {
            return new double[]{baseSpeed, 0.0};
        }
        
        
        /////////////////////////////////////////////////////////
        // 1: Vertical checking for waterlogged blocks 1.13+   //
        /////////////////////////////////////////////////////////
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
        // 3: Handle bubble columns 1.13+ // 
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
        data.sfNoLowJump = true;
        data.clearActiveHorVel(); // Might want to clear ALL horizontal vel.
        double vDistanceAboveLimit = 0.0;
        double yDistAbs = Math.abs(yDistance);
        final double maxJumpGain = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier) + 0.0001;
        /** Climbing a ladder in water and exiting water for whatever reason speeds up the player a lot in that one transition ... */
        boolean waterStep = lastMove.from.inLiquid && yDistAbs < Magic.swimBaseSpeedV(Bridge1_13.hasIsSwimming());
        // Quick, temporary fix for scaffolding block
        boolean scaffolding = from.isOnGround() && from.getBlockY() == Location.locToBlock(from.getY()) 
                              && yDistance > 0.0 && yDistance < maxJumpGain;
        double vAllowedDistance = (waterStep || scaffolding) ? yDistAbs : yDistance < 0.0 ? Magic.climbSpeedDescend : Magic.climbSpeedAscend;
        final double maxJumpHeight = LiftOffEnvelope.NORMAL.getMaxJumpHeight(0.0) + (data.jumpAmplifier > 0 ? (0.6 + data.jumpAmplifier - 1.0) : 0.0);
        
        // Climbables are a special case (ladders rather):
        // Jumping within the climbable's voxel won't immediately apply its motion: a player can jump with 0.42 motion if far away enough from the ladder, while still being in its voxel (NCP will still consider the player to be on climbable).
        // To solve this problem, we simply check if the player is on ground (excluding the climbable block itself, since NCP considers climbables as ground(most of them)) and enforce the ordinary jumping motion.
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
                if (yDistance > maxJumpGain) {
                    // If the player managed to exceed the ordinary jumping motion, we know for sure they're cheating.
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
     * In-web vertical distance checking (static falling speed).
     * 
     * @param player
     * @param thisMove
     * @param fromOnGround
     * @param toOnGround
     * @param data
     * @param cc
     * @param from
     * @param to
     * @param pData
     * @param isModernSplitMove
     * @param multiMoveCount
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistWeb(final Player player, final PlayerMoveData thisMove, final boolean fromOnGround,
                              final boolean toOnGround, final MovingData data, final MovingConfig cc, final PlayerLocation from, final PlayerLocation to,
                              final IPlayerData pData, final boolean isModernSplitMove, final int multiMoveCount) {
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
            from.collectBlockFlags(2.5); // Collect flags for the stream check below.
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
        else if (multiMoveCount == 0 || isModernSplitMove && multiMoveCount <= 8) { // The 9th part of the move event (from packet-loc to bukkit-loc) causes false positives.
            // Handle descending
            if (!thisMove.to.inWeb && lastMove.yDistance < 0.0) {
                // Falling from below.
                vAllowedDistance = -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN;
            }
            else if (!lastMove.from.inWeb && lastMove.yDistance < 0.0) {
                // Leniency for falling from high enough places (speed won't become static for a few ticks).
                vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - (thisMove.hasSlowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MIN);
            }
            else if (thisMove.hasSlowfall) {
                // The yOnGround is needed because the formula is too accurate (getting 0.0000001 violations otherwise)
                vAllowedDistance = -(Magic.DEFAULT_SLOW_FALL_GRAVITY * data.lastStuckInBlockVertical * data.lastFrictionVertical + cc.yOnGround);
            }
            // Ordinary.
            else vAllowedDistance = -Magic.GRAVITY_MAX * data.lastStuckInBlockVertical * data.lastFrictionVertical;
            // (We only care about players falling faster than legit, don't care about falling too slowly)
            vDistanceAboveLimit = yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0;
        }
        else {
            // Don't attempt to set a limit if this move wasn't split properly.
            // Let the client decide falling speed with bukkit-split moves.
            vAllowedDistance = yDistance;
            vDistanceAboveLimit = yDistance - vAllowedDistance;
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
     * Berry bush vertical distance checking (1.14+) (static falling speed)
     * 
     * @param player
     * @param from
     * @param to
     * @param toOnGround
     * @param hDistanceAboveLimit
     * @param yDistance
     * @param now
     * @param data
     * @param cc
     * @param isModernSplitMove
     * @param multiMoveCount
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistBush(final Player player, final PlayerMoveData thisMove, 
                               final boolean toOnGround, final long now, 
                               final MovingData data, final MovingConfig cc, final PlayerLocation from, final PlayerLocation to,
                               final boolean fromOnGround, final IPlayerData pData, final boolean isModernSplitMove, final int multiMoveCount) {
        final double yDistance = thisMove.yDistance;
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double jumpGainMargin = 0.00001;
        double vAllowedDistance, vDistanceAboveLimit;
        
        if (fromOnGround) {
            // Handle ground movement
            if (toOnGround) {
                // Allow stepping motion.
                vAllowedDistance = cc.sfStepHeight;
            }
            // Allow jumping motion
            else vAllowedDistance = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier, data.lastStuckInBlockVertical) + jumpGainMargin; 
            vDistanceAboveLimit = yDistance - vAllowedDistance;
        }
        else if (yDistance > 0.0) {
            // Handle ascending
            if (!lastMove.from.inBerryBush) {
                // For leniency: in the case players hop right into a bush while still ascending.
                vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_SPAN;
            }
            // Cannot ascend in a bush otherwise.
            else vAllowedDistance = 0.0;
            vDistanceAboveLimit = yDistance - vAllowedDistance;
        }
        else if (multiMoveCount == 0 || isModernSplitMove || multiMoveCount == 2) {
            // Handle descending (if slowfall is present only skip the first packet split move: from bukkit -> to packet)
            if (!thisMove.to.inBerryBush && lastMove.yDistance < 0.0) {
                // Exiting a bush from below, technically not possible because bushes need a dirt block to support them, just in case plugins get funny ideas.
                vAllowedDistance = -Magic.GRAVITY_MAX; // Arbitrary. Fuck it.
            }
            else if (!lastMove.from.inBerryBush && lastMove.yDistance < 0.0) {
                // Leniency for falling from a high enough place onto a bush
                vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - (thisMove.hasSlowfall ? Magic.SLOW_FALL_GRAVITY : Magic.GRAVITY_MAX);
            }
            else if (thisMove.hasSlowfall && (multiMoveCount > 1 || multiMoveCount == 0)) {
                // The yOnGround is needed because the formula is too accurate (getting 0.0000001 violations otherwise)
                vAllowedDistance = -(Magic.DEFAULT_SLOW_FALL_GRAVITY * data.lastStuckInBlockVertical * data.lastFrictionVertical + cc.yOnGround);
            }
            // Ordinary
            else vAllowedDistance = -Magic.GRAVITY_MAX * data.lastStuckInBlockVertical * data.lastFrictionVertical;
            // (We only care about players falling faster than legit, don't care about falling too slowly)
            vDistanceAboveLimit = yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0;
        }
        else {
            // Don't attempt to set a limit if this move wasn't split properly.
            // Do set a limit if we've reached the second phase of a split move however.
            vAllowedDistance = yDistance;
            vDistanceAboveLimit = yDistance - vAllowedDistance;
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
    * 
    * @param yDistance
    * @param form
    * @param to
    * @param cc
    * @param data
    * @param player
    * @param pData
    * @param now
    * @return vAllowedDistance, vDistanceAboveLimit
    * 
    */
    private double[] vDistPowderSnow(final double yDistance, final PlayerLocation from, final PlayerLocation to, 
                                     final MovingConfig cc, final MovingData data, final Player player, final IPlayerData pData,
                                     final long now) {
        double vAllowedDistance; 
        double vDistanceAboveLimit = 0.0;
        final double yDistToBlock = from.getY() - from.getBlockY();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData secondlastMove = data.playerMoves.getSecondPastMove();
   
        if (from.isOnGround(BlockFlags.F_POWDERSNOW)) {
            // Handle ground movement (this assumes the player to not be submerged in powder snow)
            if (to.isOnGround(BlockFlags.F_POWDERSNOW)) {
                // Stepping motion
                vAllowedDistance = cc.sfStepHeight;
            }
            // Allow jumping motion
            else vAllowedDistance = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier, data.lastStuckInBlockVertical);
            vDistanceAboveLimit = yDistance - vAllowedDistance;
            tags.add("snowjump");
        }
        else if (BridgeMisc.hasLeatherBootsOn(player)) {
            // If equipped with leather boots, allow climbing motion or jump motion
            if (thisMove.hasSlowfall 
                && yDistance > 0.0 && lastMove.yDistance < 0.0 && !thisMove.to.inPowderSnow 
                && (!lastMove.from.inPowderSnow || !secondlastMove.from.inPowderSnow) 
                && player.isSneaking() && reallySneaking.contains(player.getName())) {
                // Shift+Space bar on top of powder snow: yDistance inversion, from a negative to a positive amount. Appears only with slowfall. (-> -0.095 -> 0.279 (+0.374 gain, sometimes even more)
                vAllowedDistance = lastMove.yDistance * data.lastStuckInBlockVertical * data.lastFrictionVertical - 0.25;
            }
            else if (yDistance < 0.0 && lastMove.yDistance < 0.0 && !lastMove.from.inPowderSnow) {
                // Hopping onto a snow block and sinking in.
                vAllowedDistance = lastMove.yDistance * data.lastStuckInBlockVertical - Magic.GRAVITY_MIN - Magic.GRAVITY_VACC;
            }
            else if (thisMove.hasSlowfall && yDistance < 0.0 && lastMove.yDistance < 0.0) { 
                // Enforce slower falling speed.
                // (Both descending moves: when jumping the player keeps climbing descend speed on the first descending move 0.63 -> -0.118)
                vAllowedDistance = -(Magic.DEFAULT_SLOW_FALL_GRAVITY * data.lastStuckInBlockVertical * data.lastFrictionVertical + cc.yOnGround); 
            }
            // Climbing or jumping on powder snow while being submerged in it (cannot use the ground check because it would always return true,)
            else vAllowedDistance = yDistToBlock <= Magic.GRAVITY_VACC ? data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier) : (yDistance < 0.0 ? -Magic.snowClimbSpeedDescend : Magic.snowClimbSpeedAscend);
            vDistanceAboveLimit = Math.abs(yDistance) > Math.abs(vAllowedDistance) ? Math.abs(yDistance - vAllowedDistance) : 0.0;
            tags.add("bootson");
        }
        else {
            // Handle descend speed
            if (lastMove.yDistance > 0.0 && thisMove.yDistance > 0.0 && thisMove.to.inPowderSnow && lastMove.from.inPowderSnow) {
                // One cannot ascend in powder snow without boots
                // (== 0.0 is covered by nogravityblock)
                vAllowedDistance = 0.0;
            }
            else if (thisMove.yDistance < 0.0 && lastMove.yDistance < 0.0 && !lastMove.from.inPowderSnow) {
                // Hopping onto a snow block and sinking in.
                vAllowedDistance = lastMove.yDistance * data.lastStuckInBlockVertical * data.lastFrictionVertical - Magic.GRAVITY_MAX - Magic.GRAVITY_VACC;
            }
            else if (thisMove.hasSlowfall && thisMove.yDistance < 0.0 && lastMove.yDistance < 0.0) {
                // Enforce slower falling speed 
                vAllowedDistance = -(Magic.DEFAULT_SLOW_FALL_GRAVITY * data.lastStuckInBlockVertical * data.lastFrictionVertical + cc.yOnGround);
            }
            // Descend motion.
            else vAllowedDistance = -Magic.snowClimbSpeedDescend;
            vDistanceAboveLimit = Math.abs(yDistance) > Math.abs(vAllowedDistance) ? Math.abs(yDistance - vAllowedDistance) : 0.0;
            tags.add("bootsoff");
        }
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
     * Levitation can be easily abused by players, so we need to make a prediction-based check, like vDistRel. 
     * 
     * @param pData
     * @param player
     * @param data
     * @param from
     * @param to
     * @param cc
     * @param fromOnGround
     * @param multiMoveCount
     * @param isModernSplitMove
     * @return
     */
    private double[] vDistLevitation(final IPlayerData pData, final Player player, final MovingData data, final PlayerLocation from, 
                                     final PlayerLocation to, final MovingConfig cc, final boolean fromOnGround, final int multiMoveCount, 
                                     final boolean isModernSplitMove) {
        double vAllowedDistance = 0.0;
        double vDistanceAboveLimit = 0.0;
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData secondLastMove = data.playerMoves.getSecondPastMove();
        final double LevitationLevel = Bridge1_9.getLevitationAmplifier(player) + 1;
        final double estimationAccuracy = 0.000001;
        data.sfNoLowJump = true;
        // Just to be sure: clear the legacy gravity check's data when levitation is present.
        data.clearAccounting();
        
        ///////////////////////////////////////////////////////////
        // Estimate the allowed motion (per-move distance check) //
        ///////////////////////////////////////////////////////////
        // Minecraft bug: sbyte overflow with levitation level at or above 127
        if (LevitationLevel >= 126) {
            // Using /effect minecraft:levitation 255 negates all sort of gravitational force on the player
            // Levitation level over 127 = fall down at a fast or slow rate, depending on the value.
            vAllowedDistance = thisMove.yDistance; // Not ideal, but what can we do. We can't account for each and every vanilla bullshit... Let EXTREME_MOVE catch absurd distances and call it a day.
            vDistanceAboveLimit = 0.0;
            tags.add("sbyteof");
        }
        else {
            if (multiMoveCount == 0 || isModernSplitMove || multiMoveCount == 2) {
                // How minecraft calculates levitation speed.
                vAllowedDistance = (lastMove.yDistance + (0.05D * LevitationLevel - lastMove.yDistance) * 0.2D) * data.lastFrictionVertical * data.lastStuckInBlockVertical;
            }
            // Broken move.
            else vAllowedDistance = thisMove.yDistance;

            /** Expected difference from current to estimated */
            final double yDistDiffEx = thisMove.yDistance - vAllowedDistance;
            boolean vDistRelVL = false;
            // Compare distance to expected cases and throw a violation if no match is found.
            if (Math.abs(yDistDiffEx) < estimationAccuracy) {
                // Precision margin
            }
            else if (lastMove.toIsValid) {
                if (
                    // 0: The first move leaving ground is almost always mispredicted due to split moves
                    fromOnGround && !thisMove.to.onGround 
                    && thisMove.yDistance < data.liftOffEnvelope.getMinJumpGain(Bridge1_9.getLevitationAmplifier(player) - 1.0, data.lastStuckInBlockVertical) - Magic.GRAVITY_MIN
                    && thisMove.setBackYDistance == thisMove.yDistance
                    // 0: Can't ascend if head is obstructed
                    || (thisMove.headObstructed || lastMove.toIsValid && lastMove.headObstructed && lastMove.yDistance >= 0.0) 
                    && !from.isInPowderSnow()
                    // 0: For some reasons, the player speeds up less than expected on the first air move (0.047 actual VS 0.063 estimated, level 1)
                    || lastMove.touchedGround && data.sfJumpPhase == 1 && thisMove.yDistance > 0.0
                    && MathUtil.between(-Magic.GRAVITY_SPAN, yDistDiffEx, 0.0)
                    // 0: Webs cannot be predicted reliably. For now, simply allow ascending, without checking for speed.
                    || (from.isInWeb() || to.isInWeb()) && multiMoveCount > 0 && thisMove.yDistance > 0.0 && lastMove.yDistance > 0.0
                    // 0: Players can still press the space bar in powder snow to boost ascending speed.
                    || from.isInPowderSnow() && MathUtil.between(vAllowedDistance, thisMove.yDistance, vAllowedDistance + LiftOffEnvelope.POWDER_SNOW.getMaxJumpGain(0.0))
                    && thisMove.yDistance > 0.0 && lastMove.yDistance > 0.0 && BridgeMisc.hasLeatherBootsOn(player) && thisMove.yDistance - lastMove.yDistance == 0.0
                    // 0:
                    || (from.isResetCond() || to.isResetCond()) && thisMove.yDistance > 0.0 && lastMove.yDistance > 0.0
                    && thisMove.yDistance - lastMove.yDistance == 0.0 && MathUtil.between(0.0, Math.abs(yDistDiffEx), 0.1)
                    && !from.isOnClimbable()) {
                    // Several types of odd (but legit) moves.
                }
                else vDistRelVL = true;
            }
            else if (data.sfJumpPhase == 0 && secondLastMove.toIsValid
                && (yDistDiffEx < 0.0 || MathUtil.between(0.0, yDistDiffEx, Magic.GRAVITY_VACC))) {
                 // First move after setback, without this the player will be caught in a setback loop
            }
            else if (thisMove.to.hashCode() == data.lastSetBackHash && secondLastMove.toIsValid) {
                // Same case as above.
            }
            else vDistRelVL = true;
            
            // Unexpected move: violation
            if (vDistRelVL) {
                // Attempt to use velocity
                if (data.getOrUseVerticalVelocity(MovingUtil.getBaseV(0.0, thisMove.yDistance, 0.0f, 0.0, LevitationLevel, 0.0, false)) == null) {
                    vDistanceAboveLimit = Math.abs(thisMove.yDistance - vAllowedDistance);
                    tags.add("vdistrel(lev)");
                }
            }
        } 
        // (Add more auxiliary checks? i.e.: blatant attempt to descend or keep altitude. Already prevented, but still.)
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
        final double hDistDiffEx = thisMove.hDistance - thisMove.hAllowedDistance;
        final StringBuilder builder = new StringBuilder(500);
        builder.append(CheckUtils.getLogMessagePrefix(player, type));
        final String hVelUsed = hFreedom > 0 ? " / hVelUsed: " + StringUtil.fdec3.format(hFreedom) : "";
        builder.append("\nOnGround: " + (thisMove.headObstructed ? "(head obstr.) " : "") + (thisMove.touchedGroundWorkaround ? "(lost ground) " : "") + (fromOnGround ? "onground -> " : (resetFrom ? "resetcond -> " : "--- -> ")) + (toOnGround ? "onground" : (resetTo ? "resetcond" : "---")) + ", jumpPhase: " + data.sfJumpPhase + ", LiftOff: " + data.liftOffEnvelope.name() + "(" + data.insideMediumCount + ")");
        final String dHDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(hDistance, lastMove.hDistance) + ")" : "";
        final String dYDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(yDistance, lastMove.yDistance)+ ")" : "";
        final String frictionTick = ("keepFrictionTick= " + data.keepfrictiontick + " , ");
        builder.append("\n Tick counters: " + frictionTick);
        builder.append("\n" + " hDist: " + StringUtil.fdec3.format(hDistance) + dHDist + " / hDistDiffEx: " + hDistDiffEx + " / hAD: " + StringUtil.fdec3.format(hAllowedDistance) + hVelUsed +
                       "\n" + " vDist: " + StringUtil.fdec3.format(yDistance) + dYDist + " / yDistDiffEx: " + yDistDiffEx + " / vAD: " + StringUtil.fdec3.format(vAllowedDistance) + " , setBackY: " + (data.hasSetBack() ? (data.getSetBackY() + " (setBackYDist: " + StringUtil.fdec3.format(to.getY() - data.getSetBackY()) + " / MaxJumpHeight: " + data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier) + ")") : "?"));
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