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
package fr.neatmonster.nocheatplus.checks.net.protocollib;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerAction;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.net.NetConfig;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.checks.net.ToggleFrequency;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;



public class EntityActionAdapter extends BaseAdapter {
    
    private final ToggleFrequency toggleFrequency = new ToggleFrequency();
    /** Location for temporary use with getLocation(useLoc). Always call setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);
    
    private static PacketType[] initPacketTypes() {
        final List<PacketType> types = new LinkedList<PacketType>(Arrays.asList(PacketType.Play.Client.ENTITY_ACTION));
        return types.toArray(new PacketType[types.size()]);
    }

    public EntityActionAdapter(Plugin plugin) {
        super(plugin, ListenerPriority.LOW, initPacketTypes());
    }
    
    @Override
    public void onPacketReceiving(final PacketEvent event) {
        // Do some early return tests first.
        try {
            if (event.isPlayerTemporary()) return;
        } 
        catch (NoSuchMethodError e) {}
        
        // Not interested in any other packet.
        if (event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION) {
            return;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            counters.add(ProtocolLibComponent.idNullPlayer, 1);
            return;
        }
        
        final IPlayerData pData = DataManager.getPlayerDataSafe(player);
        if (pData == null) {
            StaticLog.logWarning("Failed to fetch player data with " + event.getPacketType() + " for: " + player.toString());
            return;
        }
        // Return if moving checks are disabled, because we request a setback to prevent the action, instead of cancelling it.
        // (Actions are read-only and client-sided (cancelling the event won't do anything))
        if (!pData.isCheckActive(CheckType.MOVING, player)) {
            return;
        }
        final StructureModifier<PlayerAction> actions = event.getPacket().getPlayerActions();
        // if (actions.size() != 1) {
        //     StaticLog.logWarning("Unexpected packet size for EntityActionAdapter: " + actions.size());
        //     return;
        // }
       
        final NetData data = pData.getGenericInstance(NetData.class);
        // (This check might invalidate the current BedLeave implementation...)
        // Currently, we don't discern action types from one another.
        data.playerActionFreq.add(System.currentTimeMillis(), 1f);
        // Always set last received time.
        data.lastKeepAliveTime = System.currentTimeMillis();
        boolean cancel = false;
        if (!cancel && pData.isCheckActive(CheckType.NET_TOGGLEFREQUENCY, player)
            && toggleFrequency.check(player, data, pData.getGenericInstance(NetConfig.class), pData)
             && !pData.hasBypass(CheckType.NET_TOGGLEFREQUENCY, player)) {
            cancel = true;
        }
        
        // Request a setback
        if (cancel) {
            final Location knownLocation = player.getLocation(useLoc);
            final MovingData mData = pData.getGenericInstance(MovingData.class);
            int task = -1;
            task = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                /** New to location to setback the player to: get the first set-back location that might be available */
                final Location newTo = mData.hasSetBack() ? mData.getSetBack(knownLocation) :
                                       mData.hasMorePacketsSetBack() ? mData.getMorePacketsSetBack() :
                                       knownLocation;
                // Unsafe position. Null world or not updated world.    
                if (newTo == null) {
                    // (Kick the player due to crash exploit potential i.e.: with extremely long blink cheat distances)
                    StaticLog.logSevere("[NoCheatPlus] Could not restore location for " + player.getName() + ", kicking them.");
                    CheckUtils.kickIllegalMove(player, pData.getGenericInstance(MovingConfig.class));
                } 
                else {
                    // Mask player teleport as a set back.
                    mData.prepareSetBack(newTo);
                    player.teleport(LocUtil.clone(newTo), BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
                    // TODO: Might request an Improbable update here as well.
                    if (pData.isDebugActive(CheckType.NET_TOGGLEFREQUENCY)) {
                        debug(player, "Set back player: " + player.getName() + ":" + LocUtil.simpleFormat(newTo));
                    }
               }
            });
            if (task == -1) {
                StaticLog.logWarning("[NoCheatPlus] Failed to schedule task for player: " + player.getName());
            }
            // Cleanup
            mData.resetTeleported();
            useLoc.setWorld(null);
        }
    }
}