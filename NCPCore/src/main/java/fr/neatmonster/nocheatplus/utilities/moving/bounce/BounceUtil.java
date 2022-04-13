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
package fr.neatmonster.nocheatplus.utilities.moving.bounce;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.BlockChangeEntry;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.Direction;
import fr.neatmonster.nocheatplus.components.debug.IDebugPlayer;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;


/**
 * Auxiliary methods for bounce effect
 * @author asofold 
 * 
 */
public class BounceUtil {

    private static final long FLAGS_VELOCITY_BOUNCE_BLOCK = VelocityFlags.ORIGIN_BLOCK_BOUNCE;
    private static final long FLAGS_VELOCITY_BOUNCE_BLOCK_MOVE_ASCEND = FLAGS_VELOCITY_BOUNCE_BLOCK | VelocityFlags.SPLIT_ABOVE_0_42 
                                                                        | VelocityFlags.SPLIT_RETAIN_ACTCOUNT | VelocityFlags.ORIGIN_BLOCK_MOVE;

    /**
     * Adjust data to allow bouncing back and/or removing fall damage.<br>
     * yDistance is < 0, the middle of the player is above a slime block (to) +
     * on ground. This might be a micro-move onto ground.
     * 
     * @param player
     * @param verticalBounce 
     * @param from
     * @param to
     * @param data
     * @param cc
     */
    public static void processBounce(final Player player,final double fromY, final double toY, final BounceType bounceType, final int tick, final IDebugPlayer idp,
                                     final MovingData data, final MovingConfig cc, final IPlayerData pData) {

        // Prepare velocity.
        final double fallDistance = MovingUtil.getRealisticFallDistance(player, fromY, toY, data, pData);
        final double base =  Math.sqrt(fallDistance) / 3.3;
        double effect = Math.min(Magic.BOUNCE_VERTICAL_MAX_DIST, base + Math.min(base / 10.0, Magic.GRAVITY_MAX)); // Ancient Greek technology with gravity added.
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final boolean debug = pData.isDebugActive(CheckType.MOVING);

        if (effect > 0.415 && lastMove.toIsValid) {
            // Extra cap by last y distance(s).
            final double max_gain = Math.abs(lastMove.yDistance < 0.0 ? Math.min(lastMove.yDistance, toY - fromY) : (toY - fromY)) - Magic.GRAVITY_SPAN;
            if (max_gain < effect) {
                effect = max_gain;
                if (debug) idp.debug(player, "Cap bounce effect by recent y-distances.");
            }
        }
        if (bounceType == BounceType.STATIC_PAST_AND_PUSH) {
            /*
             * TODO: Find out if relevant and handle here (still use maximum
             * cap, but not by y-distance.). Could be the push part is only
             * necessary if the player is pushed upwards without prepared
             * bounce.
             */
        }
        // (Actually observed max. is near 3.5.) TODO: Why 3.14 then?
        if (debug) idp.debug(player, "Set bounce effect (dY=" + fallDistance + " / " + bounceType + "): " + effect); 
        data.noFallSkipAirCheck = true;
        data.verticalBounce = new SimpleEntry(tick, effect, FLAGS_VELOCITY_BOUNCE_BLOCK, 1); // Just bounce for now.
    }


    /**
     * Handle a prepare bounce.
     * 
     * @param player
     * @param from
     * @param to
     * @param lastMove
     * @param tick
     * @param data
     * @return True, if bounce has been used, i.e. to do without fall damage.
     */
    public static boolean onPreparedBounceSupport(final Player player, final Location from, final Location to, 
                                                  final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                                  final int tick, final MovingData data) {

        if (to.getY() > from.getY() || to.getY() == from.getY() && data.verticalBounce.value < 0.13) {
            // Apply bounce.
            if (to.getY() == from.getY()) {
                // Fake use velocity here.
                data.prependVerticalVelocity(new SimpleEntry(tick, 0.0, 1));
                data.getOrUseVerticalVelocity(0.0);
                if (lastMove.toIsValid && lastMove.yDistance < 0.0) {
                    // Renew the bounce effect.
                    data.verticalBounce = new SimpleEntry(tick, data.verticalBounce.value, 1);
                }
            }
            else data.useVerticalBounce(player);
            return true;
            // TODO: Find % of verticalBounce.value or abs. value for X: yDistance > 0, deviation from effect < X -> set sfNoLowJump
        }
        else {
            data.verticalBounce = null;
            return false;
        }
    }


    /**
     * Only for yDistance < 0 + some bounce envelope checked.
     * @param player
     * @param from
     * @param to
     * @param lastMove
     * @param lastMove
     * @param tick
     * @param data
     * @param cc
     * @return
     */
    public static BounceType checkPastStateBounceDescend(final Player player, final PlayerLocation from, final PlayerLocation to,
                                                         final PlayerMoveData thisMove, final PlayerMoveData lastMove, final int tick, 
                                                         final MovingData data, final MovingConfig cc, BlockChangeTracker blockChangeTracker) {

        // TODO: Find more preconditions.
        // TODO: Might later need to override/adapt just the bounce effect set by the ordinary method.
        final UUID worldId = from.getWorld().getUID();
        // Prepare (normal/extra) bounce.
        // Typical: a slime block has been there.
        final BlockChangeEntry entryBelowAny = blockChangeTracker.getBlockChangeEntryMatchFlags(data.blockChangeRef, tick, worldId, to.getBlockX(), to.getBlockY() - 1, to.getBlockZ(), null, BlockFlags.F_BOUNCE25);
        if (entryBelowAny != null) {
            // TODO: Check preconditions for bouncing here at all (!).
            // Check if the/a block below the feet of the player got pushed into the feet of the player.
            final BlockChangeEntry entryBelowY_POS = entryBelowAny.direction == Direction.Y_POS ? entryBelowAny 
                                                    : blockChangeTracker.getBlockChangeEntryMatchFlags(data.blockChangeRef, tick, worldId, to.getBlockX(), to.getBlockY() - 1, to.getBlockZ(), Direction.Y_POS, BlockFlags.F_BOUNCE25);
            if (entryBelowY_POS != null) {
                // TODO: Can't know if used... data.blockChangeRef.updateSpan(entryBelowY_POS);
                // TODO: So far, doesn't seem to be followed by violations.
                return BounceType.STATIC_PAST_AND_PUSH;
            }
            // TODO: Can't know if used... data.blockChangeRef.updateSpan(entryBelowAny);
            else return BounceType.STATIC_PAST;
        }
        /*
         * TODO: Can't update span here. If at all, it can be added as side
         * condition for using the bounce effect. Probably not worth it.
         */
        return BounceType.NO_BOUNCE; // Nothing found, return no bounce.
    }
    

    /**
     * Only for yDistance > 0 + some bounce envelope checked.
     * @param player
     * @param from
     * @param to
     * @param lastMove
     * @param lastMove
     * @param tick
     * @param data
     * @param cc
     * @return
     */
    public static BounceType checkPastStateBounceAscend(final Player player, final PlayerLocation from, final PlayerLocation to,
                                                        final PlayerMoveData thisMove, final PlayerMoveData lastMove, final int tick, final IPlayerData pData, 
                                                        final IDebugPlayer idp, final MovingData data, final MovingConfig cc, BlockChangeTracker blockChangeTracker) {

        // TODO: More preconditions.
        // TODO: Nail down to more precise side conditions for larger jumps, if possible.
        final UUID worldId = from.getWorld().getUID();
        final boolean debug = pData.isDebugActive(CheckType.MOVING);
        // Possibly a "lost use of slime".
        // TODO: Might need to cover push up, after ordinary slime bounce.
        // TODO: Work around 0-dist?
        // TODO: Adjust amount based on side conditions (center push or off center, distance to block top).
        double amount = -1.0;
        final BlockChangeEntry entryBelowY_POS = blockChangeTracker.getBlockChangeEntryMatchFlags(data.blockChangeRef, tick, worldId, from.getBlockX(), from.getBlockY() - 1, from.getBlockZ(), Direction.Y_POS, BlockFlags.F_BOUNCE25);

        if (
                // Center push.
                entryBelowY_POS != null
                // Off center push.
                || thisMove.yDistance < 1.515 && from.matchBlockChangeMatchResultingFlags(blockChangeTracker, data.blockChangeRef, Direction.Y_POS, Math.min(.415, thisMove.yDistance), BlockFlags.F_BOUNCE25)
            ) {

            amount = Math.min(Math.max(0.505, 1.0 + (double) from.getBlockY() - from.getY() + 1.515), 1.915); // Old: 2.525
            if (debug) {
                idp.debug(player, "Direct block push with bounce (" + (entryBelowY_POS == null ? "off_center)." : "center)."));
            }
            if (entryBelowY_POS != null) {
                data.blockChangeRef.updateSpan(entryBelowY_POS);
            }
        }

        // Center push while being on the top height of the pushed block already (or 0.5 above (!)).
        if (
                amount < 0.0
                // TODO: Not sure about y-Distance.
                && lastMove.toIsValid && lastMove.yDistance >= 0.0 && lastMove.yDistance <= 0.505
                && Math.abs(from.getY() - (double) from.getBlockY() - lastMove.yDistance) < 0.205 // from.getY() - (double) from.getBlockY() == lastMove.yDistance
            ) {

            final BlockChangeEntry entry2BelowY_POS = blockChangeTracker.getBlockChangeEntryMatchFlags(data.blockChangeRef, tick, worldId, from.getBlockX(), from.getBlockY() - 2, from.getBlockZ(), Direction.Y_POS, BlockFlags.F_BOUNCE25);
            if (entry2BelowY_POS != null) {
                // TODO: Does off center push exist with this very case?
                amount = Math.min(Math.max(0.505, 1.0 + (double) from.getBlockY() - from.getY() + 1.515),  1.915 - lastMove.yDistance); // TODO: EXACT MAGIC.
                if (debug) {
                    idp.debug(player, "Foot position block push with bounce (" + (entry2BelowY_POS == null ? "off_center)." : "center)."));
                }
                if (entryBelowY_POS != null) {
                    data.blockChangeRef.updateSpan(entry2BelowY_POS);
                }
            }
        }

        // Finally add velocity if set.
        if (amount >= 0.0) {
            /*
             * TODO: USE EXISTING velocity with bounce flag set first, then peek
             * / add. (might while peek -> has bounce flag: remove velocity)
             */
            data.removeLeadingQueuedVerticalVelocityByFlag(VelocityFlags.ORIGIN_BLOCK_BOUNCE);
            /*
             * TODO: Concepts for limiting... max amount based on side
             * conditions such as block height+1.5, max coordinate, max
             * amount per use, ALLOW_ZERO flag/boolean and set in
             * constructor, demand max. 1 zero dist during validity. Bind
             * use to initial xz coordinates... Too precise = better with
             * past move tracking, or a sub-class of SimpleEntry with better
             * access signatures including thisMove.
             */
            /*
             * TODO: Also account for current yDistance here? E.g. Add two
             * entries, split based on current yDistance?
             */
            final SimpleEntry vel = new SimpleEntry(tick, amount, FLAGS_VELOCITY_BOUNCE_BLOCK_MOVE_ASCEND, 4);
            data.verticalBounce = vel;
            data.useVerticalBounce(player);
            data.useVerticalVelocity(thisMove.yDistance);
            //if (thisMove.yDistance > 0.42) {
            //    data.setFrictionJumpPhase();
            //}
            if (debug) {
                idp.debug(player, "checkPastStateBounceAscend: set velocity: " + vel);
            }
            // TODO: Exact type to return.
            return BounceType.STATIC_PAST_AND_PUSH;
        }
        // TODO: There is a special case with 1.0 up on pistons pushing horizontal only (!).
        return BounceType.NO_BOUNCE;
    }


    /**
     * Pre conditions: A slime block is underneath and the player isn't really
     * sneaking. This does not account for pistons pushing (slime) blocks.<br>
     * 
     * @param player
     * @param from
     * @param to
     * @param data
     * @param cc
     * @return
     */
    public static boolean checkBounceEnvelope(final Player player, final PlayerLocation from, final PlayerLocation to, 
                                              final MovingData data, final MovingConfig cc, final IPlayerData pData) {
        
        // Workaround/fix for bed bouncing. getBlockY() would return an int, while a bed's maxY is 0.5625, causing this method to always return false.
        // A better way to do this would to get the maxY through another method, just can't seem to find it :/
        // Collect block flags at the current location as they may not already be there, and cause NullPointer errors.
        to.collectBlockFlags();
        double blockY = ((to.getBlockFlags() & BlockFlags.F_BOUNCE25) != 0) 
                        && ((to.getY() + 0.4375) % 1 == 0) ? to.getY() : to.getBlockY();
        return 
                // 0: Normal envelope (forestall NoFall).
                (
                    // 1: Ordinary.
                    to.getY() - blockY <= Math.max(cc.yOnGround, cc.noFallyOnGround)
                    // 1: With carpet.
                    || BlockProperties.isCarpet(to.getTypeId()) && to.getY() - to.getBlockY() <= 0.9
                )
                && MovingUtil.getRealisticFallDistance(player, from.getY(), to.getY(), data, pData) > 1.0
                // 0: Within wobble-distance.
                || to.getY() - blockY < 0.286 && to.getY() - from.getY() > -0.9
                && to.getY() - from.getY() < -Magic.GRAVITY_MIN
                && !to.isOnGround()
                ;
    }
}