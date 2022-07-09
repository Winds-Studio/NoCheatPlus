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
package fr.neatmonster.nocheatplus.compat.blocks.init.vanilla;

import org.bukkit.Material;

import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.blocks.BlockPropertiesSetup;
import fr.neatmonster.nocheatplus.compat.blocks.init.BlockInit;
import fr.neatmonster.nocheatplus.config.*;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties.BlockProps;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

public class BlocksMC1_19 implements BlockPropertiesSetup {
    public BlocksMC1_19() {
        BlockInit.assertMaterialExists("SCULK_CATALYST");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setupBlockProperties(WorldConfigProvider<?> worldConfigProvider) {
        BlockFlags.setFlagsAs("OCHRE_FROGLIGHT", Material.DIRT);
        BlockProperties.setBlockProps("OCHRE_FROGLIGHT", new BlockProperties.BlockProps(BlockProperties.noTool, 0.3f));
        BlockInit.setAs("VERDANT_FROGLIGHT", "OCHRE_FROGLIGHT");
        BlockInit.setAs("PEARLESCENT_FROGLIGHT", "OCHRE_FROGLIGHT");


        //
        BlockProperties.setBlockProps("MUD", new BlockProperties.BlockProps(BlockProperties.woodSpade, 0.5f, BlockProperties.secToMs(0.75, 0.4, 0.2, 0.15, 0.1, 0.1, 0.1)));
        BlockFlags.setFlagsAs("MUD", Material.DIRT);
        //
        BlockProperties.setBlockProps("PACKED_MUD", new BlockProperties.BlockProps(BlockProperties.woodSpade, 1f, BlockProperties.secToMs(1.5, 0.75, 0.4, 0.25, 0.2, 0.2, 0.15)));
        BlockFlags.setFlagsAs("PACKED_MUD", Material.DIRT);
        BlockInit.setAs("MUD_BRICKS", Material.ANDESITE);
        BlockInit.setAs("MUD_BRICK_SLAB", Material.ANDESITE_SLAB);
        BlockInit.setAs("MUD_BRICK_STAIRS", Material.ANDESITE_STAIRS);
        BlockInit.setAs("MUD_BRICK_WALL", Material.ANDESITE_WALL);
        // TODO: Remove those below if hardness overhaul is completed
        BlockInit.setPropsAs("MUD_BRICK_SLAB", Material.ANDESITE);
        BlockInit.setPropsAs("MUD_BRICK_STAIRS", Material.ANDESITE);
        BlockInit.setPropsAs("MUD_BRICK_WALL", Material.ANDESITE);


        BlockProperties.setBlockProps("FROGSPAWN", BlockProperties.instantType);


        BlockFlags.setFlagsAs("MUDDY_MANGROVE_ROOTS", Material.DIRT);
        //
        BlockProperties.setBlockProps("MUDDY_MANGROVE_ROOTS", new BlockProperties.BlockProps(BlockProperties.woodSpade, 0.7f, BlockProperties.secToMs(1.05, 0.55, 0.3, 0.2, 0.15, 0.15, 0.1)));
        BlockFlags.setFlagsAs("MANGROVE_ROOTS", Material.DIRT);
        //
        BlockProperties.setBlockProps("MANGROVE_ROOTS", new BlockProperties.BlockProps(BlockProperties.woodAxe, 0.7f, BlockProperties.secToMs(1.05, 0.55, 0.3, 0.2, 0.15, 0.15, 0.1)));
        
        
        BlockFlags.setBlockFlags("SCULK_VEIN", BlockFlags.F_IGN_PASSABLE);
        //
        BlockProperties.setBlockProps("SCULK_VEIN", new BlockProperties.BlockProps(BlockProperties.woodHoe, 0.2f, BlockProperties.secToMs(0.3, 0.15, 0.1, 0.0, 0.0, 0.0, 0.0)));
        BlockInit.setPropsAs("SCULK", "SCULK_VEIN");
        BlockFlags.setFlagsAs("SCULK", Material.DIRT);
        BlockFlags.setFlagsAs("SCULK_CATALYST", Material.DIRT);
        //
        BlockProperties.setBlockProps("SCULK_CATALYST", new BlockProperties.BlockProps(BlockProperties.woodHoe, 3f, BlockProperties.secToMs(4.5, 2.25, 1.15, 0.75, 0.6, 0.5, 0.4)));
        BlockFlags.setFlagsAs("REINFORCED_DEEPSLATE", Material.OBSIDIAN);
        BlockProperties.setBlockProps("REINFORCED_DEEPSLATE", new BlockProperties.BlockProps(BlockProperties.noTool, 55f));
        //
        BlockProperties.setBlockProps("SCULK_SHRIEKER", new BlockProperties.BlockProps(BlockProperties.woodHoe, 3f, BlockProperties.secToMs(4.5, 2.25, 1.15, 0.75, 0.6, 0.5, 0.4)));
        BlockFlags.setBlockFlags("SCULK_SHRIEKER", BlockFlags.SOLID_GROUND);

        ConfigFile config = ConfigManager.getConfigFile();
        if (config.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, config.getBoolean(ConfPaths.CHECKS_DEBUG, false)))
        StaticLog.logInfo("Added block-info for Minecraft 1.19 blocks.");
    }
}
