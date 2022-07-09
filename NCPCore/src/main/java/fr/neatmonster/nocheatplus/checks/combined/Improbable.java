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

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.components.registry.feature.IDisableListener;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * This check combines different other checks frequency and occurrences into one count.
 * (Intended for static access by other checks.) 
 *
 * @author asofold
 *
 */
public class Improbable extends Check implements IDisableListener {

    private static Improbable instance = null;

    /**
     * Return if to cancel.
     * @param player
     * @param weights
     * @param now
     * @return
     */
    public static final boolean check(final Player player, final float weight, final long now, final String tags, final IPlayerData pData) {
        return instance.checkImprobable(player, weight, now, tags, pData);
    }

    /**
     * Feed the check but no violations processing (convenience method).
     * @param player
     * @param weight
     * @param now
     * @param pData
     */
    public static final void feed(final Player player, final float weight, final long now, final IPlayerData pData) {
        pData.getGenericInstance(CombinedData.class).improbableCount.add(now, weight);
    }

    /**
     * Feed the check but no violations processing (convenience method).
     * @param player
     * @param weight
     * @param now
     */
    public static void feed(final Player player, final float weight, long now) {
        feed(player, weight, now, DataManager.getPlayerData(player));
    }

    ////////////////////////////////////
    // Instance methods.
    ///////////////////////////////////

    public Improbable() {
        super(CheckType.COMBINED_IMPROBABLE);
        instance = this;
    }

    /**
     * Checks a player for improbable behaviour.
     * @param player
     * @param weight
     * @param now
     * @param tags
     * @param pData
     * @return true if to cancel
     * 
     */
    private boolean checkImprobable(final Player player, final float weight, final long now, final String tags, final IPlayerData pData) {
        // Random/Unlikely note about a potential, future approach to the Improbable check
        // 1) Let ALL checks *feed* the Improbable check 
        // 2) Then let server administrators perform an on-demand *check*, instead of having selected checks requesting Improbable checkings. (i.e.: /ncp test Improbable [player] [time-frame to check improbable behaviour for])
        // 3) Return a percentage, indicating how likely the player is to be cheating ("[player] showed improbable behaviour over the last [specified time-window] hour(s)/minute(s) (Perc.: [percentage]").
        // 3) Introduce some kind of resetting mechanic, which are likely going to be needed, otherwise Improbable can collect too much data
        //    (i.e.: - Reset all fed amount anyway after a custom amount of hours;
        //           - If the returned % is lower than 50, reward the player by reducing the fed amount;
        //           - Reduce data when players are reaching high violation levels in other checks anyway, because
        //             improbable wouldn't be needed much in that case, clearly (i.e.: triggering EXTREME_MOVE)...;
        //           - Relax data on certain events (i.e.: teleports and portals)
        //           - Always reduce data with stuff that we know to be 100% legit 
        //             (would need some kind of restriction against abuses. i.e.: click 50 times a second, then pause for some minutes, rinse and repeat.)
        //           - Scale data according to lag factor, like the current implementation does
        // Certainly, such implementation would benefit from having a good variety of checks

        if (!pData.isCheckActive(type, player)) {
            return false;
        }
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        final CombinedConfig cc = pData.getGenericInstance(CombinedConfig.class);
        data.improbableCount.add(now, weight);
        /** Score of the first 3 seconds */
        final float shortTerm = data.improbableCount.bucketScore(0);
        double violation = 0.0;
        boolean violated = false;
        if (shortTerm * 0.8f > cc.improbableLevel / 20.0) {
            /** Lag factor for the first window lag (3 seconds) */
            final float lag = pData.getCurrentWorldData().shouldAdjustToLag(type) ? TickTask.getLag(data.improbableCount.bucketDuration(), true) : 1f;
            // Re-check with lag adaptation.
            if (shortTerm / lag > cc.improbableLevel / 20.0) {
                violation += shortTerm * 2D / lag;
                violated = true;
            }
        }
        /** Sum score of all buckets, no weight */
        final double full = data.improbableCount.score(1.0f);
        if (full > cc.improbableLevel) {
            /** Full window lag factor, 1 minute */
            final float lag = pData.getCurrentWorldData().shouldAdjustToLag(type) ? TickTask.getLag(data.improbableCount.bucketDuration() * data.improbableCount.numberOfBuckets(), true) : 1f;
            // Re-check with lag adaptation.
            if (full / lag > cc.improbableLevel) {
                violation += full / lag;
                violated = true;
            }
        }
        boolean cancel = false;
        if (violated) {
            // Execute actions
            data.improbableVL += violation / 10.0;
            final ViolationData vd = new ViolationData(this, player, data.improbableVL, violation, cc.improbableActions);
            if (tags != null && !tags.isEmpty()) vd.setParameter(ParameterName.TAGS, tags);
            cancel = executeActions(vd).willCancel();
        }
        else data.improbableVL *= 0.95;
        return cancel;
    }

    @Override
    public void onDisable() {
        instance = null;
    }
}
