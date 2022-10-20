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
package fr.neatmonster.nocheatplus.checks.net;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;

/**
 * Misc. checks related to flying packets.
 * Currently only has a simplistic check for extreme moves.
 */
public class Moving extends Check {

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    final Location useLoc = new Location(null, 0, 0, 0);
    final List<String> tags = new ArrayList<String>();
    
    public Moving() {
        super(CheckType.NET_MOVING);
    }

    /**
     * Checks a player
     * 
     * @param player
     * @param packetData
     * @param data
     * @param cc
     * @param pData
     * @param cc
     * @return True, if to cancel the event
     */
    public boolean check(final Player player, final DataPacketFlying packetData, final NetData data, final NetConfig cc, 
                         final IPlayerData pData, final Plugin plugin) {
        // TODO: This will trigger if the client is waiting for chunks to load (Slow fall, on join or after a teleport)
        boolean cancel = false;
        final long now = System.currentTimeMillis();
        final boolean debug = pData.isDebugActive(CheckType.NET_MOVING);
        tags.clear();
        if (now > pData.getLastJoinTime() && pData.getLastJoinTime() + 10000 > now) {
            tags.add("login_grace");
        	return false;
        }
        if (packetData != null && packetData.hasPos) {
            final MovingData mData = pData.getGenericInstance(MovingData.class);
            /** Actual Location on the server */
            final Location knownLocation = player.getLocation(useLoc);
            /** Claimed Location sent by the client */
            final Location packetLocation = new Location(null, packetData.getX(), packetData.getY(), packetData.getZ());
            final double hDistanceDiff = TrigUtil.distance(knownLocation, packetLocation);
            final double yDistanceDiff = Math.abs(knownLocation.getY() - packetLocation.getY());

            if (yDistanceDiff > 100.0 || hDistanceDiff > 100.0) {
                data.movingVL++ ;
                tags.add("invalid_pos");
                final ViolationData vd = new ViolationData(this, player, data.movingVL, 1.0, cc.movingActions);
                if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
                cancel = executeActions(vd).willCancel();
            }
            else {
                data.movingVL *= 0.98;
            }

            // Request a setback
            if (cancel) {
                // Player might be freezed by canceling, set back might turn it to normal
                int task = -1;
                task = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    /** Use the first valid setback location that we can get a hold of */
                    final Location newTo = mData.hasSetBack() ? mData.getSetBack(knownLocation) :
                                           mData.hasMorePacketsSetBack() ? mData.getMorePacketsSetBack() :
                                           knownLocation;
                                           //null;
                    // Unsafe position. Null world or world not updated world
                    if (newTo == null) {
                        // (Kick the player due to crash exploit potential)
                        StaticLog.logSevere("[NoCheatPlus] Could not restore location for " + player.getName() + ", kicking them.");
                        CheckUtils.kickIllegalMove(player, pData.getGenericInstance(MovingConfig.class));
                    } 
                    else {
                        // Mask player teleport as a set back.
                        mData.prepareSetBack(newTo);
                        player.teleport(LocUtil.clone(newTo), BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
                        // Request an Improbable update, unlikely that this is legit.
                        TickTask.requestImprobableUpdate(player.getUniqueId(), 1.0f);
                        if (debug) {
                            debug(player, "Set back player: " + player.getName() + ":" + LocUtil.simpleFormat(newTo));
                        }
                    }
                });
                if (task == -1) {
                    StaticLog.logWarning("[NoCheatPlus] Failed to schedule task for player: " + player.getName());
                }
                mData.resetTeleported(); // Cleanup, just in case.
            }
            // Cleanup
            useLoc.setWorld(null);
        }

        if (debug) {
            final Location packetLocation = new Location(null, packetData.getX(), packetData.getY(), packetData.getZ());
            final StringBuilder builder = new StringBuilder(500);
            if (packetData.hasPos) {
                builder.append(CheckUtils.getLogMessagePrefix(player, type));
                builder.append("\nPacket location: " + LocUtil.simpleFormat(packetLocation));
                builder.append("\nServer location: " + LocUtil.simpleFormat(player.getLocation(useLoc)));
                builder.append("\nDeltas: h= " + TrigUtil.distance(player.getLocation(useLoc), packetLocation) + ", y= " + Math.abs(player.getLocation(useLoc).getY() - packetLocation.getY()));
            }
            else {
            	builder.append("Empty packet (no position)");
            }
            useLoc.setWorld(null);
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, builder.toString());
        }
        return cancel;
    }
}