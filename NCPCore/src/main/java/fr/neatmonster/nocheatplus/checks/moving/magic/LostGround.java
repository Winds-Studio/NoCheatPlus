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
import org.bukkit.World;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;



/**
 * Lost ground workarounds.
 * @See <a href="https://bugs.mojang.com/browse/MC-90024">Mojang's issue tracker</a> 
 * 
 * @author asofold
 *
 */
public class LostGround {


    /**
     * Check if touching the ground was lost (client did not send / server did not put it through / false negatives on NCP's side).
     * @param player
     * @param from
     * @param to
     * @param hDistance
     * @param yDistance
     * @param sprinting
     * @param data
     * @param cc
     * @param useBlockChangeTracker 
     * @return If touching the ground was lost.
     */
    public static boolean lostGround(final Player player, final PlayerLocation from, final PlayerLocation to, 
                                     final double hDistance, final double yDistance, final boolean sprinting, 
                                     final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc, 
                                     final BlockChangeTracker blockChangeTracker, final Collection<String> tags) {
        // TODO: Regroup with appropriate conditions (toOnGround first?).
        // TODO: yDistance limit does not seem to be appropriate.
        // Temporary let it here
        // TODO: Remove :)
        data.snowFix = (from.getBlockFlags() & BlockFlags.F_HEIGHT_8_INC) != 0;
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        if (yDistance >= -0.7 && yDistance <= Math.max(cc.sfStepHeight, LiftOffEnvelope.NORMAL.getMaxJumpGain(data.jumpAmplifier) + 0.174)) { // Where does this magic come from!?

            // Ascending
            if (yDistance >= 0.0 && lastMove.toIsValid) {
                if (lostGroundAscend(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags)) {
                    return true;
                }
            }

            // Descending.
            // Check for sprinting down blocks.
            if (MathUtil.between(0, data.sfJumpPhase, 7) && lastMove.toIsValid
                && thisMove.setBackYDistance < 0.0 && lastMove.setBackYDistance < 0.0
                && MathUtil.between(Magic.GRAVITY_MAX, yDistance - lastMove.yDistance, 0.2)
                && thisMove.yDistance > lastMove.yDistance && thisMove.hDistance >= 0.1
                && !to.isOnGround()) {
                if (from.isOnGround(0.6, 0.4, 0.0, 0L)) {
                    return applyLostGround(player, from, true, thisMove, data, "pyramid", tags);
               }
            }
        }

        // Block change tracker (kept extra for now).
        if (blockChangeTracker != null && lostGroundPastState(player, from, to, data, cc, blockChangeTracker, tags)) {
            return true;
        }
        // Nothing found.
        return false;
    }


    private static boolean lostGroundPastState(final Player player, final PlayerLocation from, final PlayerLocation to, 
                                               final MovingData data, final MovingConfig cc, final BlockChangeTracker blockChangeTracker, 
                                               final Collection<String> tags) {
        // TODO: Heuristics.
        // TODO: full y-move at from-xz (!).
        final int tick = TickTask.getTick();
        if (from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick)) {
            // NOTE: Not sure with setBackSafe here (could set back a hundred blocks on parkour).
            return applyLostGround(player, from, false, data.playerMoves.getCurrentMove(), data, "past", tags);
        }
        return false;
    }


    /**
     * Check if a ground-touch has been lost due to event-sending-frequency or other reasons.<br>
     * This is for ascending only (yDistance >= 0). Needs last move data.
     * @param player
     * @param from
     * @param loc 
     * @param to
     * @param hDistance
     * @param yDistance
     * @param sprinting
     * @param data
     * @param cc
     * @return
     */
    private static boolean lostGroundAscend(final Player player, final PlayerLocation from, final PlayerLocation to, final double hDistance, final double yDistance, 
                                            final boolean sprinting, final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc, final Collection<String> tags) {

        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final double setBackYDistance = from.getY() - data.getSetBackY();
        final double yDistChange = thisMove.yDistance - lastMove.yDistance;
        final double setBackYMargin = data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier) - setBackYDistance;


        // Micro lost ground, appear when respawn, lantern
        // TODO: hDistance is to confine, need to test
        if (hDistance <= 0.03 && from.isOnGround(0.03) 
            && (thisMove.headObstructed && MaterialUtil.LANTERNS.contains(from.getTypeIdAbove()) || data.joinOrRespawn)) {
            return applyLostGround(player, from, true, thisMove , data, "micro", tags);
        }

        // Step height related cases.
        if (yDistance <= cc.sfStepHeight && hDistance <= 1.5) { // hDistance is arbitrary, just to confine.
            
            // With positive margin
            if (setBackYMargin >= 0.0) {
                // Half block step up (definitive).
                // Also, why is this even a "lost" ground case if the player has moved onto ground!?
                // if (to.isOnGround() && setBackYMargin >= yDistance && hDistance <= thisMove.hAllowedDistance) {
                //    if (lastMove.yDistance < 0.0 || from.isOnGround(cc.sfStepHeight - yDistance)) {
                //        return applyLostGround(player, from, true, thisMove, data, "step", tags);
                //    }
                //}

                // Check for sprint-jumping on fences with trapdoors above (missing trapdoor's edge touch on server-side, player lands directly onto the fence)
                // This is rather a false negative: NCP's collision differs from MC's; NCP won't detect this specific collision while MC does.
                // [Tested in 1.18: Still needed although it would be best if this could be fixed in BlockProperties, rather than resorting to a workaround]
                if (setBackYDistance > 1.0 && setBackYDistance <= 1.5 
                    && setBackYMargin < 0.6 && data.bunnyhopDelay > 0 
                    && yDistance > from.getyOnGround() && lastMove.yDistance <= Magic.GRAVITY_MAX
                    && (yDistance < Magic.GRAVITY_MIN || yDistance <= data.liftOffEnvelope.getMaxJumpGain(0.0) / 1.52)) {

                    to.collectBlockFlags();
                    // (Doesn't seem to be a problem with carpets)
                    if ((to.getBlockFlags() & BlockFlags.F_ATTACHED_LOW2_SNEW) != 0
                        && (to.getBlockFlags() & BlockFlags.F_HEIGHT150) != 0) {
                        
                        // Missing the trapdoor by 0.009
                        // No need to add another horizontal margin because the box already covers the block, just not vertically.
                        if (to.isOnGround(0.009, 0.0, 0.0)) {
                            // Setbacksafe: matter of taste.
                            // With false, in case of a cheating attempt, the player will be setbacked on the ground instead of the trapdoor.
                            return applyLostGround(player, from, false, thisMove, data, "trapfence", tags);
                        }
                    }
                }

                // Noob tower (moving up placing blocks underneath). Rather since 1.9: player jumps off with 0.4 speed but ground within 0.42.
                // TODO: Confine by actually having placed a block nearby.
                final double maxJumpGain = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);
                if (maxJumpGain > yDistance && MathUtil.inRange(0, data.sfJumpPhase, 7)
                    && (
                        // 0: Typical: distance to ground + yDistance roughly covers maxJumpGain.
                        yDistance > 0.0 && lastMove.yDistance < 0.0 // Rather -0.15 or so.
                        && Math.abs(lastMove.yDistance) + Magic.GRAVITY_MAX + yDistance > cc.yOnGround + maxJumpGain 
                        && from.isOnGround(0.025)
                        // 0: Rather rare: Come to rest above the block. Multiple 0-dist moves with looking packets.
                        // Not sure this happens with hdist > 0 at all.
                        || lastMove.yDistance == 0.0 && noobTowerStillCommon(to, yDistance)
                    )) {
                    // TODO: Ensure set back is slightly lower, if still on ground.
                    // setBackSafe: false to prevent a lowjump due to the setback reset.
                    return applyLostGround(player, from, false, thisMove, data, "nbtwr", tags);
                }
            }

            // Could step up (but might move to another direction, potentially).
            if (lastMove.yDistance < 0.0 && !lastMove.touchedGroundWorkaround && hDistance > 0.0001) { 
                // Generic could step.
                // TODO: Possibly confine margin depending on side, moving direction (see client code).
                if (from.isOnGround(1.0) && thisMove.hDistance <= thisMove.hAllowedDistance * 1.1
                    && BlockProperties.isOnGroundShuffled(to.getBlockCache(), from.getX(), from.getY() + cc.sfStepHeight, from.getZ(), to.getX(), 
                                                          to.getY(), to.getZ(), 0.1 + from.getBoxMarginHorizontal(), to.getyOnGround(), 0.0)) {
                    return applyLostGround(player, from, false, thisMove, data, "couldstep", tags);
                }
                // Close by ground miss (client side blocks y move, but allows h move fully/mostly, missing the edge on server side).
                // Possibly confine by more criteria.
                if (!to.isOnGround()) {
                    // (Use covered area to last from.)
                    if (
                        // (thisMove.yDistance == MathUtil.negate(lastMove.yDistance) || MathUtil.between(0.0, yDistChange, Magic.GRAVITY_SPAN - to.getyOnGround())) 
                        lostGroundEdgeAsc(player, from.getBlockCache(), from.getWorld(), from.getX(), from.getY(), from.getZ(), from.getBoxMarginHorizontal(), 
                                             from.getyOnGround(), lastMove, data, "asc1", tags, from.getMCAccess())) {
                        return true;
                    }
                    // Special cases: similar to couldstep, with 0 y-distance but slightly above any ground nearby (no micro move!).
                    if (yDistance == 0.0 && lastMove.yDistance <= -0.1515 
                        && (hDistance <= lastMove.hDistance * 1.1) && thisMove.multiMoveCount == 0) {
                        // TODO: Confining in x/z direction in general: should detect if collided in that direction (then skip the x/z dist <= last time).
                        /*
                         * xzMargin 0.15: equipped end portal frame (observed
                         * and supposedly fixed on MC 1.12.2) - might use an
                         * even lower tolerance value here, once there is time
                         * to testing this.
                         */
                        final double xzMargin = lastMove.yDistance <= -0.23 ? 0.3 : 0.15;
                        if (lostGroundEdgeAsc(player, from.getBlockCache(), to.getWorld(), to.getX(), to.getY(), 
                                              to.getZ(), from.getX(), from.getY(), from.getZ(), 
                                              hDistance, to.getBoxMarginHorizontal(), xzMargin, 
                                              data, "asc2", tags, from.getMCAccess())) {
                            return true;
                        }
                    }
                    // (Minimal margin.)
                    else if (from.isOnGround(from.getyOnGround(), 0.0625, 0.0)) {
                        return applyLostGround(player, from, false, thisMove, data, "asc3", tags); 
                    }
                }
            }
        }
        // Nothing found.
        return false;
    }


    /**
     * Common conditions for noob tower without y distance taken (likely also no
     * hdist).
     * 
     * @param to
     * @param yDistance
     * @return
     */
    private static boolean noobTowerStillCommon(final PlayerLocation to, final double yDistance) {
        // TODO: Block recently placed underneath (xz box with 0.025 down, Direction.NONE).
        return yDistance < 0.025 && to.getY() - to.getBlockY() < 0.025
               && to.isOnGround(0.025, BridgeMisc.hasLeatherBootsOn(to.getPlayer()) ? 0 : BlockFlags.F_POWDERSNOW);
    }


    /**
     * Preconditions move dist is 0, not on ground, last h dist > 0, last y dist
     * < 0. Needs last move data.
     * 
     * @param player
     * @param from
     * @param loc
     * @param to
     * @param hDistance
     * @param yDistance
     * @param sprinting
     * @param data
     * @param cc
     * @return
     */
    public static boolean lostGroundStill(final Player player, final PlayerLocation from, final PlayerLocation to, 
                                          final double hDistance, final double yDistance, final boolean sprinting, 
                                          final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc, 
                                          final Collection<String> tags) {
        if ((lastMove.yDistance == 0.0 && lastMove.touchedGround || lastMove.yDistance < 0.0)
            && data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier) > yDistance
            && noobTowerStillCommon(to, yDistance)) {
            // TODO: Ensure set back is slightly lower, if still on ground.
            final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
            // setBackSafe: false to prevent a lowjump due to the setback reset.
            return applyLostGround(player, from, false, thisMove, data, "nbtwrstill", tags);
        }
        return false;
    }


    /**
     * Vertical collision with ground on client side, shifting over an edge with
     * the horizontal move. Needs last move data.
     * 
     * @param player
     * @param blockCache
     * @param world
     * @param x1
     *            Target position.
     * @param y1
     * @param z1
     * @param boxMarginHorizontal
     *            Center to edge, at some resolution.
     * @param yOnGround
     * @param data
     * @param tag
     * @return
     */
    private static boolean lostGroundEdgeAsc(final Player player, final BlockCache blockCache, final World world, final double x1, final double y1, 
                                             final double z1, final double boxMarginHorizontal, final double yOnGround, 
                                             final PlayerMoveData lastMove, final MovingData data, final String tag, final Collection<String> tags, 
                                             final MCAccess mcAccess) {
        return lostGroundEdgeAsc(player, blockCache, world, x1, y1, z1, lastMove.from.getX(), lastMove.from.getY(), lastMove.from.getZ(), lastMove.hDistance, boxMarginHorizontal, yOnGround, data, tag, tags, mcAccess);
    }


    /**
     * Vertical collision with ground on client side, shifting over an edge with
     * the horizontal move. Needs last move data.
     * @See <a href="https://i.gyazo.com/644b9a70b60902ad780a325602e2d3ed.png">
     * This screenshot (second box, to the right, near crosshair position) for a visual representation of what's happening. </a> 
     * 
     * @param player
     * @param blockCache
     * @param world
     * @param x1
     * @param y1
     * @param z1
     * @param x2
     * @param y2
     * @param z2
     * @param hDistance2
     * @param boxMarginHorizontal
     *            Center to edge, at some resolution.
     * @param yOnGround
     * @param data
     * @param tag
     * @param tags
     * @param mcAccess
     * @return
     */
    private static boolean lostGroundEdgeAsc(final Player player, final BlockCache blockCache, final World world, 
                                             final double x1, final double y1, final double z1, double x2, final double y2, double z2, 
                                             final double hDistance2, final double boxMarginHorizontal, final double yOnGround, 
                                             final MovingData data, final String tag, final Collection<String> tags, final MCAccess mcAccess) {

        // First: calculate vector towards last from.
        x2 -= x1;
        z2 -= z1;

        // Second: cap the size of the extra box (at least horizontal).
        double fMin = 1.0; // Factor for capping.
        if (Math.abs(x2) > hDistance2) {
            fMin = Math.min(fMin, hDistance2 / Math.abs(x2));
        }
        if (Math.abs(z2) > hDistance2) {
            fMin = Math.min(fMin, hDistance2 / Math.abs(z2));
        }

        // TODO: Further / more precise ?
        // Third: calculate end points.
        x2 = fMin * x2 + x1;
        z2 = fMin * z2 + z1;

        // Finally test for ground.
        // (We don't add another xz-margin here, as the move should cover ground.)
        if (BlockProperties.isOnGroundShuffled(blockCache, x1, y1, z1, x2, y1 + (data.snowFix ? 0.125 : 0.0), z2, boxMarginHorizontal + (data.snowFix ? 0.1 : 0.0), yOnGround, 0.0)) {
            // TODO: data.fromY for set back is not correct, but currently it is more safe (needs instead: maintain a "distance to ground").
            return applyLostGround(player, new Location(world, x2, y2, z2), true, data.playerMoves.getCurrentMove(), data, "edge" + tag, tags, mcAccess);
        } 
        return false;
    }


    /**
     * Apply lost-ground workaround.
     * @param player
     * @param refLoc
     * @param setBackSafe If to use the given location as set back location.
     * @param data
     * @param tag Added to "lostground_" as tag.
     * @return Always true.
     */
    private static boolean applyLostGround(final Player player, final Location refLoc, final boolean setBackSafe, final PlayerMoveData thisMove, final MovingData data, final String tag, final Collection<String> tags, final MCAccess mcAccess) {
        if (setBackSafe) {
            data.setSetBack(refLoc);
        }
        else {
            // Keep Set back.
        }
        return applyLostGround(player, thisMove, data, tag, tags, mcAccess);
    }


    /**
     * Apply lost-ground workaround.
     * @param player
     * @param refLoc
     * @param setBackSafe If to use the given location as set back.
     * @param data
     * @param tag Added to "lostground_" as tag.
     * @return Always true.
     */
    private static boolean applyLostGround(final Player player, final PlayerLocation refLoc, final boolean setBackSafe, final PlayerMoveData thisMove, final MovingData data, final String tag, final Collection<String> tags) {
        // Set the new setBack and reset the jumpPhase.
        if (setBackSafe) {
            data.setSetBack(refLoc);
        }
        else {
            // Keep Set back.
        }
        return applyLostGround(player, thisMove, data, tag, tags, refLoc.getMCAccess());
    }


    /**
     * Apply lost-ground workaround (data adjustments and tag).
     * @param player
     * @param refLoc
     * @param setBackSafe If to use the given location as set back.
     * @param data
     * @param tag Added to "lostground_" as tag.
     * @return Always true.
     */
    private static boolean applyLostGround(final Player player, final PlayerMoveData thisMove, final MovingData data, final String tag, final Collection<String> tags, final MCAccess mcAccess) {
        // Reset the jumpPhase.
        // ? set jumpphase to 1 / other, depending on stuff ?
        data.sfJumpPhase = 0;
        // Update the jump amplifier because we assume the player to be able to jump here.
        data.jumpAmplifier = MovingUtil.getJumpAmplifier(player, mcAccess);
        // Clear data for the (old) gravity check
        data.clearAccounting();
        // Update speed factors the the speed estimation.
        data.adjustMediumProperties(player.getLocation(), DataManager.getPlayerData(player).getGenericInstance(MovingConfig.class), player, data.playerMoves.getCurrentMove());
        // Tell NoFall that we assume the player to have been on ground somehow.
        thisMove.touchedGround = true;
        thisMove.touchedGroundWorkaround = true;
        tags.add("lostground_" + tag);
        Improbable.update(player, System.currentTimeMillis());
        return true;
    }
}
