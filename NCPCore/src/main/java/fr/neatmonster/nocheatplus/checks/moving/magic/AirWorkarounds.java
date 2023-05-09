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

import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;


/**
 * Aggregate every non-ordinary vertical movement here.
 * Every workaround should at least have a very minimal description of its context
 * (Why it is needed, when did the false positive/bug appeared and such)
 */ 
public class AirWorkarounds {

    /**
     * Workarounds for exiting cobwebs and jumping on slime.
     * 
     * @param from
     * @param to
     * @param yDistChange
     * @param data
     * @param resetFrom
     * @param resetTo
     * @param yDistDiffEx
     * @return If to skip those sub-checks.
     */
    public static boolean venvHacks(final PlayerLocation from, final PlayerLocation to, 
                                    final double yDistChange, final MovingData data, 
                                    final boolean resetFrom, final boolean resetTo, final double yDistDiffEx) {
        
        // Only for air phases.
        if (!resetFrom && !resetTo) {
            return false;
        }
        
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        return 
                // 0: Intended for leaving cobwebs (0.03)
                data.liftOffEnvelope == LiftOffEnvelope.NO_JUMP && data.sfJumpPhase < 60
                && (
                    lastMove.toIsValid && lastMove.yDistance < 0.0 
                    && (
                        // 2: Switch to 0 y-Dist on early jump phase.
                        thisMove.yDistance == 0.0 
                        && MathUtil.between(-Magic.GRAVITY_MIN, lastMove.yDistance, -Magic.GRAVITY_ODD / 3.0)
                        && data.ws.use(WRPT.W_M_SF_WEB_0V1)
                        // 2: Decrease too few.
                        || MathUtil.between(-Magic.GRAVITY_MAX, yDistChange, -Magic.GRAVITY_MIN / 3.0)
                        && data.ws.use(WRPT.W_M_SF_WEB_MICROGRAVITY1)
                        // 2: Keep negative y-distance (very likely a player height issue).
                        || yDistChange == 0.0 
                        && MathUtil.between(-Magic.GRAVITY_MAX, lastMove.yDistance, -Magic.GRAVITY_ODD / 3.0)
                        && data.ws.use(WRPT.W_M_SF_WEB_MICROGRAVITY2)
                    )
                    // 1: Keep yDist == 0.0 on first falling.
                    || thisMove.yDistance == 0.0 && MathUtil.between(0, data.sfZeroVdistRepeat, 10)
                    && thisMove.hDistance < 0.125 && lastMove.hDistance < 0.125
                    && MathUtil.between(-2.0, to.getY() - data.getSetBackY(), 0.0) // Quite coarse.
                    && data.ws.use(WRPT.W_M_SF_WEB_0V2)
                    // 1: Exiting a web from below with slowfall.
                    // Very likely a bounding box issue.
                    || thisMove.hasSlowfall
                    && MathUtil.inRange(0.0, yDistDiffEx, Magic.GRAVITY_MAX)
                    && MathUtil.between(0.0, yDistChange, Magic.GRAVITY_MIN)
                    && BlockProperties.isAir(from.getTypeIdBelow())
                    && BlockProperties.isCobweb(from.getTypeIdAbove())
                    && data.ws.use(WRPT.W_M_SF_SLOW_WEB)
                )
                // 0: Jumping on slimes, change viewing direction at the max. height.
                // NOTE: Implicitly removed condition: hdist < 0.125
                // NOTE: Doesn't check for jump amplifier on purpose.
                || thisMove.yDistance == 0.0 && data.sfZeroVdistRepeat == 1 
                && (data.isVelocityJumpPhase() || data.hasSetBack() && MathUtil.between(0.0, to.getY() - data.getSetBackY(), LiftOffEnvelope.NORMAL.getMaxJumpHeight(0.0)))
                && BlockProperties.isSlime(from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - LiftOffEnvelope.NORMAL.getMaxJumpHeight(0.0)), from.getBlockZ()))
                && data.ws.use(WRPT.W_M_SF_SLIME_JP_2X0)
                ;
    }


    /**
     * Search for velocity entries that have a bounce origin. On match, set the friction jump phase for thisMove/lastMove.
     * @param to
     * @param yDistance
     * @param lastMove
     * @param data
     * @return if to apply the friction jump phase
     */
    public static boolean oddBounce(final PlayerLocation to, final double yDistance, final PlayerMoveData lastMove, final MovingData data) {

        final SimpleEntry entry = data.peekVerticalVelocity(yDistance, 0, 4);
        if (entry != null && entry.hasFlag(VelocityFlags.ORIGIN_BLOCK_BOUNCE)) {
            data.setFrictionJumpPhase();
            return true;
        }
        else {
            // Try to use past yDis
            final SimpleEntry entry2 = data.peekVerticalVelocity(lastMove.yDistance, 0, 4);
            if (entry2 != null && entry2.hasFlag(VelocityFlags.ORIGIN_BLOCK_BOUNCE)) {
                data.setFrictionJumpPhase();
                return true;
            }
        }
        return false;
    }


    /**
     * Odd speed decrease after lift-off.
     * Observed just before reaching the maximum gain of speed (i.e.: 0.333) for this jump.
     * 
     * @param to
     * @param yDistDiffEx
     * @param data
     * @return
     */
    public static boolean oddSlope(final PlayerLocation to, final double yDistDiffEx, final MovingData data) {     
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final double maxJumpGain = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);
        return 
                data.sfJumpPhase == 1 
                && Math.abs(yDistDiffEx) < 2.0 * Magic.GRAVITY_SPAN 
                && lastMove.yDistance > 0.0 && thisMove.yDistance < lastMove.yDistance
                && to.getY() - data.getSetBackY() <= data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                && !data.sfLowJump
                && (
                    // 1: Decrease more after lost-ground cases with more y-distance than normal lift-off.
                    // This can happen legitimately only a single time for a given air phase
                    MathUtil.between(maxJumpGain, lastMove.yDistance, 1.05 * maxJumpGain)
                    && data.ws.use(WRPT.W_M_SF_SLOPE1)
                    //&& fallingEnvelope(yDistance, lastMove.yDistance, 2.0 * GRAVITY_SPAN)
                    // 1: Decrease more after going through liquid (but normal ground envelope).
                    || MathUtil.between(0.5 * maxJumpGain, lastMove.yDistance, 0.84 * maxJumpGain)
                    && lastMove.yDistance - thisMove.yDistance <= Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN
                    && data.ws.use(WRPT.W_M_SF_SLOPE2)
                );
    }


    /**
     * A condition for exemption from vdistrel (vDistAir), around where gravity
     * hits most hard, including head obstruction. This method is called with
     * varying preconditions, thus a full envelope check is necessary. Needs
     * last move data.
     * 
     * @param from
     * @param to
     * @param yDistChange
     * @param yDistChange
     * @param yDistDiffEx
     * @param data
     * @return If the condition applies, i.e. if to exempt.
     */
    public static boolean oddGravity(final PlayerLocation from, final PlayerLocation to, 
                                    final double yDistChange, final double yDistDiffEx, 
                                    final MovingData data, final boolean resetFrom, final boolean resetTo,
                                    final boolean fromOnGround, final boolean toOnGround) {
        // Old condition (normal lift-off envelope).
        //        yDistance >= -GRAVITY_MAX - GRAVITY_SPAN 
        //        && (yDistChange < -GRAVITY_MIN && Math.abs(yDistChange) <= 2.0 * GRAVITY_MAX + GRAVITY_SPAN
        //        || from.isHeadObstructed(from.getyOnGround()) || data.fromWasReset && from.isHeadObstructed())
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        return 
                // 0: Any envelope (supposedly normal) near 0 yDistance.
                // NOTE: Perhaps asofold was unknowingly trying to cover / referring to 0.03 cases here
                MathUtil.between(-2.0 * Magic.GRAVITY_MAX - Magic.GRAVITY_MIN, thisMove.yDistance, 2.0 * Magic.GRAVITY_MAX + Magic.GRAVITY_MIN)
                && (
                        // 1: Too big chunk of change, but within reasonable bounds (should be contained in some other generic case?).
                        lastMove.yDistance < 3.0 * Magic.GRAVITY_MAX + Magic.GRAVITY_MIN 
                        && MathUtil.between(-2.5 * Magic.GRAVITY_MAX - Magic.GRAVITY_MIN, yDistChange, -Magic.GRAVITY_MIN)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_1) // Can't use this more than 3 times for a single air phase. Reason: long-jump exploit potential.
                        // 1: Transition to 0.0 yDistance, ascending.
                        || MathUtil.between(Magic.GRAVITY_ODD / 2.0, lastMove.yDistance, Magic.GRAVITY_MIN) && thisMove.yDistance == 0.0
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_2)
                        // 1: yDist inversion near 0 (almost). 
                        // TODO: This actually happens near liquid, but NORMAL env!?
                        // lastYDist < Gravity max + min happens with dirty phase (slimes),. previously: max + span
                        // TODO: Can all cases be reduced to change sign with max. neg. gain of max + span ?
                        || lastMove.yDistance <= Magic.GRAVITY_MAX + Magic.GRAVITY_MIN && lastMove.yDistance > Magic.GRAVITY_ODD
                        && MathUtil.between(-2.0 * Magic.GRAVITY_MAX - Magic.GRAVITY_ODD / 2.0, thisMove.yDistance, Magic.GRAVITY_ODD)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_3) // Confined for one time in-air use.
                        // 1: Head is obstructed. 
                        // TODO: Cover this in a more generic way elsewhere (<= friction envelope + obstructed).
                        || lastMove.yDistance >= 0.0 && thisMove.yDistance < Magic.GRAVITY_ODD
                        && thisMove.yDistance != 0.0 // Prevent too easy exploit.
                        && (thisMove.headObstructed || lastMove.headObstructed)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_4)
                        // 1: Break the block underneath.
                        || lastMove.yDistance < 0.0 && lastMove.to.extraPropertiesValid && lastMove.to.onGround
                        && MathUtil.inRange(-Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN, thisMove.yDistance, Magic.GRAVITY_MIN)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_5)
                        // 1: Slope with slimes (also near ground without velocityJumpPhase, rather lowjump but not always).
                        || lastMove.yDistance < -Magic.GRAVITY_MAX && MathUtil.between(-Magic.GRAVITY_MIN, yDistChange, -Magic.GRAVITY_ODD / 2.0)
                        && BlockProperties.isSlime(from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - 1.0), from.getBlockZ()))
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_6)
                        // 1: Near ground (slime block).
                        || lastMove.yDistance == 0.0 && MathUtil.between(-Magic.GRAVITY_MIN, thisMove.yDistance, -Magic.GRAVITY_ODD / 2.5)
                        && to.isOnGround(Magic.GRAVITY_MIN) 
                        && BlockProperties.isSlime(from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - Magic.GRAVITY_MIN), from.getBlockZ())) 
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_7)
                        // 1: Start to fall after touching ground somehow (possibly too slowly).
                        || (lastMove.touchedGround || lastMove.to.resetCond) && lastMove.yDistance <= Magic.GRAVITY_MIN 
                        && lastMove.yDistance >= - Magic.GRAVITY_MAX
                        && thisMove.yDistance < lastMove.yDistance - Magic.GRAVITY_SPAN 
                        && thisMove.yDistance < Magic.GRAVITY_ODD  // ?? What's with the double check?
                        && thisMove.yDistance > lastMove.yDistance - Magic.GRAVITY_MAX
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_8)
                )
                // 0: With velocity.
                || data.isVelocityJumpPhase()
                && (
                        // 1: Near zero inversion with slimes (rather dirty phase).
                        MathUtil.between(Magic.GRAVITY_ODD, lastMove.yDistance, Magic.GRAVITY_MIN + Magic.GRAVITY_MAX)
                        && thisMove.yDistance <= -lastMove.yDistance && thisMove.yDistance > -lastMove.yDistance - Magic.GRAVITY_MAX - Magic.GRAVITY_ODD
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_1)
                        // 1: Odd mini-decrease with dirty phase (slime).
                        || MathUtil.between(-0.26, lastMove.yDistance, -0.204)
                        && MathUtil.between(-Magic.GRAVITY_MIN, yDistChange, -Magic.GRAVITY_ODD / 4.0)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_2)
                        // 1: Lot's of decrease near zero 
                        // TODO: merge later.
                        || MathUtil.between(-Magic.GRAVITY_MIN, lastMove.yDistance, -Magic.GRAVITY_ODD)
                        && MathUtil.between(-2.0 * Magic.GRAVITY_MAX - 2.0 * Magic.GRAVITY_MIN, thisMove.yDistance, -Magic.GRAVITY_MAX)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_3)
                        // 1: Odd decrease less near zero.
                        || MathUtil.between(-Magic.GRAVITY_MIN, yDistChange, -Magic.GRAVITY_ODD)
                        && MathUtil.between(0.4, lastMove.yDistance, 0.5)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_4)
                        // 1: Small decrease after high edge.
                        // TODO: Consider min <-> span, generic.
                        || lastMove.yDistance == 0.0 
                        && MathUtil.between(-Magic.GRAVITY_MIN, thisMove.yDistance, -Magic.GRAVITY_ODD)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_5)
                        // 1: Too small but decent decrease moving up, marginal violation.
                        || MathUtil.between(0.0, yDistDiffEx, 0.01)
                        && MathUtil.between(Magic.GRAVITY_MAX, thisMove.yDistance, lastMove.yDistance - Magic.GRAVITY_MAX)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_6)
                )
                // 0: Small distance to setback.
                // TODO: is the absolute part needed?
                || data.hasSetBack() && Math.abs(data.getSetBackY() - from.getY()) < 1.0 && !data.sfLowJump 
                && (
                        // 1: Near ground small decrease.
                        MathUtil.between(Magic.GRAVITY_MAX, lastMove.yDistance, 3.0 * Magic.GRAVITY_MAX)
                        && MathUtil.between(-Magic.GRAVITY_MIN, yDistChange, -Magic.GRAVITY_ODD)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_SETBACK)
                )
                // 0: Jump-effect-specific
                // TODO: Which level?
                || data.jumpAmplifier > 0 
                && MathUtil.between(-2.0 * Magic.GRAVITY_MAX - 0.5 * Magic.GRAVITY_MIN, lastMove.yDistance, Magic.GRAVITY_MAX + Magic.GRAVITY_MIN / 2.0)
                && MathUtil.between(-2.0 * Magic.GRAVITY_MAX - 2.0 * Magic.GRAVITY_MIN, thisMove.yDistance, Magic.GRAVITY_MIN)
                && yDistChange < -Magic.GRAVITY_SPAN && data.liftOffEnvelope == LiftOffEnvelope.NORMAL // Skip for other envelopes.
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_JUMPEFFECT)
                // 0: Another near 0 yDistance case.
                // TODO: Inaugurate into some more generic envelope.
                || MathUtil.between(-Magic.GRAVITY_MAX, lastMove.yDistance, Magic.GRAVITY_MIN)
                && !(lastMove.touchedGround || lastMove.to.extraPropertiesValid && lastMove.to.onGroundOrResetCond)
                && MathUtil.between(lastMove.yDistance - Magic.GRAVITY_MAX - 0.5 * Magic.GRAVITY_MIN, thisMove.yDistance, lastMove.yDistance - Magic.GRAVITY_MIN / 2.0 )
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_NEAR_0)
                // 0: On (noob) tower up, the second move has a higher distance than expected, because the first had been starting slightly above the top.
                || yDistDiffEx < Magic.Y_ON_GROUND_DEFAULT && Magic.noobJumpsOffTower(thisMove.yDistance, data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier), thisMove, lastMove, data)
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_3)
            ;
    }


   /**
    * Conditions for exemption from the VDistSB check (Vertical distance to set back)
    * 
    * @param toOnGround
    * @param data
    * @param cc
    * @param now
    * @param player
    * @param totalVDistViolation
    * @param fromOnGround
    * @param tags
    * @param to
    * @param from
    * @return true, if to skip this subcheck
    */
    public static boolean vDistSBExemptions(final boolean toOnGround, final MovingData data, final MovingConfig cc, final long now, final Player player, 
                                            double totalVDistViolation, final boolean fromOnGround,
                                            final Collection<String> tags, final PlayerLocation to, final PlayerLocation from) {
        final double SetBackYDistance = to.getY() - data.getSetBackY();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final IPlayerData pData = DataManager.getPlayerData(player);
        return 
                // 0: Ignore: Legitimate step.
                (fromOnGround || thisMove.touchedGroundWorkaround || lastMove.touchedGround
                && toOnGround && thisMove.yDistance <= cc.sfStepHeight)
                // 0: Teleport to in-air (PaperSpigot 1.7.10).
                // TODO: Legacy, could drop it at this point...
                || Magic.skipPaper(thisMove, lastMove, data)
                // 0: Bunnyhop into a 1-block wide waterfall to reduce vertical water friction -> ascend in water -> leave waterfall 
                // -> have two, in-air ascending phases -> double VdistSB violation due to a too high jump, since speed wasn't reduced by enough when in water.
                || data.sfJumpPhase <= 3 && data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID
                && data.insideMediumCount < 6 && Bridge1_13.hasIsSwimming() && Magic.recentlyInWaterfall(data, 20)
                && (Magic.inAir(thisMove) || Magic.leavingWater(thisMove)) && SetBackYDistance < cc.sfStepHeight 
                && thisMove.yDistance < LiftOffEnvelope.NORMAL.getMaxJumpGain(0.0) 
                && pData.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13) 
                // 0: Lost ground cases
                || thisMove.touchedGroundWorkaround 
                && ( 
                    // 1: Skip if the player could step up by lostground_couldstep.
                    thisMove.yDistance <= cc.sfStepHeight && tags.contains("lostground_couldstep")
                    // 1: Server-sided-trapdoor-touch-miss: player lands directly onto the fence as if it were 1.0 block high
                    || thisMove.yDistance < data.liftOffEnvelope.getMaxJumpGain(0.0) && tags.contains("lostground_trapfence")
                )
        ;
    }


    /**
     * Several types of odd in-air moves, mostly with gravity near maximum,
     * friction, medium change. Needs lastMove.toIsValid.
     * 
     * @param from
     * @param to
     * @param yDistance
     * @param yDistChange
     * @param yDistDiffEx
     * @param maxJumpGain
     * @param resetTo
     * @param lastMove
     * @param data
     * @param cc
     * @return true if a workaround applies.
     */
    public static boolean oddJunction(final PlayerLocation from, final PlayerLocation to, final double yDistChange, 
                                      final double yDistDiffEx, final boolean resetTo,
                                      final MovingData data, final MovingConfig cc, final boolean resetFrom, final Player player,
                                      final boolean fromOnGround, final boolean toOnGround) {
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final double maxJumpGain = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);

        if (!lastMove.toIsValid) {
            return false;
            // Skip everything if last move is invalid
        }
        if (AirWorkarounds.oddGravity(from, to, yDistChange, yDistDiffEx, data, resetFrom, resetTo, fromOnGround, toOnGround)) {
            // Starting to fall / gravity effects.
            return true;
        }
        if ((yDistDiffEx > 0.0 || thisMove.yDistance >= 0.0) && AirWorkarounds.oddSlope(to, yDistDiffEx, data)) {
            // Odd decrease after lift-off.
            return true;
        }
        return false;
    }
}