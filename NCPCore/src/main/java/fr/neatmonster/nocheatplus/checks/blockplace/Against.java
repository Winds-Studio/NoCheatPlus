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
package fr.neatmonster.nocheatplus.checks.blockplace;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractData;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;


/**
 * Check if the placing is legitimate in terms of surrounding materials.
 * @author asofold
 *
 */
public class Against extends Check {


   /**
    * Instanties a new Against check.
    *
    */
    public Against() {
        super(CheckType.BLOCKPLACE_AGAINST);
    }


    /**
     * Checks a player
     * @param player
     * @param block
     * @param placedMat the material placed.
     * @param blockAgainst
     * @param isInteractBlock
     * @param data
     * @param cc
     * @param pData
     *
     */
    public boolean check(final Player player, final Block block, final Material placedMat, 
                         final Block blockAgainst, final boolean isInteractBlock, 
                         final BlockPlaceData data, final BlockPlaceConfig cc, final IPlayerData pData) {
        
        boolean violation = false;
        final BlockInteractData bIData = pData.getGenericInstance(BlockInteractData.class); // TODO: pass as argument.
        final Material againstType = blockAgainst.getType();
        final Material matAgainst = bIData.getLastType();

        if (pData.isDebugActive(type)) {
            debug(player, "Player placed (" + placedMat + ") against (" + blockAgainst.toString() +"/"+ matAgainst + "). againstType: " + againstType);
        }

        if (bIData.isConsumedCheck(this.type) && !bIData.isPassedCheck(this.type)) {
            // TODO: Awareness of repeated violation probably is to be implemented below somewhere.
            violation = true;
            if (pData.isDebugActive(type)) {
                debug(player, "Cancel due to block having been consumed by this check.");
            }
        }
        else if (BlockProperties.isAir(matAgainst)) { // Holds true for null blocks.
            if (isInteractBlock && !BlockProperties.isAir(matAgainst) && !BlockProperties.isLiquid(matAgainst)) {
                // Block was placed against something (e.g. cactus), allow it.
            }
            else if (!pData.hasPermission(Permissions.BLOCKPLACE_AGAINST_AIR, player)
                    && placedMat != BridgeMaterial.LILY_PAD) {
                violation = true;
                // Attempted to place a block against a null one (air)
            }
        }
        else if (BlockProperties.isLiquid(matAgainst)) {
            if ((placedMat != BridgeMaterial.LILY_PAD
                || !BlockProperties.isLiquid(block.getRelative(BlockFace.DOWN).getType()))
                && !BlockProperties.isWaterPlant(bIData.getLastType())
                && !pData.hasPermission(Permissions.BLOCKPLACE_AGAINST_LIQUIDS, player)) {
                violation = true;
            }
        }
        
        // Handle violation and return.
        bIData.addConsumedCheck(this.type);
        if (violation) {
            data.againstVL += 1.0;
            final ViolationData vd = new ViolationData(this, player, data.againstVL, 1, cc.againstActions);
            vd.setParameter(ParameterName.BLOCK_TYPE, matAgainst == null ? null : matAgainst.toString());
            return executeActions(vd).willCancel();
        }
        else {
            data.againstVL *=  0.99; // Assume one false positive every 100 blocks.
            bIData.addPassedCheck(this.type);
            return false;
        }
    }
}