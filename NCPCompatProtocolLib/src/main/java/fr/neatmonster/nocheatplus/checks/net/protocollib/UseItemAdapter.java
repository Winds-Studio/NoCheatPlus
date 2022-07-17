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

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder.RegisterMethodWithOrder;
import fr.neatmonster.nocheatplus.event.mini.MiniListener;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.InventoryUtil;

public class UseItemAdapter extends BaseAdapter {
    private final static String dftag = "system.nocheatplus.useitemadapter";
    private final static MiniListener<?>[] miniListeners = new MiniListener<?>[] {
        new MiniListener<PlayerItemConsumeEvent>() {
            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
            @RegisterMethodWithOrder(tag = dftag)
            @Override
            public void onEvent(final PlayerItemConsumeEvent event) {
                onItemConsume(event);
            }
        },
        new MiniListener<PlayerInteractEvent>() {
            @EventHandler(priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = dftag)
            @Override
            public void onEvent(final PlayerInteractEvent event) {
                onItemInteract(event);
            }
        },
        new MiniListener<InventoryOpenEvent>() {
            @EventHandler(priority = EventPriority.LOWEST)
            @RegisterMethodWithOrder(tag = dftag)
            @Override
            public void onEvent(final InventoryOpenEvent event) {
                onInventoryOpen(event);
            }
        },
        new MiniListener<PlayerItemHeldEvent>() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            @RegisterMethodWithOrder(tag = dftag)
            @Override
            public void onEvent(final PlayerItemHeldEvent event) {
                onChangeSlot(event);
            }
        }
    };

    private static int timeBetweenRL = 70;
    private static PacketType[] initPacketTypes() {
        final List<PacketType> types = new LinkedList<PacketType>(Arrays.asList(
                PacketType.Play.Client.BLOCK_DIG,
                PacketType.Play.Client.BLOCK_PLACE
                ));
        return types.toArray(new PacketType[types.size()]);
    }

    public UseItemAdapter(Plugin plugin) {
        super(plugin, ListenerPriority.MONITOR, initPacketTypes());
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        for (final MiniListener<?> listener : miniListeners) {
            api.addComponent(listener, false);
        }
    }

    @Override
    public void onPacketReceiving(final PacketEvent event) {
        if (event.isPlayerTemporary()) return;
        if (event.getPacketType().equals(PacketType.Play.Client.BLOCK_DIG)) {
            handleDiggingPacket(event);
        } 
        else {
            handleBlockPlacePacket(event);
        }
    }

    private static void onItemConsume(final PlayerItemConsumeEvent e){
        final Player p = e.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(p);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        data.isUsingItem = false;        
    }

    private static void onInventoryOpen(final InventoryOpenEvent e){
        if (e.isCancelled()) return;
        final Player p = (Player) e.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(p);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        data.isUsingItem = false;        
    }

    private static void onItemInteract(final PlayerInteractEvent e){
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Player p = e.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(p);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        // Reset
        data.offHandUse = false;
        if (!data.mightUseItem) {
            return;
        }
        data.mightUseItem = false;

        if (e.useItemInHand().equals(Event.Result.DENY)) {
            return;
        }

        if (p.getGameMode() == GameMode.CREATIVE) {
            data.isUsingItem = false;
            return;
        }

        if (e.hasItem()) {
            final ItemStack item = e.getItem();
            final Material m = item.getType();
            if (Bridge1_9.hasElytra() && p.hasCooldown(m)) {
                return;
            }

            if (InventoryUtil.isConsumable(item)) {
                // pre1.9 splash potion
                if (!Bridge1_9.hasElytra() && item.getDurability() > 16384) return;
                if (m == Material.POTION || m == Material.MILK_BUCKET || m.toString().endsWith("_APPLE") || m.name().startsWith("HONEY_BOTTLE")) {
                    data.isUsingItem = true;
                    data.offHandUse = Bridge1_9.hasGetItemInOffHand() && e.getHand() == EquipmentSlot.OFF_HAND;
                    return;
                }
                if (item.getType().isEdible() && p.getFoodLevel() < 20) {
                    data.isUsingItem = true;
                    data.offHandUse = Bridge1_9.hasGetItemInOffHand() && e.getHand() == EquipmentSlot.OFF_HAND;
                    return;
                }
            }

            if (m == Material.BOW && hasArrow(p.getInventory(), false)) {
                data.isUsingItem = true;
                data.offHandUse = Bridge1_9.hasGetItemInOffHand() && e.getHand() == EquipmentSlot.OFF_HAND;
                return;
            }

            if (Bridge1_9.hasElytra() && m == Material.SHIELD) {
                //data.isUsingItem = true;
                data.offHandUse = e.getHand() == EquipmentSlot.OFF_HAND;
                return;
            }

            if (Bridge1_13.hasIsRiptiding() && m == Material.TRIDENT) {
                //data.isUsingItem = true;
                data.offHandUse = e.getHand() == EquipmentSlot.OFF_HAND;
                return;
            }

            if (m.toString().equals("CROSSBOW")) {
                if (!((CrossbowMeta) item.getItemMeta()).hasChargedProjectiles() && hasArrow(p.getInventory(), true)) {
                    data.isUsingItem = true;
                    data.offHandUse = e.getHand() == EquipmentSlot.OFF_HAND;
                }
            }
        } else data.isUsingItem = false;        
    }

    private static void onChangeSlot(final PlayerItemHeldEvent e) {
        final Player p = e.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(p);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        //if (data.changeslot) {
        //    p.getInventory().setHeldItemSlot(data.olditemslot);
        //    data.changeslot = false;
        //}
        data.isUsingItem = false;
    }

    private static boolean hasArrow(final PlayerInventory i, final boolean fw) {
        if (Bridge1_9.hasElytra()) {
            final Material m = i.getItemInOffHand().getType();
            return (fw && m == Material.FIREWORK_ROCKET) || m.toString().endsWith("ARROW") ||
                   i.contains(Material.ARROW) || i.contains(Material.TIPPED_ARROW) || i.contains(Material.SPECTRAL_ARROW);
        }
        return i.contains(Material.ARROW);
    }

    private void handleBlockPlacePacket(PacketEvent event) {
        final Player p = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(p);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        final PacketContainer packet = event.getPacket();
        final StructureModifier<Integer> ints = packet.getIntegers();
        // Legacy: pre 1.9
        if (ints.size() > 0) {
            final int faceIndex = ints.read(0); // arg 3 if 1.7.10 below
            if (faceIndex <= 5) {
                data.mightUseItem = false;
                return;
            }
        }
        if (!event.isCancelled()) data.mightUseItem = true;
    }

    private void handleDiggingPacket(PacketEvent event) {
        Player p = event.getPlayer();       
        
        if (p == null) {
            counters.add(ProtocolLibComponent.idNullPlayer, 1);
            return;
        }
        final IPlayerData pData = DataManager.getPlayerDataSafe(p);
        if (pData == null) {
            StaticLog.logWarning("Failed to fetch player data with " + event.getPacketType() + " for: " + p.toString());
            return;
        }
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        PlayerDigType digtype = event.getPacket().getPlayerDigTypes().read(0);
        // DROP_ALL_ITEMS when dead?
        if (digtype == PlayerDigType.DROP_ALL_ITEMS || digtype == PlayerDigType.DROP_ITEM) data.isUsingItem = false;
        
        //Advanced check
        if(digtype == PlayerDigType.RELEASE_USE_ITEM) {
            data.isUsingItem = false;
            long now = System.currentTimeMillis();
            if (data.releaseItemTime != 0) {
                if (now < data.releaseItemTime) {
                    data.releaseItemTime = now;
                    return;
                }
                if (data.releaseItemTime + timeBetweenRL > now) {
                    data.isHackingRI = true;
                }
            }
            data.releaseItemTime = now;
        }
    }

    /**
     * Set Minimum time between RELEASE_USE_ITEM packet is sent.
     * If time lower this value, A check will flag
     * Should be set from 51-100. Larger number, more protection more false-positive
     * 
     * @param time milliseconds
     */ 
    public static void setuseRLThreshold(int time) {
        timeBetweenRL = time;
    }   
}
