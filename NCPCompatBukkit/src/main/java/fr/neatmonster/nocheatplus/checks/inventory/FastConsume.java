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
package fr.neatmonster.nocheatplus.checks.inventory;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.components.config.value.OverrideType;
import fr.neatmonster.nocheatplus.components.registry.feature.INotifyReload;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.InventoryUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Replaces the former InstantEat check (pre 1.5). Will be disabled from 1.8 and higher versions.<br>
 * This check is added by fr.neatmonster.nocheatplus.compat.DefaultComponentFactory.
 * @author asofold
 *
 */
public class FastConsume extends Check implements Listener {

    public static void testAvailability() {
        if (!PlayerItemConsumeEvent.class.getSimpleName().equals("PlayerItemConsumeEvent")){
            throw new RuntimeException("This exception should not even get thrown.");
        }
    }

    private final Counters counters = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class);
    private final int idCancelDead = counters.registerKey("cancel.dead");

    public FastConsume() {
        super(CheckType.INVENTORY_FASTCONSUME);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemConsume(final PlayerItemConsumeEvent event){
        final Player player = event.getPlayer();
        if (player.isDead() && BridgeHealth.getHealth(player) <= 0.0) {
            // Eat after death.
            event.setCancelled(true);
            counters.addPrimaryThread(idCancelDead, 1);
            return;
        }
        final IPlayerData pData = DataManager.getPlayerData(player);
        if (!pData.isCheckActive(type, player)) {
            return;
        }
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        final long time = System.currentTimeMillis();
        if (check(player, event.getItem(), time, data, pData)){
            event.setCancelled(true);
            DataManager.getPlayerData(player).requestUpdateInventory();
        }
    }
    
    /**
     * Checks a player
     * @param player
     * @param stack
     * @param time
     * @param data
     * @param pData
     * @return True, if to cancel the event. In that case, the inventory will be updated as well.
     */
    private boolean check(final Player player, final ItemStack stack, final long time, final InventoryData data, final IPlayerData pData) {
        // Uses the instant-eat data for convenience.
        // Consistency checks...
        if (stack == null) { // || stack.getType() != data.fastConsumeFood){
            // TODO: Strict version should prevent other material (?).
            return false;
        }
        final long ref = data.fastConsumeInteract == 0 ? 0 : Math.max(data.fastConsumeInteract, data.lastClickTime);
        if (time < ref) {
            // Time ran backwards.
            data.fastConsumeInteract = data.lastClickTime = time;
            return false;
        }
        // Check exceptions.
        final InventoryConfig cc = pData.getGenericInstance(InventoryConfig.class);
        final Material mat = stack == null ? null : stack.getType();
        if (mat != null) {
            if (cc.fastConsumeWhitelist) {
                if (!cc.fastConsumeItems.contains(mat)) {
                    return false;
                }
            }
            else if (cc.fastConsumeItems.contains(mat)) {
                return false;
            }
        }
        // Actually check.
        final long timeSpent = ref == 0 ? 0 : (time - ref); // Not interact = instant.
        final long expectedDuration = cc.fastConsumeDuration;
        boolean cancel = false;
        if (timeSpent < expectedDuration) {
            // TODO: Might have to do a specialized check for lag spikes here instead.
            final float lag = TickTask.getLag(expectedDuration, true);
            if (timeSpent * lag < expectedDuration) {
                final double difference = (expectedDuration - timeSpent * lag) / 100.0;
                data.fastConsumeVL += difference;
                final ViolationData vd = new ViolationData(this, player, data.fastConsumeVL, difference, cc.fastConsumeActions);
                vd.setParameter(ParameterName.FOOD, "" + mat);
                if (data.fastConsumeFood != mat) {
                    vd.setParameter(ParameterName.TAGS, "inconsistent(" + data.fastConsumeFood + ")");
                }
                else {
                    vd.setParameter(ParameterName.TAGS, "");
                }
                if (executeActions(vd).willCancel()){
                    cancel = true;
                }
            }
        }
        else {
            data.fastConsumeVL *= 0.6; 
        }
        // Reset interaction.
        if (cancel) {
            // Fake interaction to prevent violation loops with false positives.
            final ItemStack actualStack = InventoryUtil.getFirstConsumableItemInHand(player);
            data.fastConsumeFood = actualStack == null ? null : actualStack.getType();
            // TODO: Allows some abuse: 1. try instantly eat (cancelled) 2. consume item directly when needed.
        }
        else  {
            if (pData.isDebugActive(type)) {
                debug(player, "PlayerItemConsumeEvent, reset fastconsume: " + data.fastConsumeFood);
            }
            data.fastConsumeFood = null;
        }
        data.fastConsumeInteract = time;
        return cancel;
    }
}
