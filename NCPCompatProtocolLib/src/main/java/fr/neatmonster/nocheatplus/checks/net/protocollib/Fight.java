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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;

// TODO: Name is misleading; doesn't contain any packet related to combat. Rename to something more appropriate (i.e.: MiscAdapter)
public class Fight extends BaseAdapter {
    private static PacketType[] initPacketTypes() {
        final List<PacketType> types = new LinkedList<PacketType>(Arrays.asList(
                //PacketType.Play.Client.ARM_ANIMATION,
                PacketType.Play.Server.EXPLOSION
        ));
        return types.toArray(new PacketType[types.size()]);
    }

    public Fight(Plugin plugin) {
        super(plugin, ListenerPriority.MONITOR, initPacketTypes());
    }

    //@Override
    //public void onPacketReceiving(final PacketEvent event) {
    //    handleAnmationPacket(event);
    //}

    @Override
    public void onPacketSending(final PacketEvent event) {
        handleVelocityPacket(event);
    }
    
    private void handleVelocityPacket(PacketEvent event) {
        if (event.isPlayerTemporary()) return;
        if (event.getPacketType() != PacketType.Play.Server.EXPLOSION) return;

        final Player player = event.getPlayer();
        if (player == null) {
            counters.add(ProtocolLibComponent.idNullPlayer, 1);
            return;
        }

        final PacketContainer packet = event.getPacket();
        final StructureModifier<Float> floats = packet.getFloat();

        if (floats.size() != 4) {
            // TODO : Warning
            return;
        }

        //final Float strength = floats.read(0);

        final Float velX = floats.read(1);
        final Float velY = floats.read(2);
        final Float velZ = floats.read(3);
        if (Math.abs(velX) == 0.0 && Math.abs(velZ) == 0.0 && Math.abs(velY) == 0.0) return;

        final IPlayerData pData = DataManager.getPlayerData(player);
        if (!pData.isCheckActive(CheckType.MOVING, player)) return;
        final MovingData data = pData.getGenericInstance(MovingData.class);
        // Process velocity.
        data.shouldApplyExplosionVelocity = true;
        data.explosionVelAxisX += velX;
        data.explosionVelAxisY += velY;
        data.explosionVelAxisZ+= velZ;
    }

    //public void handleAnmationPacket(final PacketEvent event) {
        
     //   final Player player = event.getPlayer();
     //   final FightData data = DataManager.getGenericInstance(player, FightData.class);
        // TODO: Count temporary player as well?
     //   if (event.isPlayerTemporary()) return;
     //   if (event.getPacketType() != PacketType.Play.Client.ARM_ANIMATION) {
            //data.noSwingPacket = false;
            //data.noSwingArmSwung = false;
     //       return;
     //   }
     //   if (player == null) {
     //       counters.add(ProtocolLibComponent.idNullPlayer, 1);
     //       return;
     //   }
     //   data.noSwingPacket = true;
     //   data.noSwingArmSwung = true;
    //}
}