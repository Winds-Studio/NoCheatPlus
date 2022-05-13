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
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;


/**
 * Aggregate every non-ordinary vertical movement here.
 */
public class AirWorkarounds {

    // ONGOING: Tighten/Review all workarounds: some of them date back to the pre past-move-tracking framework period. They're likely too generic by now.
    // OBSERVED: Review all "landing-on-ground-allows-a-shoter-move" workarounds. They can be exploited for 1-block step cheats.
    // TODO: Review stairs workarounds due to the new shape rework
    // TODO: Review venvHacks,at least cobwebs. (Still needed?)
    // TODO: Aim to remove all oddLiquid cases, replacing them with actual MC equations/formulas.
    /**
     * REMOVED AND TESTED: 
     *  
     *  // REASON: Allows for fastfalling cheats. Falling from great heights doesn't yield any violation without this from testing.
     *  // 0: Disregard not falling faster at some point (our constants don't match 100%).
     *  yDistance < -3.0 && lastMove.yDistance < -3.0 
     *  && Math.abs(yDistDiffEx) < 5.0 * Magic.GRAVITY_MAX 
     *  && data.ws.use(WRPT.W_M_SF_FASTFALL_1)
     *  
     *  // REASON: With the LiquidEnvelope precondition, this will never be applied. Also the lack of any documentation makes it harder to debug. 
     *  // 1: Not documented (!)
     *  || !data.liftOffEnvelope.name().startsWith("LIMIT") && lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN 
     *  && lastMove.yDistance > Magic.GRAVITY_ODD
     *  && yDistance > 0.4 * Magic.GRAVITY_ODD && yDistance - lastMove.yDistance < -Magic.GRAVITY_ODD / 2.0
     *  && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_NOT_NORMAL_ENVELOPE_2)
     * 
     *  // REASON: Not documented. Ignore it.
     *  // 1: (damaged in liquid) ? -> Why would vDistAir run in liquid tho.
     *  || lastMove.yDistance < 0.2 && lastMove.yDistance >= 0.0 && yDistance > -0.2 && yDistance < 2.0 * Magic.GRAVITY_MIN
     *  && data.ws.use(WRPT.W_M_SF_ODDLIQUID_9)
     * 
     *  // REASON: Not documented. Ignore it.
     *  // 1: (Could be reset condition?)
     *  || lastMove.yDistance > 0.4 * Magic.GRAVITY_ODD && lastMove.yDistance < Magic.GRAVITY_MIN && yDistance == 0.0
     *  && data.ws.use(WRPT.W_M_SF_ODDLIQUID_10)
     *  
     *  // REASON: This is what the block change tracker is for, why isn't it working here?
     *  // 0: 1.13+ specific: breaking a block below too fast.
     *  // TODO: Confine more.
     *  || Bridge1_13.hasIsSwimming() 
     *   && (
     *       data.sfJumpPhase == 7 && yDistance < -0.02 && yDistance > -0.2
     *       || data.sfJumpPhase == 3 
     *       && lastMove.yDistance < -0.139 && yDistance > -0.1 && yDistance < 0.005
     *       || yDistance < -0.288 && yDistance > -0.32 
     *       && lastMove.yDistance > -0.1 && lastMove.yDistance < 0.005
     *       )
     *   && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_4)
     *  
     *  // REASON: This is what the block change tracker is for, why isn't it working here?
     *  // 1.13+ specific: breaking a block below too fast.
                // TODO: Confine by ground conditions
                || Bridge1_13.hasIsSwimming() // && lastMove.touchedGround
                && (
                    data.sfJumpPhase == 3 && lastMove.yDistance < -0.139 && yDistance > -0.1 && yDistance < 0.005
                   || yDistance < -0.288 && yDistance > -0.32 && lastMove.yDistance > -0.1 && lastMove.yDistance < 0.005
                ) 
                && data.ws.use(WRPT.W_M_SF_FASTFALL_6)
     * 
     */

    /**
     * Workarounds for exiting cobwebs and jumping on slime.
     * TODO: Get rid of the cobweb workaround and adjust bounding box size to check with rather.
     * 
     * @param from
     * @param to
     * @param yDistance
     * @param data
     * @return If to skip those sub-checks.
     */
    public static boolean venvHacks(final PlayerLocation from, final PlayerLocation to, final double yDistance, 
                                    final double yDistChange, final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                    final MovingData data, final boolean resetFrom, final boolean resetTo) {
        
        // Only for air phases.
        if (!resetFrom && !resetTo) {
            return false;
        }

        return 
                // 0: Intended for cobweb.
                data.liftOffEnvelope == LiftOffEnvelope.NO_JUMP && data.sfJumpPhase < 60
                && (
                    lastMove.toIsValid && lastMove.yDistance < 0.0 
                    && (
                        // 2: Switch to 0 y-Dist on early jump phase.
                        yDistance == 0.0 && lastMove.yDistance < -Magic.GRAVITY_ODD / 3.0 && lastMove.yDistance > -Magic.GRAVITY_MIN
                        && data.ws.use(WRPT.W_M_SF_WEB_0V1)
                        // 2: Decrease too few.
                        || yDistChange < -Magic.GRAVITY_MIN / 3.0 && yDistChange > -Magic.GRAVITY_MAX
                        && data.ws.use(WRPT.W_M_SF_WEB_MICROGRAVITY1)
                        // 2: Keep negative y-distance (very likely a player height issue).
                        || yDistChange == 0.0 && lastMove.yDistance > -Magic.GRAVITY_MAX && lastMove.yDistance < -Magic.GRAVITY_ODD / 3.0
                        && data.ws.use(WRPT.W_M_SF_WEB_MICROGRAVITY2)
                    )
                    // 1: Keep yDist == 0.0 on first falling.
                    || yDistance == 0.0 && data.sfZeroVdistRepeat > 0 && data.sfZeroVdistRepeat < 10 
                    && thisMove.hDistance < 0.125 && lastMove.hDistance < 0.125
                    && to.getY() - data.getSetBackY() < 0.0 && to.getY() - data.getSetBackY() > -2.0 // Quite coarse.
                    && data.ws.use(WRPT.W_M_SF_WEB_0V2)
                )
                // 0: Jumping on slimes, change viewing direction at the max. height.
                // NOTE: Implicitly removed condition: hdist < 0.125
                || yDistance == 0.0 && data.sfZeroVdistRepeat == 1 
                && (data.isVelocityJumpPhase() || data.hasSetBack() && to.getY() - data.getSetBackY() < 1.35 && to.getY() - data.getSetBackY() > 0.0)
                && BlockProperties.isSlime(from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - 1.35), from.getBlockZ()))
                && data.ws.use(WRPT.W_M_SF_SLIME_JP_2X0)
                ;
    }


    /**
     * Search for velocity entries that have a bounce origin. On match, set the friction jump phase for thisMove/lastMove.
     * 
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
     * @param to
     * @param yDistance
     * @param maxJumpGain
     * @param yDistDiffEx
     * @param data
     * @return
     */
    private static boolean oddSlope(final PlayerLocation to, final double yDistance, final double maxJumpGain, 
                                    final double yDistDiffEx, final PlayerMoveData lastMove, 
                                    final MovingData data) {

        return data.sfJumpPhase == 1 
                && Math.abs(yDistDiffEx) < 2.0 * Magic.GRAVITY_SPAN 
                && lastMove.yDistance > 0.0 && yDistance < lastMove.yDistance
                && to.getY() - data.getSetBackY() <= data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                && (
                    // 1: Decrease more after lost-ground cases with more y-distance than normal lift-off.
                    lastMove.yDistance > maxJumpGain && lastMove.yDistance < 1.1 * maxJumpGain 
                    && data.ws.use(WRPT.W_M_SF_SLOPE1)
                    //&& fallingEnvelope(yDistance, lastMove.yDistance, 2.0 * GRAVITY_SPAN)
                    // 1: Decrease more after going through liquid (but normal ground envelope).
                    || lastMove.yDistance > 0.5 * maxJumpGain && lastMove.yDistance < 0.84 * maxJumpGain
                    && lastMove.yDistance - yDistance <= Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN
                    && data.ws.use(WRPT.W_M_SF_SLOPE2)
                );
    }


    /**
     * Jump after leaving the liquid near ground / jumping through liquid or simply leaving liquid in general
     * (rather friction envelope, problematic). Needs last move data.
     * 
     * @return If the exemption condition applies.
     */
    private static boolean oddLiquid(final double yDistance, final double yDistDiffEx, final double maxJumpGain, 
                                     final boolean resetTo, final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                     final MovingData data, final boolean resetFrom, final PlayerLocation from) {

        // TODO: Relate jump phase to last/second-last move fromWasReset (needs keeping that data in classes).
        // TODO: And distinguish where JP=2 is ok?
        // TODO: Most are medium transitions with the possibility to keep/alter friction or even speed on 1st/2nd move (counting in the transition).
        final boolean LiquidEnvelope = (data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_NEAR_GROUND || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_SURFACE);
        final int blockdata = from.getData(from.getBlockX(), from.getBlockY(), from.getBlockZ());
        if (data.sfJumpPhase != 1 && data.sfJumpPhase != 2) {
            return false;
        }
        return 
                // 0: Falling slightly too fast (velocity/special).
                yDistDiffEx < 0.0 
                && (
                    // 1: Friction issues (bad).
                    // TODO: Velocity jump phase isn't exact on that account, but shouldn't hurt.
                    // TODO: Liquid-bound or not?
                    (LiquidEnvelope || data.isVelocityJumpPhase())
                    && (   
                        Magic.fallingEnvelope(yDistance, lastMove.yDistance, data.lastFrictionVertical, Magic.GRAVITY_ODD / 2.0)
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_1)
                        // 2: Moving out of lava with velocity.
                        || lastMove.from.extraPropertiesValid && lastMove.from.inLava
                        && Magic.enoughFrictionEnvelope(thisMove, lastMove, Magic.FRICTION_MEDIUM_LAVA, 0.0, 2.0 * Magic.GRAVITY_MAX, 4.0)
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_2)
                    )
                )
                // 0: LIMIT_LIQUID, vDist inversion (!).
                || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID 
                && data.sfJumpPhase == 1 && lastMove.toIsValid && yDistDiffEx <= 0.0
                && lastMove.from.inLiquid && !(lastMove.to.extraPropertiesValid && lastMove.to.inLiquid)
                && !resetFrom && resetTo // TODO: There might be other cases (possibly wrong bounding box).
                && lastMove.yDistance > 0.0 && lastMove.yDistance < 0.5 * Magic.GRAVITY_ODD
                && yDistance < 0.0 && Math.abs(Math.abs(yDistance) - lastMove.yDistance) < Magic.GRAVITY_SPAN / 2.0
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_3)
                // 0: Not normal envelope, moving out of liquid somehow.
                || LiquidEnvelope
                && (
                        // 1: Jump or decrease falling speed after a small gain (could be bounding box?).
                        // TODO: Water-bound or not?
                        yDistDiffEx > 0.0 && yDistance > lastMove.yDistance && yDistance < 0.84 * maxJumpGain
                        && lastMove.yDistance >= -Magic.GRAVITY_MAX - Magic.GRAVITY_MIN 
                        && lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_4)
                        // 1: Too few decrease on first moves out of water (upwards).
                        || lastMove.yDistance > 0.0 && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX && yDistDiffEx > 0.0 
                        && yDistDiffEx < Magic.GRAVITY_MAX + Magic.GRAVITY_ODD
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_5)
                        // 1: Odd decrease of speed as if still in water, moving out of water (downwards).
                        // TODO: data.lastFrictionVertical might not catch it (jump phase 0 -> next = air).
                        // TODO: Could not reproduce since first time (use DebugUtil.debug(String, boolean)).
                        // TODO: Due to the TODO above, we could consider dropping this one.
                        || lastMove.yDistance < -2.0 * Magic.GRAVITY_MAX && data.sfJumpPhase == 1 
                        && yDistance < -Magic.GRAVITY_MAX && yDistance > lastMove.yDistance 
                        && Math.abs(yDistance - lastMove.yDistance * data.lastFrictionVertical) < Magic.GRAVITY_MAX 
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_6)
                        // 1: Falling too slow, keeping roughly gravity-once speed.
                        || data.sfJumpPhase == 1
                        && lastMove.yDistance < -Magic.GRAVITY_ODD && lastMove.yDistance > -Magic.GRAVITY_MAX - Magic.GRAVITY_MIN 
                        && Math.abs(lastMove.yDistance - yDistance) < Magic.GRAVITY_SPAN 
                        && (yDistance < lastMove.yDistance || yDistance < Magic.GRAVITY_MIN)
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_7)
                       /* 
                        * Note that at the time these were added, only 5 lift off envelopes were available, UNKNOWN, LIMIT_LIQUID, LIMIT_NEAR_GROUND, NORMAL, NO_JUMP
                        * Excluding NO_JUMP(webs), UNKNOWN and NORMAL, these were likely intended for liquid only.
                        */
                        // 1: Wild-card allow half gravity near 0 yDistance.
                        // TODO: Check for removal of included cases elsewhere.
                        || !(LiquidEnvelope && (Math.abs(yDistance) > data.liftOffEnvelope.getMaxJumpGain(0.0) || blockdata > 3)) 
                        && lastMove.yDistance > -10.0 * Magic.GRAVITY_ODD / 2.0 && lastMove.yDistance < 10.0 * Magic.GRAVITY_ODD
                        && yDistance < lastMove.yDistance - Magic.GRAVITY_MIN / 2.0 && yDistance > lastMove.yDistance - Magic.GRAVITY_MAX
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_8)
                        // 1: Too small decrease, right after lift off.
                        || data.sfJumpPhase == 1 && lastMove.yDistance > -Magic.GRAVITY_ODD 
                        && lastMove.yDistance <= Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN
                        && Math.abs(yDistance - lastMove.yDistance) < 0.0114
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_11)
                        // 1: Any leaving liquid and keeping distance once.
                        || data.sfJumpPhase == 1 
                        && Math.abs(yDistance) <= Magic.swimBaseSpeedV(Bridge1_13.isSwimming(from.getPlayer())) && yDistance == lastMove.yDistance
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_12)
                        // 1: Not documented -> What is this why is it even here?
                        // (Leaving a climbable having been through water -> next move in air?)
                        || lastMove.from.inLiquid && lastMove.from.onClimbable && yDistDiffEx > 0.0 
                        && data.ws.use(WRPT.W_M_SF_ODDLIQUID_13)
                        // 1: Falling slightly too slow.
                        || yDistDiffEx > 0.0 
                        && (
                            // 2: Falling too slow around 0 yDistance.
                            lastMove.yDistance > -2.0 * Magic.GRAVITY_MAX - Magic.GRAVITY_ODD
                            && yDistance < lastMove.yDistance && lastMove.yDistance - yDistance < Magic.GRAVITY_MAX
                            && lastMove.yDistance - yDistance > Magic.GRAVITY_MIN / 4.0
                            && data.ws.use(WRPT.W_M_SF_ODDLIQUID_14)
                            // 2: Moving out of liquid with velocity.
                            || yDistance > 0.0 && data.sfJumpPhase == 1 && yDistDiffEx < 4.0 * Magic.GRAVITY_MAX
                            && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX && data.isVelocityJumpPhase()
                            && data.ws.use(WRPT.W_M_SF_ODDLIQUID_15)
                            // 1: Odd decrease with having been in water.
                            // NOTE: water-bound! (Will need to see if false positives appear with lava)
                            || yDistDiffEx > 0.0 && yDistDiffEx < Magic.GRAVITY_MIN && data.sfJumpPhase == 1 
                            && lastMove.from.extraPropertiesValid && lastMove.from.inWater
                            && lastMove.yDistance < -Magic.GRAVITY_ODD / 2.0 && lastMove.yDistance > -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN
                            && yDistance < lastMove.yDistance - 0.001 
                            && data.ws.use(WRPT.W_M_SF_ODDLIQUID_16)
                        )
                )
        ; // (return)
    }


    /**
     * A condition for exemption from vdistrel (vDistAir), around where gravity
     * hits most hard, including head obstruction. This method is called with
     * varying preconditions, thus a full envelope check is necessary. Needs
     * last move data.
     * 
     * @param yDistance
     * @param yDistChange
     * @param data
     * @return If the condition applies, i.e. if to exempt.
     */
    private static boolean oddGravity(final PlayerLocation from, final PlayerLocation to, 
                                      final double yDistance, final double yDistChange, 
                                      final double yDistDiffEx, final PlayerMoveData thisMove, 
                                      final PlayerMoveData lastMove, final MovingData data) {

        // Old condition (normal lift-off envelope).
        //        yDistance >= -GRAVITY_MAX - GRAVITY_SPAN 
        //        && (yDistChange < -GRAVITY_MIN && Math.abs(yDistChange) <= 2.0 * GRAVITY_MAX + GRAVITY_SPAN
        //        || from.isHeadObstructed(from.getyOnGround()) || data.fromWasReset && from.isHeadObstructed())
        return 
                // 0: Any envelope (supposedly normal) near 0 yDistance.
                yDistance > -2.0 * Magic.GRAVITY_MAX - Magic.GRAVITY_MIN && yDistance < 2.0 * Magic.GRAVITY_MAX + Magic.GRAVITY_MIN
                && (
                        // 1: Too big chunk of change, but within reasonable bounds (should be contained in some other generic case?).
                        lastMove.yDistance < 3.0 * Magic.GRAVITY_MAX + Magic.GRAVITY_MIN && yDistChange < -Magic.GRAVITY_MIN 
                        && yDistChange > -2.5 * Magic.GRAVITY_MAX -Magic.GRAVITY_MIN
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_1)
                        // 1: Transition to 0.0 yDistance, ascending.
                        || lastMove.yDistance > Magic.GRAVITY_ODD / 2.0 && lastMove.yDistance < Magic.GRAVITY_MIN && yDistance == 0.0
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_2)
                        // 1: yDist inversion near 0 (almost). 
                        // TODO: This actually happens near liquid, but NORMAL env!?
                        // TODO: Find out if this inversion actually happens with normal envelope... Should be rare, if so (confine to one time use until reset condition then)
                        // lastYDist < Gravity max + min happens with dirty phase (slimes),. previously: max + span
                        // TODO: Can all cases be reduced to change sign with max. neg. gain of max + span ?
                        || lastMove.yDistance <= Magic.GRAVITY_MAX + Magic.GRAVITY_MIN && lastMove.yDistance > Magic.GRAVITY_ODD
                        && yDistance < Magic.GRAVITY_ODD && yDistance > -2.0 * Magic.GRAVITY_MAX - Magic.GRAVITY_ODD / 2.0
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_3)
                        // 1: Head is obstructed. 
                        // TODO: Cover this in a more generic way elsewhere (<= friction envelope + obstructed).
                        || lastMove.yDistance >= 0.0 && yDistance < Magic.GRAVITY_ODD
                        && (thisMove.headObstructed || lastMove.headObstructed)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_4)
                        // 1: Break the block underneath.
                        || lastMove.yDistance < 0.0 && lastMove.to.extraPropertiesValid && lastMove.to.onGround
                        && yDistance >= -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN && yDistance <= Magic.GRAVITY_MIN
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_5)
                        // 1: Slope with slimes (also near ground without velocityJumpPhase, rather lowjump but not always).
                        || lastMove.yDistance < -Magic.GRAVITY_MAX && yDistChange < - Magic.GRAVITY_ODD / 2.0 && yDistChange > -Magic.GRAVITY_MIN
                        && BlockProperties.isSlime(from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - 1.0), from.getBlockZ()))
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_6)
                        // 1: Near ground (slime block).
                        || lastMove.yDistance == 0.0 && yDistance < -Magic.GRAVITY_ODD / 2.5 
                        && yDistance > -Magic.GRAVITY_MIN && to.isOnGround(Magic.GRAVITY_MIN) 
                        && BlockProperties.isSlime(from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - 0.9), from.getBlockZ())) 
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_7)
                        // 1: Start to fall after touching ground somehow (possibly too slowly).
                        || (lastMove.touchedGround || lastMove.to.resetCond) && lastMove.yDistance <= Magic.GRAVITY_MIN 
                        && lastMove.yDistance >= - Magic.GRAVITY_MAX
                        && yDistance < lastMove.yDistance - Magic.GRAVITY_SPAN && yDistance < Magic.GRAVITY_ODD 
                        && yDistance > lastMove.yDistance - Magic.GRAVITY_MAX
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_8)
                )
                // 0: With velocity.
                || data.isVelocityJumpPhase()
                && (
                        // 1: Near zero inversion with slimes (rather dirty phase).
                        lastMove.yDistance > Magic.GRAVITY_ODD && lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_MIN
                        && yDistance <= -lastMove.yDistance && yDistance > -lastMove.yDistance - Magic.GRAVITY_MAX - Magic.GRAVITY_ODD
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_1)
                        // 1: Odd mini-decrease with dirty phase (slime).
                        || lastMove.yDistance < -0.204 && yDistance > -0.26
                        && yDistChange > -Magic.GRAVITY_MIN && yDistChange < -Magic.GRAVITY_ODD / 4.0
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_2)
                        // 1: Lot's of decrease near zero TODO: merge later.
                        || lastMove.yDistance < -Magic.GRAVITY_ODD && lastMove.yDistance > -Magic.GRAVITY_MIN
                        && yDistance > -2.0 * Magic.GRAVITY_MAX - 2.0 * Magic.GRAVITY_MIN && yDistance < -Magic.GRAVITY_MAX
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_3)
                        // 1: Odd decrease less near zero.
                        || yDistChange > -Magic.GRAVITY_MIN && yDistChange < -Magic.GRAVITY_ODD 
                        && lastMove.yDistance < 0.5 && lastMove.yDistance > 0.4
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_4)
                        // 1: Small decrease after high edge.
                        // TODO: Consider min <-> span, generic.
                        || lastMove.yDistance == 0.0 && yDistance > -Magic.GRAVITY_MIN && yDistance < -Magic.GRAVITY_ODD
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_5)
                        // 1: Too small but decent decrease moving up, marginal violation.
                        || yDistDiffEx > 0.0 && yDistDiffEx < 0.01
                        && yDistance > Magic.GRAVITY_MAX && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_6)
                )
                // 0: Small distance to setback.
                || data.hasSetBack() && Math.abs(data.getSetBackY() - from.getY()) < 1.0 && !data.sfLowJump // Ensure this workaround only gets applied if the player performed a full jump (Experimental)
                && (
                        // 1: Near ground small decrease.
                        lastMove.yDistance > Magic.GRAVITY_MAX && lastMove.yDistance < 3.0 * Magic.GRAVITY_MAX
                        && yDistChange > -Magic.GRAVITY_MIN && yDistChange < -Magic.GRAVITY_ODD
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_SETBACK)
                )
                // 0: Jump-effect-specific
                // TODO: Which level?
                || data.jumpAmplifier > 0 && lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_MIN / 2.0 
                && lastMove.yDistance > -2.0 * Magic.GRAVITY_MAX - 0.5 * Magic.GRAVITY_MIN
                && yDistance > -2.0 * Magic.GRAVITY_MAX - 2.0 * Magic.GRAVITY_MIN && yDistance < Magic.GRAVITY_MIN
                && yDistChange < -Magic.GRAVITY_SPAN && data.liftOffEnvelope == LiftOffEnvelope.NORMAL // Skip for other envelopes.
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_JUMPEFFECT)
                // 0: Another near 0 yDistance case.
                // TODO: Inaugurate into some more generic envelope.
                || lastMove.yDistance > -Magic.GRAVITY_MAX && lastMove.yDistance < Magic.GRAVITY_MIN 
                && !(lastMove.touchedGround || lastMove.to.extraPropertiesValid && lastMove.to.onGroundOrResetCond)
                && yDistance < lastMove.yDistance - Magic.GRAVITY_MIN / 2.0 
                && yDistance > lastMove.yDistance - Magic.GRAVITY_MAX - 0.5 * Magic.GRAVITY_MIN
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_NEAR_0)
            ;
    }


    /**
     * Odd behavior with moving up or (slightly) down, not like the ordinary
     * friction mechanics, accounting for more than one past move. Needs
     * lastMove to be valid.
     * 
     * @param yDistance
     * @param lastMove
     * @param data
     * @return
     */
    private static boolean oddFriction(final double yDistance, final double yDistDiffEx, final PlayerMoveData lastMove, final MovingData data, final PlayerLocation from) {

        // Use past move data for two moves.
        final PlayerMoveData pastMove1 = data.playerMoves.getSecondPastMove();
        final boolean LiquidEnvelope = (data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_NEAR_GROUND || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_SURFACE);
        if (!lastMove.to.extraPropertiesValid || !pastMove1.toIsValid || !pastMove1.to.extraPropertiesValid) {
            return false;
        }

        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        return 
                // 0: First move into air, moving out of liquid.
                // (These should probably be oddLiquid cases, might pull pastMove1 to vDistAir later.)
                LiquidEnvelope && data.sfJumpPhase == 1 && Magic.inAir(thisMove)
                && (
                        // 1: Towards ascending rather.
                        pastMove1.yDistance > lastMove.yDistance - Magic.GRAVITY_MAX
                        && lastMove.yDistance > yDistance + Magic.GRAVITY_MAX && lastMove.yDistance > 0.0 // Positive speed. TODO: rather > 1.0 (!).
                        && (
                                // 2: Odd speed decrease bumping into a block sideways somehow, having moved through water.
                                yDistDiffEx < 0.0 && Magic.splashMove(lastMove, pastMove1)
                                && (
                                        // 3: Odd too high decrease, after middle move being within friction envelope.
                                        yDistance > lastMove.yDistance / 5.0
                                        && data.ws.use(WRPT.W_M_SF_ODDFRICTION_1)
                                        // 3: Two times about the same decrease (e.g. near 1.0), ending up near zero distance.
                                        || yDistance > -Magic.GRAVITY_MAX 
                                        && Math.abs(pastMove1.yDistance - lastMove.yDistance - (lastMove.yDistance - thisMove.yDistance)) < Magic.GRAVITY_MAX
                                        && data.ws.use(WRPT.W_M_SF_ODDFRICTION_2)
                                )
                                // 2: Almost keep speed (gravity only), moving out of lava with (high) velocity.
                                // (Needs jump phase == 1, to confine decrease from pastMove1 to lastMove.)
                                // TODO: Never seems to apply.
                                // TODO: Might explicitly demand (lava) friction decrease from pastMove1 to lastMove.
                                || Magic.inLiquid(pastMove1) && pastMove1.from.inLava
                                && Magic.leavingLiquid(lastMove) && lastMove.yDistance > 4.0 * Magic.GRAVITY_MAX
                                && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX 
                                && yDistance > lastMove.yDistance - 2.0 * Magic.GRAVITY_MAX
                                && Math.abs(lastMove.yDistance - pastMove1.yDistance) > 4.0 * Magic.GRAVITY_MAX
                                && data.ws.use(WRPT.W_M_SF_ODDFRICTION_3)
                        )
                        // 1: Less 'strict' speed increase, descending rather.
                        || pastMove1.yDistance < 0.0
                        && lastMove.yDistance - Magic.GRAVITY_MAX < yDistance && yDistance < 0.7 * lastMove.yDistance // Actual speed decrease due to water.
                        && Math.abs(pastMove1.yDistance + lastMove.yDistance) > 2.5
                        && (
                            Magic.splashMove(lastMove, pastMove1) && pastMove1.yDistance > lastMove.yDistance // (Actually splashMove or aw-ww-wa-aa)
                            // Allow more decrease if moving through more solid water.
                            || Magic.inLiquid(pastMove1) && Magic.leavingLiquid(lastMove) && pastMove1.yDistance *.7 > lastMove.yDistance
                        )
                        && data.ws.use(WRPT.W_M_SF_ODDFRICTION_4)
                        // 1: Strong decrease after rough keeping speed (hold space bar, with velocity, descending).
                        || yDistance < -0.5 // Arbitrary, actually observed was around 2.
                        && pastMove1.yDistance < yDistance && lastMove.yDistance < yDistance
                        && Math.abs(pastMove1.yDistance - lastMove.yDistance) < Magic.GRAVITY_ODD
                        && yDistance < lastMove.yDistance * 0.67 && yDistance > lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_MIN
                        && (Magic.splashMoveNonStrict(lastMove, pastMove1) || Magic.inLiquid(pastMove1) && Magic.leavingLiquid(lastMove))
                        && data.ws.use(WRPT.W_M_SF_ODDFRICTION_5)
                )
                // 0: Odd normal envelope set.
                || data.liftOffEnvelope == LiftOffEnvelope.NORMAL && data.sfJumpPhase == 1 && Magic.inAir(thisMove) 
                && (
                    //                                && data.isVelocityJumpPhase()
                    // 1: Velocity very fast into water above.
                    (Magic.splashMoveNonStrict(lastMove, pastMove1) || Magic.inLiquid(pastMove1) && Magic.leavingLiquid(lastMove))
                    && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX 
                    && yDistance > lastMove.yDistance - 2.0 * Magic.GRAVITY_MAX
                    && (
                        Math.abs(lastMove.yDistance - pastMove1.yDistance) > 4.0 * Magic.GRAVITY_MAX
                        || pastMove1.yDistance > 3.0 && lastMove.yDistance > 3.0 && Math.abs(lastMove.yDistance - pastMove1.yDistance) < 2.0 * Magic.GRAVITY_MAX
                    ) 
                    && data.ws.use(WRPT.W_M_SF_ODDFRICTION_6)
                )
                // 0: Exiting a berry bush (this move in air but with bush friction)
                // [Still needed, likely wrong bounding box]
                || lastMove.from.inBerryBush && !thisMove.from.inBerryBush && data.liftOffEnvelope == LiftOffEnvelope.BERRY_JUMP
                && yDistance < -Magic.GRAVITY_MIN && yDistance > Magic.bushSpeedDescend && lastMove.from.extraPropertiesValid
                && data.ws.use(WRPT.W_M_SF_ODDFRICTION_7)
            ;
    }


    /**
     * Odd vertical movements with negative yDistance. Rather too fast falling cases.
     * Called after having checked for too big and too short moves, with negative yDist and yDistDiffEx <= 0.0.
     * Doesn't require lastMove's data.
     * 
     * @param yDistance
     * @param yDistDiffEx Difference from actual yDistance to vAllowedDistance
     * @param lastMove
     * @param data
     * @param from
     * @param to
     * @param now
     * @param strictVdistRel
     * @param yDistChange Change seen from last yDistance. Double.MAX_VALUE if lastMove is not valid.
     * @param resetTo
     * @param fromOnGround
     * @param toOnGround
     * @param maxJumpGain
     * @param player
     * @param thisMove
     * @param resetFrom
     * @return  
     */
    public static boolean fastFallExemptions(final double yDistance, final double yDistDiffEx, 
                                             final PlayerMoveData lastMove, final MovingData data,
                                             final PlayerLocation from, final PlayerLocation to,
                                             final long now, final boolean strictVdistRel, final double yDistChange,
                                             final boolean resetTo, final boolean fromOnGround, final boolean toOnGround,
                                             final double maxJumpGain, final Player player, 
                                             final PlayerMoveData thisMove, final boolean resetFrom) {

        if (data.fireworksBoostDuration > 0 && data.keepfrictiontick < 0 
            && lastMove.toIsValid && yDistance - lastMove.yDistance > -0.7) {
            data.keepfrictiontick = 0;
            return true;
            // Early return: transition from CreativeFly to SurvivalFly having been in a gliding phase.
        }
            
        return 

                // 0: Moving onto ground allows a shorter move. 1
                (resetTo && (yDistDiffEx > -Magic.GRAVITY_SPAN || !fromOnGround && !thisMove.touchedGround && yDistChange >= 0.0))
                && data.ws.use(WRPT.W_M_SF_FASTFALL_2)
                // 0: Mirrored case for yDistance > vAllowedDistance, hitting ground. 2
                // TODO: Needs more efficient structure.
                || yDistance > lastMove.yDistance - Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN && (resetTo || thisMove.touchedGround)
                // && thisMove.setBackYDistance <= 0.0 // Only allow the move if the player had actually been falling
                && data.ws.use(WRPT.W_M_SF_FASTFALL_3)
                // 0: Stairs and other cases moving off ground or ground-to-ground. 3
                // TODO: Margins !?
                || (resetFrom && yDistance >= -0.5 && (yDistance > -0.31 || (resetTo || to.isAboveStairs()) && (lastMove.yDistance < 0.0)))
                && data.ws.use(WRPT.W_M_SF_FASTFALL_4)
                // 0: Head was blocked, thus faster decrease than expected.
                || yDistance <= 0.0 && yDistance > -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN 
                && (thisMove.headObstructed || lastMove.toIsValid && lastMove.headObstructed && lastMove.yDistance >= 0.0)
                && data.ws.use(WRPT.W_M_SF_FASTFALL_5)
        ;
    }


    /**
     * Odd vertical movements yDistance >= 0.0.
     * Called after having checked for too big moves (yDistDiffEx > 0.0).
     * Doesn't require lastMove's data.
     * 
     * @param yDistance
     * @param yDistDiffEx Difference from actual yDistance to vAllowedDistance
     * @param lastMove
     * @param data
     * @param from
     * @param to
     * @param now
     * @param strictVdistRel 
     * @param maxJumpGain
     * @param vAllowedDistance
     * @param player
     * @param thisMove
     * @return 
     */
    public static boolean shortMoveExemptions(final double yDistance, final double yDistDiffEx, 
                                              final PlayerMoveData lastMove, final MovingData data,
                                              final PlayerLocation from, final PlayerLocation to,
                                              final long now, final boolean strictVdistRel,
                                              final double maxJumpGain, double vAllowedDistance, 
                                              final Player player, final PlayerMoveData thisMove) {

        if (data.fireworksBoostDuration > 0 
            && data.keepfrictiontick < 0 && lastMove.toIsValid) {
            data.keepfrictiontick = 0;
            return true;
            // Early return: transition from CreativeFly to SurvivalFly having been in a gliding phase.
        }

        return 
                // 0: Allow jumping less high, unless within "strict envelope". 4
                // TODO: Extreme anti-jump effects, perhaps.
                (!strictVdistRel || Math.abs(yDistDiffEx) <= Magic.GRAVITY_SPAN || vAllowedDistance <= 0.2)
                && data.ws.use(WRPT.W_M_SF_SHORTMOVE_1)
                // 0: Too strong decrease with velocity.
                // TODO: Observed when moving off water, might be confined by that.
                || yDistance > 0.0 && lastMove.toIsValid && lastMove.yDistance > yDistance
                && lastMove.yDistance - yDistance <= lastMove.yDistance / (lastMove.from.inLiquid ? 1.76 : 4.0)
                && data.isVelocityJumpPhase()
                && data.ws.use(WRPT.W_M_SF_SHORTMOVE_2)
                // 0: Head is blocked, thus a shorter move.
                || (thisMove.headObstructed || lastMove.toIsValid && lastMove.headObstructed && lastMove.yDistance >= 0.0)
                && data.ws.use(WRPT.W_M_SF_SHORTMOVE_3)
                // 0: Allow too strong decrease
                || thisMove.yDistance < 1.0 && thisMove.yDistance > 0.9 
                && lastMove.yDistance >= 1.5 && data.sfJumpPhase <= 2
                && lastMove.verVelUsed != null 
                && (lastMove.verVelUsed.flags & (VelocityFlags.ORIGIN_BLOCK_MOVE | VelocityFlags.ORIGIN_BLOCK_BOUNCE)) != 0
                && data.ws.use(WRPT.W_M_SF_SHORTMOVE_4)
        ;
        
    }


    /**
     * Odd vertical movements with yDistDiffEx having returned a positive value (yDistance is bigger than expected.)
     * Checked first.
     * Needs lastMove's data.
     * 
     * @param yDistance
     * @param yDistDiffEx Difference from actual yDistance to vAllowedDistance
     * @param lastMove
     * @param data
     * @param from
     * @param to
     * @param now
     * @param yDistChange Change seen from the last yDistance. Double.MAX_VALUE if lastMove is not valid.
     * @param maxJumpGain
     * @param player
     * @param thisMove
     * @param resetTo
     * @return 
     */
    public static boolean outOfEnvelopeExemptions(final double yDistance, final double yDistDiffEx, 
                                                  final PlayerMoveData lastMove, final MovingData data,
                                                  final PlayerLocation from, final PlayerLocation to,
                                                  final long now, final double yDistChange, 
                                                  final double maxJumpGain, final Player player,
                                                  final PlayerMoveData thisMove, final boolean resetTo) {

        if (!lastMove.toIsValid) {
            return false;
            // Skip everything if last move is invalid
        }

        if (yDistance > 0.0 && lastMove.yDistance < 0.0 
            && AirWorkarounds.oddBounce(to, yDistance, lastMove, data)
            && data.ws.use(WRPT.W_M_SF_SLIME_JP_2X0)) {
            data.setFrictionJumpPhase();
            return true;
            // Odd slime bounce: set friction and return.
        }

        if (data.keepfrictiontick < 0) {
            if (lastMove.toIsValid) {
                if (yDistance < 0.4 && lastMove.yDistance == yDistance) {
                    data.keepfrictiontick = 0;
                    data.setFrictionJumpPhase();
                }
            } 
            else data.keepfrictiontick = 0;
            return true;
            // Early return: transition from CreativeFly to SurvivalFly having been in a gliding phase.
        }

        return
                
                // 0: Pretty coarse workaround, should instead do a proper modeling for from.getDistanceToGround.
                // (OR loc... needs different model, distanceToGround, proper set back, moveHitGround)
                // TODO: Slightly too short move onto the same level as snow (0.75), but into air (yDistance > -0.5).
                // TODO: Better on-ground model (adapt to actual client code).
                yDistance < 0.0 && lastMove.yDistance < 0.0 && yDistChange > -Magic.GRAVITY_MAX
                && (
                    from.isOnGround(Math.abs(yDistance) + 0.001) 
                    || BlockProperties.isLiquid(to.getTypeId(to.getBlockX(), Location.locToBlock(to.getY() - 0.5), to.getBlockZ()))
                )
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_1)
                // 0: Special jump (water/edges/assume-ground), too small decrease.
                || yDistDiffEx < Magic.GRAVITY_MIN / 2.0 && data.sfJumpPhase == 1 
                && to.getY() - data.getSetBackY() <= data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                && lastMove.yDistance <= maxJumpGain && yDistance > -Magic.GRAVITY_MAX && yDistance < lastMove.yDistance
                && lastMove.yDistance - yDistance > Magic.GRAVITY_ODD / 3.0
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_2) 
                // 0: On (noob) tower up, the second move has a higher distance than expected, because the first had been starting slightly above the top.
                || yDistDiffEx < Magic.Y_ON_GROUND_DEFAULT && Magic.noobJumpsOffTower(yDistance, maxJumpGain, thisMove, lastMove, data)
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_3)
        ;
    }
    

   /**
    * Conditions for exemption from the VDistSB check (Vertical distance to set back)
    *
    * @param fromOnGround
    * @param thisMove
    * @param lastMove
    * @param data
    * @param cc
    * @param tags
    * @param now
    * @param player
    * @param totalVDistViolation
    * @return true, if to skip this subcheck
    */
    public static boolean vDistSBExemptions(final boolean toOnGround, final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                            final MovingData data, final MovingConfig cc, final long now, final Player player, 
                                            double totalVDistViolation, final double yDistance, final boolean fromOnGround,
                                            final Collection<String> tags, final PlayerLocation to, final PlayerLocation from) {
        
      
        final double SetBackYDistance = to.getY() - data.getSetBackY();

        return 
                // 0: Ignore: Legitimate step.
                (fromOnGround || thisMove.touchedGroundWorkaround || lastMove.touchedGround
                && toOnGround && yDistance <= cc.sfStepHeight)
                // 0: Teleport to in-air (PaperSpigot 1.7.10).
                // TODO: Legacy, could drop it at this point...
                || Magic.skipPaper(thisMove, lastMove, data)
                // 0: Bunnyhop into a 1-block wide waterfall to reduce vertical water friction -> ascend in water -> leave waterfall 
                // -> have two, in-air ascending air phases -> double VdistSB violation due to a too high jump, since speed wasn't reduced by enough when in water.
                || data.sfJumpPhase <= 3 && data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID
                && data.insideMediumCount < 6 && Bridge1_13.hasIsSwimming() && Magic.recentlyInWaterfall(data, 20)
                && (Magic.inAir(thisMove) || Magic.leavingWater(thisMove)) && SetBackYDistance < cc.sfStepHeight 
                && yDistance < LiftOffEnvelope.NORMAL.getMaxJumpGain(0.0) 
                // 0: Lost ground cases
                || thisMove.touchedGroundWorkaround 
                && ( 
                    // 1: Skip if the player could step up by lostground_couldstep.
                    yDistance <= cc.sfStepHeight && tags.contains("lostground_couldstep")
                    // 1: Server-sided-trapdoor-touch-miss: player lands directly onto the fence as if it were 1.0 block high
                    || yDistance < data.liftOffEnvelope.getMaxJumpGain(0.0) && tags.contains("lostground_trapfence")
                )
        ;
    }
    

    /**
     * Odd vertical movements with yDistDiffEx having returned a positive value (yDistance is bigger than expected.)
     * Checked first with outOfEnvelopeExemptions.
     * Does not require lastMove's data.
     * 
     * @param yDistance
     * @param from
     * @param to
     * @param thisMove
     * @param resetTo
     * @return 
     */
    public static boolean outOfEnvelopeNoData(final double yDistance, final PlayerLocation from, final PlayerLocation to, 
                                              final PlayerMoveData thisMove, final boolean resetTo, final MovingData data) {

        return  
                // 0: Allow falling shorter than expected, if onto ground.
                // Note resetFrom should usually mean that allowed dist is > 0 ?
                yDistance <= 0.0 && (resetTo || thisMove.touchedGround) 
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_NODATA1)
                // 0: Pre 1.17 bug.
                // TODO: Possibly confine by further criteria ?
                || to.isHeadObstructed() && yDistance > 0.0 && yDistance < 1.2 
                && MaterialUtil.SHULKER_BOXES.contains(from.getTypeId())
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_NODATA2)
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
    public static boolean oddJunction(final PlayerLocation from, final PlayerLocation to,
                                      final double yDistance, final double yDistChange, final double yDistDiffEx, 
                                      final double maxJumpGain, final boolean resetTo,
                                      final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                      final MovingData data, final MovingConfig cc, final boolean resetFrom) {
        if (!lastMove.toIsValid) {
            return false;
            // Skip everything if last move is invalid
        }
        // TODO: Cleanup/reduce signature (accept thisMove.yDistance etc.).
        if (AirWorkarounds.oddLiquid(yDistance, yDistDiffEx, maxJumpGain, resetTo, thisMove, lastMove, data, resetFrom, from)) {
            // Jump after leaving the liquid near ground.
            return true;
        }
        if (AirWorkarounds.oddGravity(from, to, yDistance, yDistChange, yDistDiffEx, thisMove, lastMove, data)) {
            // Starting to fall / gravity effects.
            return true;
        }
        if ((yDistDiffEx > 0.0 || yDistance >= 0.0) && AirWorkarounds.oddSlope(to, yDistance, maxJumpGain, yDistDiffEx, lastMove, data)) {
            // Odd decrease after lift-off.
            return true;
        }
        if (AirWorkarounds.oddFriction(yDistance, yDistDiffEx, lastMove, data, from)) {
            // Odd behavior with moving up or (slightly) down, accounting for more than one past move.
            return true;
        }
        return false;
    }
}