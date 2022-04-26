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

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.magic.AirWorkarounds;
import fr.neatmonster.nocheatplus.checks.moving.magic.LiquidWorkarounds;
import fr.neatmonster.nocheatplus.checks.moving.magic.LostGround;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_17;
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
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.math.VanillaMath;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;

/**
 * The counterpart to the CreativeFly check. People that are not allowed to fly get checked by this. It will try to
 * identify when they are jumping, check if they aren't jumping too high or far, check if they aren't moving too fast on
 * normal ground, while sprinting, sneaking, swimming, etc.
 */
public class SurvivalFly extends Check {

 
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
        final boolean fromOnGround = thisMove.from.onGround;
        final boolean toOnGround = thisMove.to.onGround || useBlockChangeTracker && toOnGroundPastStates(from, to, thisMove, tick, data, cc);  // TODO: Work in the past ground stuff differently (thisMove, touchedGround?, from/to ...)
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
            final Location loc = player.getLocation(useLoc);
            data.adjustMediumProperties(loc, cc, player, thisMove);
            data.adjustLiftOffEnvelope(from);
            // Cleanup
            useLoc.setWorld(null);
        }

        // Determine if the player is actually sprinting.
        final boolean sprinting;
        if (data.lostSprintCount > 0) {
            // Sprint got toggled off, though the client is still (legitimately) moving at sprinting speed.
            // NOTE: This could extend the "sprinting grace" period, theoretically, until on ground.
            if (resetTo && (fromOnGround || from.isResetCond()) || hDistance <= Magic.WALK_SPEED) {
                // Invalidate.
                data.lostSprintCount = 0;
                tags.add("invalidate_lostsprint");
                if (now <= data.timeSprinting + cc.sprintingGrace) {
                    sprinting = true;
                }
                else sprinting = false;
            }
            else {
                tags.add("lostsprint");
                sprinting = true;
                if (data.lostSprintCount < 3 && toOnGround || to.isResetCond()) {
                    data.lostSprintCount = 0;
                }
                else data.lostSprintCount --;
            }
        }
        else if (now <= data.timeSprinting + cc.sprintingGrace) {
            // Within grace period for hunger level being too low for sprinting on server side (latency).
            if (now != data.timeSprinting) {
                tags.add("sprintgrace");
            }
            sprinting = true;
        }
        else sprinting = false;
        
        final Location loc = player.getLocation(useLoc);
        data.adjustMediumProperties(loc, cc, player, thisMove);
        useLoc.setWorld(null);



        ////////////////////////////////////
        // Mixed checks (lost ground)    ///
        ////////////////////////////////////
        // TODO: This isn't correct, needs redesign.
        // TODO: Quick addition. Reconsider entry points etc.
        final boolean resetFrom;
        if (fromOnGround || from.isResetCond()) {
            resetFrom = true;
        }
        else if (isSamePos) {

            if (useBlockChangeTracker && from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick)) {
                resetFrom = true;
                tags.add("pastground_from");
            }
            else if (lastMove.toIsValid) {
                // Note that to is not on ground either.
                resetFrom = LostGround.lostGroundStill(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags);
            }
            else resetFrom = false;
        }
        // TODO: More refined conditions possible ?
        // TODO: Consider if (!resetTo) ?
        // Check lost-ground workarounds.
        else resetFrom = LostGround.lostGround(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, useBlockChangeTracker ? blockChangeTracker : null, tags);

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

        // Alter some data before checking anything
        setHorVerDataExAnte(thisMove, from, to, data, yDistance, pData, player, cc, xDistance, zDistance); 




        /////////////////////////////////////
        // Horizontal move                ///
        /////////////////////////////////////
        bufferUse = true;
        double hAllowedDistance = 0.0, hDistanceAboveLimit = 0.0, hFreedom = 0.0;

        // Run through all hDistance checks if the player has actually some horizontal distance
        if (HasHorizontalDistance) {

            // Set the allowed distance and determine the distance above limit
            double hResult[] = hDistRel(from, to, pData, player, data, thisMove, lastMove, cc, sprinting, true, tick, useBlockChangeTracker);
            hAllowedDistance = hResult[0];
            hDistanceAboveLimit = hResult[1];
            // The player went beyond the allowed limit, execute the after failure checks.
            if (hDistanceAboveLimit > 0.0) {
                final double[] failureResult = hDistAfterFailure(player, from, to, hAllowedDistance, hDistanceAboveLimit, sprinting, thisMove, lastMove, data, cc, pData, tick, useBlockChangeTracker);
                hAllowedDistance = failureResult[0];
                hDistanceAboveLimit = failureResult[1];
                hFreedom = failureResult[2];
            }
            // Clear active velocity if the distance is within limit (clearly not needed. :))2
            else {
                data.clearActiveHorVel();
                hFreedom = 0.0;
                // Distance is within limit and the player is coming from ground after a not too recent bunnyhop, do reset the delay
                if (resetFrom && data.bunnyhopDelay <= 6) {
                    data.bunnyhopDelay = 0;
                }
            }
        }
        // No horizontal distance present
        else {
            // Prevent way too easy abuse by simply collecting queued entries while standing still with no-knockback on. (Experimental, likely too strict)
            if (cc.velocityStrictInvalidation && lastMove.hAllowedDistanceBase == 0.0 
                && data.hasQueuedHorVel()) {
                data.clearAllHorVel();
                hFreedom = 0.0;  
            }
            // Always clear active velocity, regardless of velocityStrictInvalidation.
            data.clearActiveHorVel();
            thisMove.hAllowedDistanceBase = 0.0;
            thisMove.hAllowedDistance = 0.0;
        }
        // Adjust some data after horizontal checking but before vertical
        data.setHorDataExPost();



        /////////////////////////////////////
        // Vertical move                  ///
        /////////////////////////////////////
        double vAllowedDistance = 0, vDistanceAboveLimit = 0;
        
        // Wild-card: allow step height from ground to ground, if not on/in a medium already.
        if (yDistance >= 0.0 && yDistance <= cc.sfStepHeight 
            && toOnGround && fromOnGround && !from.isResetCond()) {
            vAllowedDistance = cc.sfStepHeight;
            thisMove.allowstep = true;
            tags.add("groundstep");
        }
        // Powder snow (1.17+)
        else if (from.isInPowderSnow()) {
            final double[] resultSnow = vDistPowderSnow(yDistance, from, to, cc, data, player);
            vAllowedDistance = resultSnow[0];
            vDistanceAboveLimit = resultSnow[1];
        }
        // Climbable blocks
        // NOTE: this check needs to be before isInWeb, because this can lead to false positive
        else if (from.isOnClimbable()) {
            final double[] resultClimbable = vDistClimbable(player, from, to, fromOnGround, toOnGround, thisMove, lastMove, yDistance, data);
            vAllowedDistance = resultClimbable[0];
            vDistanceAboveLimit = resultClimbable[1];
        }
        // Webs 
        else if (from.isInWeb()) {
            final double[] resultWeb = vDistWeb(player, thisMove, toOnGround, hDistanceAboveLimit, now, data, cc, from);
            vAllowedDistance = resultWeb[0];
            vDistanceAboveLimit = resultWeb[1];
        }
        // Berry bush (1.15+)
        else if (from.isInBerryBush()){
            final double[] resultBush = vDistBush(player, thisMove, toOnGround, hDistanceAboveLimit, now, data, cc, from, fromOnGround);
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

            final Location vLoc = handleViolation(now, Double.isInfinite(result) ? 30.0 : result, player, from, to, data, cc);
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
        // 1: Adjust lift off envelope to medium
        // NOTE: isNextToGround(0.15, 0.4) allows a little much (yMargin), but reduces false positives.
        // Related commit: https://github.com/NoCheatPlus/NoCheatPlus/commit/c8ac66de2c94ac9f70f29c350054dd7896cd8646#diff-b8df089e2a4295e12695420f6066320d96119aa12c80e1c64efcb959f089db2d
        // TODO: Test with 0.2
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

        // 3: Apply reset conditions.
        if (resetTo) {
            // The player has moved onto ground.
            if (toOnGround) {
                // Moving onto ground but still ascending (jumping next to a block).
                if (yDistance > 0.0 && to.getY() > data.getSetBackY() + 0.13 // 0.15 ?
                    && !from.isResetCond() && !to.isResetCond()) { 
                    // Schedule a no low jump flag, because this low descending phase is legit
                    data.sfNoLowJump = true;
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
            if (to.getY() < 0.0 && cc.sfSetBackPolicyVoid) {
                data.setSetBack(to);
            }
        }
        
        // 4: Adjust in-air counters.
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

        // 5: Horizontal velocity invalidation.
        if (hDistance <= (cc.velocityStrictInvalidation ? thisMove.hAllowedDistanceBase : thisMove.hAllowedDistanceBase / 2.0)) {
            data.clearActiveHorVel();
        }

        // 6: Update unused velocity tracking.
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
      
        // 7: Adjust various speed/friction factors (both h/v).
        data.lastFrictionHorizontal = data.nextFrictionHorizontal;
        data.lastFrictionVertical = data.nextFrictionVertical;
        data.lastStuckInBlockHorizontal = data.nextStuckInBlockHorizontal;  
        data.lastBlockSpeedHorizontal = data.nextBlockSpeedHorizontal;

        // 8: Log tags added after violation handling.
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
    * Check for toOnGround past states
    * 
    * @param from
    * @param to
    * @param thisMove
    * @param tick
    * @param data
    * @param cc
    * @return
    */
    private boolean toOnGroundPastStates(final PlayerLocation from, final PlayerLocation to, 
                                         final PlayerMoveData thisMove, int tick, 
                                         final MovingData data, final MovingConfig cc) {
        
        // TODO: Heuristics / more / which? (too short move, typical step up moves, typical levels, ...)
        if (to.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick)) {
            tags.add("pastground_to");
            return true;
        }
        else {
            return false;
        }
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
                                     final IPlayerData pData, final Player player, final MovingConfig cc, final double xDistance, final double zDistance) {
        
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
        // Set flag for swimming with the flowing direction of liquid.
        thisMove.downStream = from.isDownStream(xDistance, zDistance); 
        // Set flag for swimming in a waterfall
        thisMove.inWaterfall = from.isWaterfall(yDistance);
        // Check if head is obstructed.
        thisMove.headObstructed = (yDistance > 0.0 ? from.isHeadObstructed(yDistance) : from.isHeadObstructed());
        // Get the distance to set-back.
        thisMove.setBackYDistance = to.getY() - data.getSetBackY();
        
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
                        //                        // TODO: Push of box off-center has the same effect.
                        //                        final BlockChangeEntry entry = blockChangeTracker.getBlockChangeEntryMatchFlags(data.blockChangeRef, 
                        //                                tick, from.getWorld().getUID(), from.getBlockX(), from.getBlockY() - 1, from.getBlockZ(),
                        //                                Direction.Y_POS, BlockFlags.F_BOUNCE25);
                        //                        if (entry != null) {
                        //                            data.blockChangeRef.updateSpan(entry);
                        //                            data.prependVerticalVelocity(new SimpleEntry(tick, 0.5015, 3)); // TODO: HACK
                        //                            tags.add("past_bounce");
                        //                        }
                        //                        else 
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
            // (No else.)
            //            if (yDistance <= 1.55) {
            //                // TODO: Edges ca. 0.5 (or 2x 0.5).
            //                // TODO: Center ca. 1.5. With falling height, values increase slightly.
            //                // Simplified: Always allow 1.5 or less with being pushed up by slime.
            //                // TODO: 
            //                if (from.matchBlockChangeMatchResultingFlags(
            //                        blockChangeTracker, data.blockChangeRef, Direction.Y_POS, 
            //                        Math.min(yDistance, 0.415), // Special limit.
            //                        BlockFlags.F_BOUNCE25)) {
            //                    tags.add("blkmv_y_pos_bounce");
            //                    final double maxDistYPos = yDistance; //1.0 - (from.getY() - from.getBlockY()); // TODO: Margin ?
            //                    // TODO: Set bounce effect or something !?
            //                    // TODO: Bounce effect instead ?
            //                    data.addVerticalVelocity(new SimpleEntry(tick, Math.max(0.515, yDistance - 0.5), 2));
            //                    return new double[]{maxDistYPos, 0.0};
            //                }
            //            }
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
     * Core h-distance checks for all kinds of media and status
     * @return hAllowedDistance, hDistanceAboveLimit
     */
    private final double[] hDistRel(final PlayerLocation from, final PlayerLocation to, final IPlayerData pData, final Player player, 
                                    final MovingData data, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingConfig cc,
                                    final boolean sprinting, boolean checkPermissions, final int tick, final boolean useBlockChangeTracker) {

        /** Predicted speed (x) */
        double xDistance = 0.0;
        /** Predicted speed (z) */ 
        double zDistance = 0.0;
        /** Relative horizontal distance VL*/
        boolean hDistRelVL = false;
        double hAllowedDistance = 0.0;
        double hDistanceAboveLimit = 0.0;
        /** Always set to true unless the player is in bunnyfly phase */
        boolean allowHop = true;
        final double minJumpGain = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier);
        final boolean headObstructed = thisMove.headObstructed || lastMove.toIsValid && lastMove.headObstructed;
        final PlayerMoveData pastMove2 = data.playerMoves.getSecondPastMove();
        final PlayerMoveData pastMove3 = data.playerMoves.getThirdPastMove();
        final PlayerMoveData pastMove4 = data.playerMoves.getPastMove(3);
        final double jumpGainMargin = 0.005;
        boolean sneaking = player.isSneaking() && reallySneaking.contains(player.getName());
        /** Strafe/forward/backwards movement factors */
        double[] dirFactor = MovingUtil.getMovementDirectionFactors(player, pData, left, right, forward, backwards, sneaking, checkPermissions, data);
        /** Takes into account lost ground cases and past on ground states due to block activity */
        final boolean onGround = thisMove.touchedGround || (useBlockChangeTracker && (toOnGroundPastStates(from, to, thisMove, tick, data, cc) || from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick)));
        /** Past on ground location. Checked last, assuming regular isOnGround has returned false and no lost ground case applies. */
        final boolean fromPastOnGround = useBlockChangeTracker && from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick);
        /** The movement_speed attribute of the player. Includes all effects except sprinting (!) */
        double playerSpeedAttribute = attributeAccess.getHandle().getMovementSpeed(player); // TODO: IMPLEMENT (!)
        /** The sprinting multiplier */
        double sprintingAttrMod = attributeAccess.getHandle().getSprintAttributeMultiplier(player);


        //////////////////////////////////////////////////
        // After hop checks (bunnfly)                   //
        //////////////////////////////////////////////////
        // Check if this bunnyfly phase is to be prolonged or reset earlier.
        if (data.bunnyhopDelay > 0) {
            allowHop = false;

            // Do prolong bunnyfly if the player is yet to touch the ground
            if (data.bunnyhopDelay == 1 && !thisMove.to.onGround && !to.isResetCond()) {
                data.bunnyhopDelay++;
                tags.add("bunnyfly(keep)");
            }
            else tags.add("bunnyfly(" + data.bunnyhopDelay + ")");
            
            // Do allow earlier hopping, provided the player is not lowjumping
            if (!allowHop && !data.sfLowJump) {
            	// 0: With slopes
                // Introduced with commit: https://github.com/Updated-NoCheatPlus/NoCheatPlus/commit/2ee891a427a047010f7358a7b246dd740398fa12
                if (data.bunnyhopDelay <= 6 && (thisMove.from.onGround || thisMove.touchedGroundWorkaround || fromPastOnGround)) {
                    tags.add("ediblebunny");
                    allowHop = true;  
                }
                // 0: With head obstructed.
                else if (
                        // 1: The usual case: fully air move -> air-ground -> ground-air/ground (allow hop).
                        Magic.inAir(pastMove2) && !lastMove.from.onGround 
                        && lastMove.to.onGround && thisMove.from.onGround 
                        && !lastMove.bunnyHop && headObstructed
                        // 1: Sliding on ice: air-ground -> ground-ground -> ground-air
                        || Magic.touchedIce(pastMove2) && pastMove2.to.onGround && lastMove.from.onGround 
                        && lastMove.to.onGround && thisMove.from.onGround && !thisMove.to.onGround
                        && !lastMove.bunnyHop && headObstructed
                        // 1: Double head bang bunny
                        || !pastMove2.bunnyHop && lastMove.bunnyHop && headObstructed && data.bunnyhopDelay == 9 && thisMove.from.onGround) {
                    tags.add("headbangbunny");
                    allowHop = true;
                }
               //TODO: Uhm... Double bunny I guess?
            }
        }


        //////////////////////////////////////////////////////////
        // Estimate horizontal speed (per-move distance check)  //                      
        //////////////////////////////////////////////////////////
        if (!from.isInWater() && !to.isInWater()) {
            
            // Normal move 
            if (!from.isInLava() && !to.isInLava()) {
                
                /** How much speed should be conserved on the next tick */
                double inertia = onGround ? data.lastFrictionHorizontal * Magic.HORIZONTAL_INERTIA : Magic.HORIZONTAL_INERTIA;       
                /** Calculate acceleration after inertia is updated. */
                // NOTE: MC 1.8 reports 0.16277136, 0.21600002D is from 1.18.2 from LivingEntity.getFrictionInfluencedSpeed() 
                // Not sure which one we should use
                // NOTE: MC 1.8 seems to be using *inertia* here (multiplied by 0.91), not block friction, unlike in 1.18
                double acceleration = 0.21600002D / Math.pow(data.lastFrictionHorizontal, 3);
                /** The MOVEMENT_SPEED attribute factor: base value 0.1 can be found in Abilities.java (walkingspeed); affected by effects and sprinting, which is included here. */
                double movementSpeedFactor;

                // Ground  
                if (onGround) {
                    tags.add("groundspeed");
                    movementSpeedFactor = playerSpeedAttribute * acceleration; 
                     
                    // Bunnyhop (aka: sprint-jump) detection.
                    // (Should note that client code seems to apply this boost only with ordinary vertical motion (0.42)
                    // but it will still apply on (many) other occasions. Can't seem to find the references in the code as of now)
                    if (allowHop && !data.sfLowJump && sprinting && lastMove.toIsValid) {
                        // 0: Y-distance envelope.
                        if (
                            // 1: Normal jumping. Demand the player to hit the jumping envelope.
                            thisMove.yDistance > minJumpGain - Magic.GRAVITY_SPAN
                            // 1: Too short with head obstructed.
                            || headObstructed
                            // 1: Bunnyhop after jumping up 1 block
                            || Magic.jumpedUpSlope(data, from, 9) && !lastMove.bunnyHop
                            && thisMove.yDistance > minJumpGain - Magic.GRAVITY_MAX - cc.yOnGround
                            && !from.isResetCond() && !to.isResetCond()
                            // 0: Ground + jump phase conditions.
                            && (
                                // 1: Ordinary/obvious lift-off.
                                data.sfJumpPhase == 0 && thisMove.from.onGround 
                                // 1: For lenience: the player somehow touched the ground with this (lostground) or last move.
                                || data.sfJumpPhase <= 1 && ((thisMove.touchedGroundWorkaround || fromPastOnGround) && !lastMove.bunnyHop) 
                            )) {
                            // Adds 0.2 towards the player's current facing
                            float facing = (float) (from.getYaw() * 0.017453292);
                            xDistance -= (double) (VanillaMath.sin(facing) * Magic.BUNNYHOP_ACCEL_BOOST); 
                            zDistance += (double) (VanillaMath.cos(facing) * Magic.BUNNYHOP_ACCEL_BOOST); 
                            // This prevents abuse of the speed boost.
                            data.bunnyhopDelay = Magic.BUNNYHOP_MAX_DELAY; 
                            thisMove.bunnyHop = true;
                            tags.add("bunnyhop");
                        }
                    }
                }
                // Air 
                else {
                    tags.add("airspeed");
                    movementSpeedFactor = Magic.AIR_MOVEMENT_SPEED_ATTRIBUTE; 
                }
                
                // Count in sprinting.
                if (sprinting) {
                    movementSpeedFactor *= sprintingAttrMod;
                }
                
                // Calculate and apply the new speed
                MovingUtil.updateHorizontalSpeed(dirFactor[0], dirFactor[1], movementSpeedFactor, xDistance, zDistance, from);
                if (from.isOnClimbable() || to.isOnClimbable()) {
                    xDistance = MathUtil.clampDouble(xDistance, -Magic.CLIMBABLE_MAX_HORIZONTAL_SPEED, Magic.CLIMBABLE_MAX_HORIZONTAL_SPEED);
                    zDistance = MathUtil.clampDouble(zDistance, -Magic.CLIMBABLE_MAX_HORIZONTAL_SPEED, Magic.CLIMBABLE_MAX_HORIZONTAL_SPEED);
                }
                MovingUtil.applyModifiersToUpdatedSpeed(player, pData, from, data, to, sneaking, xDistance, zDistance, onGround, tags, checkPermissions, mcAccess.getHandle().getWidth(player)); // (Client code here moves the player)
                // Apply friction to simulate drag
                xDistance *= inertia;
                zDistance *= inertia;
                // Account for NCP base speed modifiers. 
                xDistance *= cc.survivalFlyWalkingSpeed / 100D;
                zDistance *= cc.survivalFlyWalkingSpeed / 100D;
            }
            // Handle lava movement
            else {
                tags.add("lavaspeed");
                // Calculate and apply the new speed
                MovingUtil.updateHorizontalSpeed(dirFactor[0], dirFactor[1], Magic.LIQUID_BASE_ACCELERATION, xDistance, zDistance, from); 
                MovingUtil.applyModifiersToUpdatedSpeed(player, pData, from, data, to, sneaking, xDistance, zDistance, onGround, tags, checkPermissions, mcAccess.getHandle().getWidth(player));
                // Apply friction to the new speed to simulate drag 
                xDistance *= Magic.LAVA_HORIZONTAL_INERTIA;
                zDistance *= Magic.LAVA_HORIZONTAL_INERTIA; 
                // Account for NCP base speed modifiers.
                xDistance *= cc.survivalFlySwimmingSpeed / 100D;
                zDistance *= cc.survivalFlySwimmingSpeed / 100D;
            }
        }
        // Handle water movement
        else {
            tags.add("waterspeed");
            // Apparently the game uses isSprinting to tell if the player is swimming and simply reduces friction.
            double waterInertia = (Bridge1_13.isSwimming(player) || sprinting) ? Magic.HORIZONTAL_SWIMMING_INERTIA : Magic.WATER_HORIZONTAL_INERTIA;
            double acceleration = Magic.LIQUID_BASE_ACCELERATION;
            final double StriderLevel = BridgeEnchant.getDepthStriderLevel(player) * (!onGround ? Magic.STRIDER_OFF_GROUND_PENALTY_MULTIPLIER : 1.0); // NCP already caps Strider to 3
            
            if (StriderLevel > 0.0) {
                waterInertia += (0.54600006D - waterInertia) * StriderLevel / 3.0;
                acceleration += (playerSpeedAttribute * 1.0 - acceleration) * StriderLevel / 3.0; 
            }
            
            // Should note that this is the only reference about dolphin's grace in the code that I could find.
            if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
                waterInertia = Magic.DOLPHIN_GRACE_INERTIA; 
            }

            // Calculate and apply the new speed
            MovingUtil.updateHorizontalSpeed(dirFactor[0], dirFactor[1], acceleration, xDistance, zDistance, from);
            MovingUtil.applyModifiersToUpdatedSpeed(player, pData, from, data, to, sneaking, xDistance, zDistance, onGround, tags, checkPermissions, mcAccess.getHandle().getWidth(player));
            // Apply friction to the new speed to simulate drag
            xDistance *= waterInertia; 
            zDistance *= waterInertia;
            // Account for NCP base speed modifiers.
            xDistance *= cc.survivalFlySwimmingSpeed / 100D;
            zDistance *= cc.survivalFlySwimmingSpeed / 100D;
        }
        
        // Generic speed bypass
        if (checkPermissions && pData.hasPermission(Permissions.MOVING_SURVIVALFLY_SPEEDING, player)) {
            xDistance *= cc.survivalFlySpeedingSpeed / 100D;
            zDistance *= cc.survivalFlySpeedingSpeed / 100D;
        }

        // Set the estimated speed.
        thisMove.hAllowedDistance = hAllowedDistance = MathUtil.dist(xDistance, zDistance);

        // Compare estimated distance with workarounds 
        //if (HorizontalWorkarounds.some_kind_of_medium_transition_that_we_likely_need_to_handle()) {
        //    // Several types of odd accelerations
        //    bufferUse = false; // Ensure the buffer doesn't get consumed for allowed moves.
        //}
        // Nothing found, throw a violation
        //else hDistRelVL = true;
        
        if (hDistRelVL) {
            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance - hAllowedDistance);
            tags.add("hdistrel");
        }


        //////////////////////////////////////////////////////
        // Check for no slow down cheats.                   //
        //////////////////////////////////////////////////////
        if (data.isHackingRI && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))) {
            data.isHackingRI = false;
            bufferUse = false;
            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
            tags.add("noslowpacket");
        }


        /////////////////////////////////////////////////////////
        // Checks for micro y deltas when moving above liquid. //
        /////////////////////////////////////////////////////////
        if ((!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_WATERWALK, player))) {

            final Material blockUnder = from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - 0.3), from.getBlockZ());
            final Material blockAbove = from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() + 0.1), from.getBlockZ());
            if (blockUnder != null && BlockProperties.isLiquid(blockUnder) && BlockProperties.isAir(blockAbove)) {
            
                if (hDistanceAboveLimit <= 0.0 
                    && thisMove.hDistance > 0.11 && thisMove.yDistance <= LiftOffEnvelope.LIMIT_LIQUID.getMaxJumpGain(0.0) 
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
            if (hDistanceAboveLimit <= 0.0 && thisMove.yDistance == 0.0 && lastMove.yDistance == 0.0 && lastMove.toIsValid
                && thisMove.hDistance > 0.090 && lastMove.hDistance > 0.090 // Do not check lower speeds. The cheat would be purely cosmetic at that point, it wouldn't offer any advantage.
                && BlockProperties.isLiquid(to.getTypeId()) 
                && BlockProperties.isLiquid(from.getTypeId())
                && !onGround
                && !from.isHeadObstructed() && !to.isHeadObstructed() 
                && !Bridge1_13.isSwimming(player)) {
                hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
                bufferUse = false;
                tags.add("liquidwalk");
            }
        }
        

        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // The vDistSBLow subcheck: after taking a height difference, ensure setback distance did decrease properly. //
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // (Taken out of vDistAir because this checks ex-post, once the player is on ground. Also the reason why we invalidate hDistance and not yDistance, despite it being tied to vertical checking, rather)
        if (cc.survivalFlyAccountingStep && !data.isVelocityJumpPhase() && data.hasSetBack() && !(thisMove.from.aboveStairs || lastMove.from.aboveStairs)
            && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_STEP, player))) {

            // Monitor setback distance between this and the last 9 moves.
            if (Magic.getPastLiftOffAvailable(10, data) && data.liftOffEnvelope == LiftOffEnvelope.NORMAL) { // Currently, only for normal env.
                
                if (
                    // 0: The usual case
                    thisMove.setBackYDistance < data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                    && (
                        lastMove.setBackYDistance == thisMove.setBackYDistance 
                        || lastMove.setBackYDistance - pastMove2.setBackYDistance < minJumpGain / 1.7
                    )
                    && pastMove2.setBackYDistance > pastMove3.setBackYDistance && pastMove3.setBackYDistance <= minJumpGain + jumpGainMargin
                    && pastMove3.setBackYDistance >= minJumpGain - (Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN)
                    // 0: Too little dropoff
                    || thisMove.setBackYDistance == 0.0 && lastMove.setBackYDistance < data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                    && pastMove2.setBackYDistance > lastMove.setBackYDistance && pastMove2.setBackYDistance - lastMove.setBackYDistance < jumpGainMargin
                    // 0: Sharp distance dropoff
                    // (Not observed nor tested though. This is just an educated guess.)
                    || thisMove.setBackYDistance > 0.0 && lastMove.yDistance > 0.0 && thisMove.yDistance <= 0.0
                    && thisMove.setBackYDistance < data.liftOffEnvelope.getMinJumpHeight(data.jumpAmplifier)) {
                    
                    // If the player has been on ground for 1 event and setback distance didn't decrease properly, flag. (Assume everything else gets caught by vDistRel and vDistSB)
                    if (!lastMove.from.onGround && lastMove.to.onGround && thisMove.to.onGround) { // from.onGround is ignored on purpose.
                        if (data.getOrUseVerticalVelocity(thisMove.yDistance) == null) {
                            // TODO: Redesign setback handling: allow queuing setbacks and use past setback locations, instead of invaliding hDistance.
                            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, thisMove.hDistance);
                            bufferUse = false;
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
     * Core y-distance checks for in-air movement (may include air -> other).
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

        double vAllowedDistance          = 0.0;
        double vDistanceAboveLimit       = 0.0;
        final PlayerMoveData thisMove    = data.playerMoves.getCurrentMove();
        final double yDistChange         = lastMove.toIsValid ? yDistance - lastMove.yDistance : Double.MAX_VALUE; // Change seen from last yDistance.
        final double maxJumpGain         = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);
        final int maxJumpPhase           = data.liftOffEnvelope.getMaxJumpPhase(data.jumpAmplifier);
        final double jumpGainMargin      = 0.005; // TODO: Model differently, workarounds where needed. 0.05 interferes with max height vs. velocity (<= 0.47 gain).
        final boolean strictVdistRel;


        ///////////////////////////////////////////////////////////////////////////////////
        // Estimate the allowed relative yDistance (per-move distance check)             //
        ///////////////////////////////////////////////////////////////////////////////////
        // Less headache: Always allow falling. 
        if (lastMove.toIsValid && Magic.fallingEnvelope(yDistance, lastMove.yDistance, data.lastFrictionVertical, 0.0)) {
            vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_MIN;
            strictVdistRel = true;
        }
        else if (resetFrom || thisMove.touchedGroundWorkaround) {

            // TODO: More concise conditions? Some workaround may allow more.
            if (toOnGround) {

                // Hack for boats (coarse: allows minecarts too): allow staying on the entity
                if (yDistance > cc.sfStepHeight && yDistance - cc.sfStepHeight < 0.00000003 
                    && to.isOnGroundDueToStandingOnAnEntity()) {
                    vAllowedDistance = yDistance;
                }
                else {
                    vAllowedDistance = Math.max(cc.sfStepHeight, maxJumpGain + jumpGainMargin);
                    thisMove.allowstep = true;
                    thisMove.allowjump = true;
                }
            }
            else {

                // Code duplication with the absolute limit below.
                if (yDistance < 0.0 || yDistance > cc.sfStepHeight || !tags.contains("lostground_couldstep")) {
                    vAllowedDistance = maxJumpGain + jumpGainMargin;
                    thisMove.allowjump = true;
                }
                // Lostground_couldstep was applied.
                else vAllowedDistance = yDistance;
            }
            strictVdistRel = false;
        }
        else if (lastMove.toIsValid) {

            if (lastMove.yDistance >= -Math.max(Magic.GRAVITY_MAX / 2.0, 1.3 * Math.abs(yDistance)) 
                && lastMove.yDistance <= 0.0 
                && (lastMove.touchedGround || lastMove.to.extraPropertiesValid && lastMove.to.resetCond)) {

                if (resetTo) {
                    vAllowedDistance = cc.sfStepHeight;
                    thisMove.allowstep = true;
                }
                else {
                    vAllowedDistance = maxJumpGain + jumpGainMargin;
                    thisMove.allowjump = true;
                } // TODO: Needs more precise confinement + setting set back or distance to ground or estYDist.
                strictVdistRel = false;
            }
            else {
                // Friction.
                vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_ODD; // Upper bound.
                strictVdistRel = true;
            }
        }
        // Teleport/join/respawn.
        else {
            tags.add(lastMove.valid ? "data_reset" : "data_missing");
            
            // Allow falling.
            if (thisMove.yDistance > -(Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN) && yDistance < 0.0) {
                vAllowedDistance = yDistance; 
                // vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_MIN;
            }
            // Allow jumping.
            else if (thisMove.from.onGround || (lastMove.valid && lastMove.to.onGround)) {
                // TODO: Is (lastMove.valid && lastMove.to.onGround) safe?
                vAllowedDistance = maxJumpGain + jumpGainMargin;
                if (lastMove.to.onGround && vAllowedDistance < 0.1) vAllowedDistance = maxJumpGain + jumpGainMargin;
                // Allow stepping
                if (thisMove.to.onGround) vAllowedDistance = Math.max(cc.sfStepHeight, vAllowedDistance);
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
        /** Relative vertical distance violation */
        boolean vDistRelVL = false;
        /** Expected difference from current to allowed */       
        final double yDistDiffEx          = yDistance - vAllowedDistance; 
        final boolean honeyBlockCollision = MovingUtil.isCollideWithHB(from, to, data) && yDistance < -0.125 && yDistance > -0.128;
        final boolean GravityEffects      = AirWorkarounds.oddJunction(from, to, yDistance, yDistChange, yDistDiffEx, maxJumpGain, resetTo, thisMove, lastMove, data, cc, resetFrom);
        final boolean TooBigMove          = AirWorkarounds.outOfEnvelopeExemptions(yDistance, yDistDiffEx, lastMove, data, from, to, now, yDistChange, maxJumpGain, player, thisMove, resetTo);
        final boolean TooShortMove        = AirWorkarounds.shortMoveExemptions(yDistance, yDistDiffEx, lastMove, data, from, to, now, strictVdistRel, maxJumpGain, vAllowedDistance, player, thisMove);
        final boolean TooFastFall         = AirWorkarounds.fastFallExemptions(yDistance, yDistDiffEx, lastMove, data, from, to, now, strictVdistRel, yDistChange, resetTo, fromOnGround, toOnGround, maxJumpGain, player, thisMove, resetFrom);
        final boolean VEnvHack            = AirWorkarounds.venvHacks(from, to, yDistance, yDistChange, thisMove, lastMove, data, resetFrom, resetTo);
        final boolean TooBigMoveNoData    = AirWorkarounds.outOfEnvelopeNoData(yDistance, from, to, thisMove, resetTo, data);

        // Quick invalidation for too much water envelope
        // if (!from.isInLiquid() && strictVdistRel 
        //     && data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID
        //     && yDistance > 0.3 && yDistance > vAllowedDistance 
        //     && data.getOrUseVerticalVelocity(yDistance) == null) {
        //     thisMove.invalidate();
        // }

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
        
        // No match found (unexpected move) or move is within allowed dist: use velocity as compensation, if available.
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
        
                if (AirWorkarounds.vDistSBExemptions(toOnGround, thisMove, lastMove, data, cc, now, player, 
                                                     totalVDistViolation, yDistance, fromOnGround, tags, to, from)) {
                    // Edge cases.
                }
                // Attempt to use velocity.
                else if (data.getOrUseVerticalVelocity(yDistance) == null) {
                    vDistanceAboveLimit = Math.max(vDistanceAboveLimit, totalVDistViolation);
                    tags.add("vdistsb(" + StringUtil.fdec3.format((to.getY()-data.getSetBackY())) +"/"+ maxJumpHeight + ")");
                }
            }
        }


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
                    if (data.bunnyhopDelay < 9 && !((lastMove.touchedGround || lastMove.from.onGroundOrResetCond)
                        && lastMove.yDistance == 0D) && data.getOrUseVerticalVelocity(yDistance) == null) {
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
                // TODO: sfDirty: Account for actual velocity (demands consuming queued for dir-change(!))!
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
                            || yDistance <= 0.0 && lastMove.headObstructed && lastMove.yDistance >= 0.0) {
                            // Exempt.
                            tags.add("lowjump_skip");
                        }
                        else {
                            // Violation
                            vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(minJumpHeight - setBackYDistance));
                            // Set a flag to tell us that from here, this whole descending phase is due to a lowjump
                            data.sfLowJump = true;
                            // Feed the Improbable.
                            Improbable.feed(player, (float) cc.yOnGround, System.currentTimeMillis());
                        }
                    }
                } 
            }
        }
        

        ////////////////////////////////////////////////////////
        // The Vertical Accounting subcheck: gravity enforcer //
        ///////////////////////////////////////////////////////
        if (InAirPhase && cc.survivalFlyAccountingV) {

            // Currently only for "air" phases.
            if (MovingUtil.isCollideWithHB(from, to, data) && thisMove.yDistance < 0.0 && thisMove.yDistance > -0.21) {
                data.vDistAcc.clear();
                data.vDistAcc.add((float) -0.2033);
            }
            else if (ChangedYDir && lastMove.yDistance > 0.0) { // lastMove.toIsValid is checked above. 
                // Change to descending phase.
                data.vDistAcc.clear();
                // Allow adding 0.
                data.vDistAcc.add((float) yDistance);
            }
            else if (thisMove.verVelUsed == null // Only skip if just used.
                    && !(lastMove.from.inLiquid && Math.abs(yDistance) < 0.31 
                    || data.timeRiptiding + 1000 > now)) { 
                
                // Here yDistance can be negative and positive.
                data.vDistAcc.add((float) yDistance);
                final double accAboveLimit = verticalAccounting(yDistance, data.vDistAcc, tags, "vacc" + (data.isVelocityJumpPhase() ? "dirty" : ""));

                if (accAboveLimit > vDistanceAboveLimit) {
                    if (data.getOrUseVerticalVelocity(yDistance) == null) {
                        vDistanceAboveLimit = accAboveLimit;
                    }
                }         
            }
            // Just to exclude source of error, might be redundant.
            else data.vDistAcc.clear(); 
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
    private static final double verticalAccounting(final double yDistance, 
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
     * Buffer, velocity, bunnyhop, block move and reset-item.
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
                                       final MovingData data, final MovingConfig cc, final IPlayerData pData, final int tick, boolean useBlockChangeTracker) {


        // 1: Attempt to reset item on NoSlow Violation, if set so in the configuration.
        if (cc.survivalFlyResetItem && hDistanceAboveLimit > 0.0 && data.sfHorizontalBuffer <= 0.5 && tags.contains("usingitem")) {
            tags.add("itemreset");
            // Handle through nms
            if (mcAccess.getHandle().resetActiveItem(player)) {
                data.isUsingItem = false;
                pData.requestUpdateInventory();
            }
            // Off hand (non nms)
            else if (Bridge1_9.hasGetItemInOffHand() && data.offHandUse) {
                ItemStack stack = Bridge1_9.getItemInOffHand(player);
                if (stack != null) {
                    if (ServerIsAtLeast1_13) {
                        if (player.isHandRaised()) {
                            // Does nothing
                        }
                        // False positive
                        else data.isUsingItem = false;
                    } 
                    else {
                        player.getInventory().setItemInOffHand(stack);
                        data.isUsingItem = false;
                    }
                }
            }
            // Main hand (non nms)
            else if (!data.offHandUse) {
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
                    else data.isUsingItem = false;
                } 
                else {
                    if (stack != null) {
                        Bridge1_9.setItemInMainHand(player, stack);
                    }
                }
                data.isUsingItem = false;
            }
            if (!data.isUsingItem) {
                double[] hResult = hDistRel(from, to, pData, player, data, thisMove, lastMove, cc, sprinting, false, tick, useBlockChangeTracker);
                hAllowedDistance = hResult[0];
                hDistanceAboveLimit = hResult[1];
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

        // Add the hspeed tag on violation.
        if (hDistanceAboveLimit > 0.0) {
            tags.add("hspeed");
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
     * Inside liquids vertical speed checking. setFrictionJumpPhase must be set
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
        
        ////////////////////////
        // Minimal speed.     //
        ////////////////////////
        if (yDistAbs <= baseSpeed) {
            return new double[]{baseSpeed, 0.0};
        }
        

        /////////////////////////////////////
        // 1: Handle bubble columns 1.13+  // 
        ////////////////////////////////////
        // TODO: bubble columns adjacent to each other: push prevails on drag. So do check adjacent blocks and see if they are draggable as well.
        //[^ Problem: we only get told if the column can drag players]
        // TODO: Launch effect. How to treat it... Like bounce effect? [Currently, we add velocity when player leaves a column; value and invalidation is based on a simple counter]
        // NOTE: Launch speed depends on how long one has been in the column (!)
        if (from.isInBubbleStream() || to.isInBubbleStream()) { 
            if (from.isDraggedByBubbleStream() && to.isDraggedByBubbleStream()) {
                tags.add("bubblestream_drag");
                // Players cannot ascend if they get dragged down.
                if (yDistance > 0.0 && data.insideBubbleStreamCount < 0) {
                    return new double[]{0.0, Math.abs(yDistance)};
                }
                return new double[]{Magic.bubbleStreamDescend, yDistAbs - Magic.bubbleStreamDescend};
            }
            else {
                tags.add("bubblestream_push("+ data.insideBubbleStreamCount +")");
                // Players can't descend if getting pushed up by a bubble stream (unless they are on the surface, in this case they'll sink back in a bit)
                if (yDistance < 0.0 && BlockProperties.isLiquid(from.getTypeIdAbove())) {
                    return new double[]{0.0, Math.abs(yDistance)};
                }
                return new double[]{Magic.bubbleStreamAscend, yDistAbs - Magic.bubbleStreamAscend};
            }
        }
        

        /////////////////////////////////////////////////////////
        // 2: Vertical checking for waterlogged blocks 1.13+   //
        /////////////////////////////////////////////////////////
        // Waterlogged after columns because it can conflict with particular elevators (made of honey)
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
        // 3: Friction envelope (allow any kind of slow down). //
        ////////////////////////////////////////////////////////
        final double frictDist = lastMove.toIsValid ? Math.abs(lastMove.yDistance) * data.lastFrictionVertical : baseSpeed; // Bounds differ with sign.
        if (lastMove.toIsValid) {
            // (Descend speed depends on how fast one dives in)
            if (lastMove.yDistance < 0.0 && yDistance < 0.0 && yDistAbs < frictDist + Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN) {
                tags.add("frictionenv(desc)");
                return new double[]{-frictDist - Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN, 0.0};
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


        ///////////////////////////////////////
        // 4: Workarounds for special cases. // 
        //////////////////////////////////////
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
        final double vl2 = Math.abs(yDistAbs - frictDist - (yDistance < 0.0 ? Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN : Magic.GRAVITY_MIN));
        if (vl1 <= vl2) return new double[]{yDistance < 0.0 ? -baseSpeed : baseSpeed, vl1};
        else return new double[]{yDistance < 0.0 ? -frictDist - Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN : frictDist - Magic.GRAVITY_SPAN, vl2};
        
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

        // TODO: Might not be able to ignore vertical velocity if moving off climbable (!).
        data.sfNoLowJump = true;
        data.clearActiveHorVel(); // Might want to clear ALL horizontal vel.
        double vDistanceAboveLimit = 0.0;
        double yDistAbs = Math.abs(yDistance);
        /** Climbing a ladder in water and exiting water for whatever reason speeds up the player a lot in that one transition ... */
        boolean waterStep = lastMove.from.inLiquid && yDistAbs < Magic.swimBaseSpeedV(Bridge1_13.hasIsSwimming());
        double vAllowedDistance = waterStep ? yDistAbs : yDistance < 0.0 ? Magic.climbSpeedDescend : Magic.climbSpeedAscend;
        final double jumpHeight = LiftOffEnvelope.NORMAL.getMaxJumpHeight(0.0) + (data.jumpAmplifier > 0 ? (0.6 + data.jumpAmplifier - 1.0) : 0.0);
        final double maxJumpGain = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier) + 0.005;
        // Quick, temporary fix for scaffolding block
        boolean scaffolding = from.isOnGround() && from.getBlockY() == Location.locToBlock(from.getY()) 
                              && yDistance > 0.0 && yDistance < maxJumpGain;

        if (yDistAbs > vAllowedDistance) {
            // Catch insta-ladder quick.
            if (from.isOnGround(jumpHeight, 0D, 0D, BlockFlags.F_CLIMBABLE)) {
                if (yDistance > maxJumpGain) {
                    vDistanceAboveLimit = yDistAbs - vAllowedDistance;
                    tags.add("climbstep");
                }
                else tags.add("climbheight("+ StringUtil.fdec3.format(yDistance) +")");
            }
            else if (!scaffolding) {
                vDistanceAboveLimit = yDistAbs - vAllowedDistance;
                tags.add("climbspeed");
            }
        }
        
        // Can't climb up with vine not attached to a solid block (legacy).
        if (yDistance > 0.0 && !thisMove.touchedGround && !from.canClimbUp(jumpHeight)) {
            vDistanceAboveLimit = yDistance;
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
    private double[] vDistWeb(final Player player, final PlayerMoveData thisMove, 
                              final boolean toOnGround, final double hDistanceAboveLimit, final long now, 
                              final MovingData data, final MovingConfig cc, final PlayerLocation from) {

        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double yDistance = thisMove.yDistance;
        final boolean step = toOnGround && yDistance > 0.0 && yDistance <= cc.sfStepHeight || thisMove.from.inWeb && !lastMove.from.inWeb && yDistance <= cc.sfStepHeight;
        double vAllowedDistance, vDistanceAboveLimit;
        data.sfNoLowJump = true;
        data.jumpAmplifier = 0; 

        if (yDistance >= 0.0) {
            // Allow ascending if the player is on ground
            if (thisMove.from.onGround) {
                vAllowedDistance = 0.1;
                vDistanceAboveLimit = yDistance - vAllowedDistance;
            }
            // Bubble columns can slowly push the player upwards through the web.
            else if (from.isInBubbleStream() && !from.isDraggedByBubbleStream() && yDistance < Magic.bubbleStreamAscend) {
                vAllowedDistance = lastMove.yDistance * Magic.FRICTION_MEDIUM_WATER;
                vDistanceAboveLimit = yDistance - vAllowedDistance;
                tags.add("bubbleweb");
            }
            // Allow stepping anyway
            else if (step) {
                vAllowedDistance = yDistance;
                vDistanceAboveLimit = 0.0;
                tags.add("webstep");
            }
            // Don't allow players to ascend in web.
            else {
                vAllowedDistance = 0.0;
                vDistanceAboveLimit = yDistance;
            }
        }
        // (Falling speed is static, however if falling from high enough places, it can depend on how fast one "dives" in.)
        else {
            // Lenient on first move(s) in web.
            if (data.insideMediumCount < 4 && lastMove.yDistance <= 0.0) {
                vAllowedDistance = lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR - Magic.GRAVITY_MAX;
            }
            // Ordinary.
            // We could be stricter but spamming WASD in a tower of webs results in random falling speed changes: ca. observed -0.058 (!? Mojang...)
            else vAllowedDistance = -Magic.GRAVITY_MIN * Magic.FRICTION_MEDIUM_AIR;
            vDistanceAboveLimit = yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0;
        }

        if (vDistanceAboveLimit > 0.0) {
            tags.add(yDistance >= 0.0 ? "vweb" : "vwebdesc");
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
                               final MovingData data, final MovingConfig cc, final PlayerLocation from,
                               final boolean fromOnGround) {

        final double yDistance = thisMove.yDistance;
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double jumpGainMargin = 0.005;
        double vAllowedDistance, vDistanceAboveLimit;

        if (yDistance >= 0.0) {
            // Jump speed gain.
            if (thisMove.from.onGround || !lastMove.from.inBerryBush) {
                vAllowedDistance = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier) + jumpGainMargin; 
                vDistanceAboveLimit = yDistance - vAllowedDistance;
            }
            // Likewise webs, one can't ascend in berry bushes (demand immediate fall)
            else {
                vAllowedDistance = 0.0;
                vDistanceAboveLimit = yDistance;
            }
        }
        // (Falling speed is static, however if falling from high enough places, it can depend on how fast one "dives" in.)
        else {
            // Lenient on the first move(s) in bush
            if (data.insideMediumCount < 4 && lastMove.yDistance <= 0.0) {
                vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_SPAN;
            }
            // Ordinary
            // (Falling speed seems to be kept reliably, unlike webs)
            else vAllowedDistance = -Magic.GRAVITY_MIN * data.lastFrictionVertical - 0.0005;
            vDistanceAboveLimit = yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0;
        }
        // (We don't care about velocity here, though we may not be able to ignore PVP velocity)
        if (vDistanceAboveLimit > 0.0) {
            tags.add(yDistance >= 0.0 ? "vbush" : "vbushdesc");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }


   /**
    * Powder snow vertical distance checking (1.17+): behaves similarly to a climbable block; handled in a separate method
    * because of its properties. (This block is ground with leather boots on)
    * @param yDistance
    * @param form
    * @param to
    * @param cc
    * @param data
    * @return vAllowedDistance, vDistanceAboveLimit
    * 
    */
    private double[] vDistPowderSnow(final double yDistance, final PlayerLocation from, final PlayerLocation to, 
                                     final MovingConfig cc, final MovingData data, final Player player) {
            
        boolean fall = false;
        double vAllowedDistance, vDistanceAboveLimit;
        final double yToBlock = from.getY() - from.getBlockY();

        if (yToBlock <= cc.yOnGround) {
            vAllowedDistance = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier, 1.5);
        }
        else {
            if (Bridge1_17.hasLeatherBootsOn(player)) {
                vAllowedDistance = yDistance < 0.0 ? -Magic.snowClimbSpeedDescend : Magic.snowClimbSpeedAscend;
            } 
            else {
                vAllowedDistance = -Magic.snowClimbSpeedDescend;
                fall = true;
            }
        }
        final double yDistDiffEx = yDistance - vAllowedDistance;
        final boolean violation = fall ? Math.abs(yDistDiffEx) > 0.05 : Math.abs(yDistance) > Math.abs(vAllowedDistance);
        vDistanceAboveLimit = violation ? yDistance : 0.0;
        // (Velocity?)
        if (vDistanceAboveLimit > 0.0) {
            tags.add(yDistance >= 0.0 ? "vsnowasc" : "vsnowdesc");
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
        if (Double.isInfinite(data.survivalFlyVL)) data.survivalFlyVL = 0;
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
        if (Double.isInfinite(data.survivalFlyVL)) data.survivalFlyVL = 0;
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
        final String lostSprint = (data.lostSprintCount > 0 ? (" , lostSprint: " + data.lostSprintCount) : "");
        final String hVelUsed = hFreedom > 0 ? " / hVelUsed: " + StringUtil.fdec3.format(hFreedom) : "";
        builder.append("\nOnGround: " + (thisMove.headObstructed ? "(head obstr.) " : "") + (thisMove.touchedGroundWorkaround ? "(touched ground) " : "") + (fromOnGround ? "onground -> " : (resetFrom ? "resetcond -> " : "--- -> ")) + (toOnGround ? "onground" : (resetTo ? "resetcond" : "---")) + ", jumpPhase: " + data.sfJumpPhase + ", LiftOff: " + data.liftOffEnvelope.name() + "(" + data.insideMediumCount + ")");
        final String dHDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(hDistance, lastMove.hDistance) + ")" : "";
        final String dYDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(yDistance, lastMove.yDistance)+ ")" : "";
        final String frictionTick = ("keepFrictionTick= " + data.keepfrictiontick + " , ");
        builder.append("\n Tick counters: " + frictionTick);
        builder.append("\n" + " hDist: " + StringUtil.fdec3.format(hDistance) + dHDist + " / hAD: " + StringUtil.fdec3.format(hAllowedDistance) + hBuf + lostSprint + hVelUsed +
                       "\n" + " vDist: " + StringUtil.fdec3.format(yDistance) + dYDist + " / yDistDiffEx: " + StringUtil.fdec3.format(yDistDiffEx) + " / vAD: " + StringUtil.fdec3.format(vAllowedDistance) + " , setBackY: " + (data.hasSetBack() ? (data.getSetBackY() + " (setBackYDist: " + StringUtil.fdec3.format(to.getY() - data.getSetBackY()) + " / MaxJumpHeight: " + data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier) + ")") : "?"));
        if (lastMove.toIsValid) {
            builder.append("\n fdsq: " + StringUtil.fdec3.format(thisMove.distanceSquared / lastMove.distanceSquared));
            if (data.bunnyhopDelay > 0) {
                builder.append("\n Bunny ratios: " +"c/b("+ StringUtil.fdec3.format(hDistance / thisMove.hAllowedDistanceBase) + ") / c/l(" + StringUtil.fdec3.format(hDistance / lastMove.hDistance) + ")"); // Current/base current/last
            }
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