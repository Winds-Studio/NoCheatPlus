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
import org.bukkit.inventory.InventoryView;

import fr.neatmonster.nocheatplus.checks.access.ACheckData;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;

/**
 * Player specific dataFactory for the inventory checks.
 */
public class InventoryData extends ACheckData {

    // Violation levels.
    public double invMoveVL;
    public double fastClickVL;
    public double instantBowVL;
    public double fastConsumeVL;
    public double gutenbergVL;



    // Data shared between the checks.
    /** Remember the last time an inventory click happened. Always updates with each click */
    public long lastClickTime = 0;
    /** Remember the time at which a containter was interacted with */
    public long containerOpenTime = 0;
    /**
     * Remember when the player clicked in an inventory for the first time. Intention is to see if players could have opened their inventory:
     * It resets when we receive an InventoryCloseEvent or other events that would forcibly close the player's inv (for false positives).
     * See: https://www.spigotmc.org/threads/detecting-when-player-opens-their-own-inv.397535/#post-3563623
     */
    public long firstClickTime;

    // Data of the fast click check.
    public final ActionFrequency fastClickFreq = new ActionFrequency(5, 200L);
    public Material fastClickLastClicked = null;
    public int fastClickLastSlot = InventoryView.OUTSIDE;
    public Material fastClickLastCursor = null;
    public int fastClickLastCursorAmount = 0;

    // Data of the instant bow check.
    /** Last time right click interact on bow. A value of 0 means 'invalid'.*/
    public long instantBowInteract = 0;
    public long instantBowShoot;

    // Data of the fastconsume check.
    public Material fastConsumeFood;
    public long fastConsumeInteract;
    
    // Data of the InventoryMove check.
    public long lastMoveEvent = 0;
}
