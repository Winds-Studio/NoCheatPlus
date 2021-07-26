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
package fr.neatmonster.nocheatplus.checks.fight;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;

public class Visible extends Check {

    public Visible() {
        super(CheckType.FIGHT_VISIBLE);
    }

    public boolean check() {
        return false;
    }

    public boolean loopCheck() {
        return false;
    }

    public boolean loopFinish() {
        return false;
    }

    /**
     * Data context for iterating over ITraceEntry instances.
     * @param player
     * @param pLoc
     * @param damaged
     * @param damagedLoc
     * @param data
     * @param cc
     * @return
     */
    public VisibleContext getContext(final Player player, final Location pLoc, 
                                   final Entity damaged, final Location damagedLoc, 
                                   final FightData data, final FightConfig cc) {
        final VisibleContext context = new VisibleContext();
        context.pY = pLoc.getY() + player.getEyeHeight();
        return context;
    }

}
