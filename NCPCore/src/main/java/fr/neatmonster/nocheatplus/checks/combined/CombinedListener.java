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
package fr.neatmonster.nocheatplus.checks.combined;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckListener;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.registry.factory.IFactoryOne;
import fr.neatmonster.nocheatplus.components.registry.feature.JoinLeaveListener;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.PlayerFactoryArgument;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.worlds.WorldFactoryArgument;

/**
 * Class to combine some things, make available for other checks, or just because they don't fit into another section.<br>
 * This is registered before the FightListener.
 * Do note the registration order in fr.neatmonster.nocheatplus.NoCheatPlus.onEnable (within NCPPlugin).
 * 
 * @author asofold
 *
 */
public class CombinedListener extends CheckListener implements JoinLeaveListener {

    protected final Improbable improbable = addCheck(new Improbable());

    protected final MunchHausen munchHausen = addCheck(new MunchHausen());

    private final Counters counters = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class);

    private final int idFakeInvulnerable = counters.registerKey("fakeinvulnerable");

    @SuppressWarnings("unchecked")
    public CombinedListener(){
        super(CheckType.COMBINED);
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        api.register(api.newRegistrationContext()
                // CombinedConfig
                .registerConfigWorld(CombinedConfig.class)
                .factory(new IFactoryOne<WorldFactoryArgument, CombinedConfig>() {
                    @Override
                    public CombinedConfig getNewInstance(WorldFactoryArgument arg) {
                        return new CombinedConfig(arg.worldData);
                    }
                })
                .registerConfigTypesPlayer()
                .context() //
                // CombinedData
                .registerDataPlayer(CombinedData.class)
                .factory(new IFactoryOne<PlayerFactoryArgument, CombinedData>() {
                    @Override
                    public CombinedData getNewInstance(
                            PlayerFactoryArgument arg) {
                        return new CombinedData();
                    }
                })
                .addToGroups(CheckType.MOVING, false, IData.class, ICheckData.class)
                .removeSubCheckData(CheckType.COMBINED, true)
                .context() //
                );
    }
    
    @Override
    public void playerJoins(final Player player) {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        final CombinedConfig cc = pData.getGenericInstance(CombinedConfig.class);
        final boolean debug = pData.isDebugActive(checkType);

        if (cc.invulnerableCheck 
            && (cc.invulnerableTriggerAlways || cc.invulnerableTriggerFallDistance && player.getFallDistance() > 0)) {
            // TODO: maybe make a heuristic for small fall distances with ground under feet (prevents future abuse with jumping) ?
            final int invulnerableTicks = mcAccess.getHandle().getInvulnerableTicks(player);
            if (invulnerableTicks == Integer.MAX_VALUE) {
            	if (debug) {
            		debug(player, "Invulnerable ticks could not be determined.");
            	}   	
            } 
            else {
                final int ticks = cc.invulnerableInitialTicksJoin >= 0 ? cc.invulnerableInitialTicksJoin : invulnerableTicks;
                data.invulnerableTick = TickTask.getTick() + ticks;
                mcAccess.getHandle().setInvulnerableTicks(player, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        data.resetImprobableData();
        final boolean debug = pData.isDebugActive(checkType);
        if (debug) debug(player, "Clear improbable data on respawn.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        data.resetImprobableData();
        final boolean debug = pData.isDebugActive(checkType);
        if (debug) debug(player, "Clear improbable data on death.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(final PlayerGameModeChangeEvent event) {
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        // If gamemode changes, then we can safely drop Improbable's data.
        data.resetImprobableData();
        final boolean debug = pData.isDebugActive(checkType);
        if (debug) debug(player, "Clear improbable data on game mode change.");
    }

    @Override
    public void playerLeaves(final Player player) {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        data.resetImprobableData();
        final boolean debug = pData.isDebugActive(checkType);
        if (debug) debug(player, "Clear improbable data on leave.");
    }
    
    /**
     * We listen to entity damage events for the Invulnerable feature.
     * @param event
     *           the event
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        final Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        final Player  player = (Player) entity;
        final IPlayerData pData = DataManager.getPlayerData(player);
        final CombinedConfig cc = pData.getGenericInstance(CombinedConfig.class);
        if (!cc.invulnerableCheck) {
            return;
        }
        final DamageCause cause = event.getCause();
        // Ignored causes.
        if (cc.invulnerableIgnore.contains(cause)) {
            return;
        }
        // Modified invulnerable ticks.
        Integer modifier = cc.invulnerableModifiers.get(cause);
        if (modifier == null) modifier = cc.invulnerableModifierDefault;
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        // TODO: account for tick task reset ? [it should not though, due to data resetting too, but API would allow it]
        if (TickTask.getTick() >= data.invulnerableTick + modifier.intValue()) {
            return;
        }
        // Still invulnerable.
        event.setCancelled(true);
        counters.addPrimaryThread(idFakeInvulnerable, 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerFish(final PlayerFishEvent event) {
        // Check also in case of cancelled events.
        final Player player = event.getPlayer();
        if (munchHausen.isEnabled(player) && munchHausen.checkFish(player, event.getCaught(), event.getState())) {
            event.setCancelled(true);
        }
    }
}
