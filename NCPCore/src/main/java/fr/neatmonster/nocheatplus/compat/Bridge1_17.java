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
package fr.neatmonster.nocheatplus.compat;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;


public class Bridge1_17 {

    private static final boolean hasIsFrozen = ReflectionUtil.getMethodNoArgs(LivingEntity.class, "isFrozen", boolean.class) != null;

    public static boolean hasIsFrozen() {
        return hasIsFrozen;
    }

    public static int getFreezeSeconds(final Player player) {
        if (!hasIsFrozen()) return 0;
        // Capped at 140ticks (=7s)
        return Math.min(7, (player.getFreezeTicks() / 20));
    }

    /**
     * Test if the player has any piece of leather armor on 
     * which will prevent freezing.
     * 
     * @param player
     * @return
     */
    public static boolean isImmuneToFreezing(final Player player) {
        if (!hasIsFrozen()) {
            return false;
        }
        final PlayerInventory inv = player.getInventory();
        final ItemStack[] contents = inv.getArmorContents();
        for (int i = 0; i < contents.length; i++){
            final ItemStack armor = contents[i];
            if (armor != null && armor.getType().toString().startsWith("LEATHER")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test if the player is equipped with leather boots
     * Meant for checking if the player can stand on top of powder snow.
     * 
     * @param player
     * @return
     */
    public static boolean hasLeatherBootsOn(final Player player) {
        if (!hasIsFrozen()) {
            return false;
        }
        else {
            final ItemStack boots = player.getInventory().getBoots();
            return boots != null && boots.getType() == Material.LEATHER_BOOTS;
        }
    }
}
