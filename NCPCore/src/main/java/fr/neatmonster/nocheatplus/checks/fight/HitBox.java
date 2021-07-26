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

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.location.tracking.LocationTrace.ITraceEntry;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.collision.CollideRayVsAABB;
import fr.neatmonster.nocheatplus.utilities.collision.ICollideRayVsAABB;
import fr.neatmonster.nocheatplus.utilities.collision.PassableRayTracing;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.WrapBlockCache;

public class HitBox extends Check {
    private final ICollideRayVsAABB boulder = new CollideRayVsAABB();
    private final PassableRayTracing rayTracing = new PassableRayTracing();
    private final WrapBlockCache wrapBlockCache;

    public HitBox() {
        super(CheckType.FIGHT_HITBOX);
        wrapBlockCache = new WrapBlockCache();
        rayTracing.setMaxSteps(30);
    }

    public boolean check(final Player player, final Location loc, 
            final Entity damagedEntity, final boolean worldChanged, 
            final FightData data, final FightConfig cc, final IPlayerData pData) {
        return false;
    }

    public boolean loopCheck(final Player player, final Location loc, final ITraceEntry dLoc, 
            final DirectionContext dirContext, final ReachContext reachContext, final VisibleContext visibleContext,
            final FightData data, final FightConfig cc,
            final boolean directionEnabled, final boolean reachEnabled, final boolean visibleEnabled) {
        final BlockCache blockCache = wrapBlockCache.getBlockCache();
        blockCache.setAccess(loc.getWorld());
        boolean cancel = false;
        
        final double eyeYLocation = loc.getY() + player.getEyeHeight() - (player.isSneaking() ? 0.08 : 0.0);
        final double distanceLimit = cc.reachSurvivalDistance + (player.getGameMode() == GameMode.CREATIVE ? 2.0 : 0.0);
        if (reachEnabled) reachContext.distanceLimit = distanceLimit;

        boulder.setFindNearestPointIfNotCollide(false)
        .setRay(loc.getX(), eyeYLocation, loc.getZ(), dirContext.direction.getX(), dirContext.direction.getY(), dirContext.direction.getZ())
        .setAABB(dLoc.getX(), dLoc.getY(), dLoc.getZ(), dLoc.getBoxMarginHorizontal() + 0.125, dLoc.getBoxMarginVertical() + 0.125)
        .loop();

        if (boulder.collides()) {
            // Direction passed 
            // Checking distance
            final double distance = TrigUtil.distance(boulder.getX(), boulder.getY(), boulder.getZ(), 
                    loc.getX(), eyeYLocation, loc.getZ());
            if (reachEnabled) {
                if (distance > distanceLimit) {
                    reachContext.minViolation = Math.min(reachContext.minViolation, distance);
                    cancel = true;
                }
                reachContext.minResult = Math.min(reachContext.minResult, distance);
            }

            // Checking visible
            if (visibleEnabled) {
                rayTracing.setBlockCache(blockCache);
                rayTracing.setIgnoreInitiallyColliding(true);
                rayTracing.set(loc.getX(), eyeYLocation, loc.getZ(), boulder.getX(), boulder.getY(), boulder.getZ());
                rayTracing.loop();
                
                if (rayTracing.collides()) {
                    visibleContext.collided = true;
                    cancel = true;
                }
            }
        } else {
            // Direction failed. Skipping distance, visible check
            final Vector blockEyes = new Vector(dLoc.getX() - loc.getX(), dLoc.getY() + dLoc.getBoxMarginVertical() / 2D - loc.getY() - player.getEyeHeight(), dLoc.getZ() - loc.getZ());
            final double distance = blockEyes.crossProduct(dirContext.direction).length() / dirContext.lengthDirection;
            dirContext.minViolation = Math.min(dirContext.minViolation, distance);
            cancel = true;
            //player.sendMessage("Direction flag " + dLoc.getBoxMarginVertical() + " : " + eyeYLocation + " " + dLoc.getX() + " " + dLoc.getY() + " " + dLoc.getZ());
        }
        rayTracing.cleanup();
        blockCache.cleanup();
        return cancel;
    }

    public boolean loopFinish(final Player player, final Location pLoc, final Entity damaged, 
            final ReachContext reachContext, final DirectionContext dirContext, final VisibleContext visibleContext,
            final ITraceEntry traceEntry, final boolean forceViolation, 
            final FightData data, final FightConfig cc, final boolean reachEnabled, final boolean visibleEnabled, final IPlayerData pData) {
        boolean cancel = false;
        final double distance = reachEnabled ? forceViolation && reachContext.minViolation != Double.MAX_VALUE ? reachContext.minViolation : reachContext.minResult : Double.MAX_VALUE;
        final double off = forceViolation && dirContext.minViolation != Double.MAX_VALUE ? dirContext.minViolation : dirContext.minResult;
        final boolean collided = visibleEnabled ? forceViolation && visibleContext.collided : false;

        // Direction violation
        if (off != Double.MAX_VALUE && off > 0) {
            data.directionVL += off;
            cancel = executeActions(player, data.directionVL, off, cc.directionActions).willCancel();
            if (cancel) {
                // Deal an attack penalty time.
                data.attackPenalty.applyPenalty(cc.directionPenalty);
                return cancel;
            }
        } else {
            // Reward the player by lowering their violation level.
            data.directionVL *= 0.8D;
        }

        // Reach violation
        if (distance != Double.MAX_VALUE && distance > reachContext.distanceLimit) {
            data.reachVL += distance - reachContext.distanceLimit;
            final ViolationData vd = new ViolationData(this, player, data.reachVL, distance - reachContext.distanceLimit, cc.reachActions);
            vd.setParameter(ParameterName.REACH_DISTANCE, StringUtil.fdec3.format(distance));
            cancel = executeActions(vd).willCancel();
            if (cancel) {
                // Apply an attack penalty time.
                data.attackPenalty.applyPenalty(cc.reachPenalty);
                return cancel;
            }
        } else {
            // Player passed the check, reward them.
            data.reachVL *= 0.8D;
        }

        // Visible violation
        if (collided) {
            data.visibleVL++;
            cancel = true;
        } else {
            data.visibleVL *= 0.8;
        }
        return cancel;
    }

}
