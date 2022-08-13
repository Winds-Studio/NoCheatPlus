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
package fr.neatmonster.nocheatplus.checks.workaround;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

import fr.neatmonster.nocheatplus.workaround.IWorkaround;
import fr.neatmonster.nocheatplus.workaround.SimpleWorkaroundRegistry;
import fr.neatmonster.nocheatplus.workaround.WorkaroundCountDown;
import fr.neatmonster.nocheatplus.workaround.WorkaroundCounter;

/**
 * Workaround registry for primary thread use. Potentially cover all checks.
 * 
 * @author asofold
 *
 */
public class WRPT extends SimpleWorkaroundRegistry {

    ///////////////////////
    // Workaround ids.
    ///////////////////////
    // MOVING_SURVIVALFLY
    // (vEnvHacks)
    // TODO: The use once thing could be shared by several spots (e.g. all double-0 top of slope).
    /** Workaround: One time use max of jump phase twice zero dist. */
    // TODO: This might be changed to (or extended with addition of) use once within air jump phase.
    public static final String W_M_SF_SLIME_JP_2X0 = "m.sf.slime.jp.2x0"; // hum. sha-1 instead?
    /** Zero vdist after negative vdist, "early" jump phase, cobweb. venvHacks */
    public static final String W_M_SF_WEB_0V1 = "m.sf.web.0v1";
    public static final String W_M_SF_WEB_0V2 = "m.sf.web.0v2";
    public static final String W_M_SF_WEB_MICROGRAVITY1 = "m.sf.web.microgravity1";
    public static final String W_M_SF_WEB_MICROGRAVITY2 = "m.sf.web.microgravity2";
    public static final String W_M_SF_SLOW_WEB = "m.sf.web.slowfall";

    // oddSlope
    public static final String W_M_SF_SLOPE1 = "m.sf.slope1";
    public static final String W_M_SF_SLOPE2 = "m.sf.slope2";

    // oddLiquid
    public static final String W_M_SF_ODDLIQUID_1 = "m.sf.oddliquid.1";
    public static final String W_M_SF_ODDLIQUID_2 = "m.sf.oddliquid.2";
    public static final String W_M_SF_ODDLIQUID_3 = "m.sf.oddliquid.3";
    public static final String W_M_SF_ODDLIQUID_4 = "m.sf.oddliquid.4";
    public static final String W_M_SF_ODDLIQUID_5 = "m.sf.oddliquid.5";
    public static final String W_M_SF_ODDLIQUID_6 = "m.sf.oddliquid.6";
    public static final String W_M_SF_ODDLIQUID_7 = "m.sf.oddliquid.7";
    public static final String W_M_SF_ODDLIQUID_8 = "m.sf.oddliquid.8";
    public static final String W_M_SF_ODDLIQUID_9 = "m.sf.oddliquid.9";
    public static final String W_M_SF_ODDLIQUID_10 = "m.sf.oddliquid.10";
    public static final String W_M_SF_ODDLIQUID_11 = "m.sf.oddliquid.11";
    public static final String W_M_SF_ODDLIQUID_12 = "m.sf.oddliquid.12";
    public static final String W_M_SF_ODDLIQUID_13 = "m.sf.oddliquid.13";
    public static final String W_M_SF_ODDLIQUID_14 = "m.sf.oddliquid.14";
    public static final String W_M_SF_ODDLIQUID_15 = "m.sf.oddliquid.15";
    public static final String W_M_SF_ODDLIQUID_16 = "m.sf.oddliquid.16";

    // oddGravity
    public static final String W_M_SF_ODDGRAVITY_1 = "m.sf.oddgravity.1";
    public static final String W_M_SF_ODDGRAVITY_2 = "m.sf.oddgravity.2";
    public static final String W_M_SF_ODDGRAVITY_3 = "m.sf.oddgravity.3";
    public static final String W_M_SF_ODDGRAVITY_4 = "m.sf.oddgravity.4";
    public static final String W_M_SF_ODDGRAVITY_5 = "m.sf.oddgravity.5";
    public static final String W_M_SF_ODDGRAVITY_6 = "m.sf.oddgravity.6";
    public static final String W_M_SF_ODDGRAVITY_7 = "m.sf.oddgravity.7";
    public static final String W_M_SF_ODDGRAVITY_8 = "m.sf.oddgravity.8";
    public static final String W_M_SF_ODDGRAVITY_VEL_1 = "m.sf.oddgravity.vel.1";
    public static final String W_M_SF_ODDGRAVITY_VEL_2 = "m.sf.oddgravity.vel.2";
    public static final String W_M_SF_ODDGRAVITY_VEL_3 = "m.sf.oddgravity.vel.3";
    public static final String W_M_SF_ODDGRAVITY_VEL_4 = "m.sf.oddgravity.vel.4";
    public static final String W_M_SF_ODDGRAVITY_VEL_5 = "m.sf.oddgravity.vel.5";
    public static final String W_M_SF_ODDGRAVITY_VEL_6 = "m.sf.oddgravity.vel.6";
    public static final String W_M_SF_ODDGRAVITY_SETBACK = "m.sf.oddgravity.setback";
    public static final String W_M_SF_ODDGRAVITY_JUMPEFFECT = "m.sf.oddgravity.jumpeffect";
    public static final String W_M_SF_ODDGRAVITY_NEAR_0 = "m.sf.oddgravity.near.0";

    // Sf-related not categorized.
    public static final String W_M_SF_OUT_OF_ENVELOPE_NODATA1 = "m.sf.out.of.envelope.nodata1";
    public static final String W_M_SF_OUT_OF_ENVELOPE_NODATA2 = "m.sf.out.of.envelope.nodata2";
    public static final String W_M_SF_OUT_OF_ENVELOPE_NODATA3 = "m.sf.out.of.envelope.nodata3";
    public static final String W_M_SF_OUT_OF_ENVELOPE_NODATA4 = "m.sf.out.of.envelope.nodata4";
    public static final String W_M_SF_ACCEPTED_ENV = "m.sf.accepted.env";

    // oddFriction
    public static final String W_M_SF_ODDFRICTION_1 = "m.sf.oddfriction.1";
    public static final String W_M_SF_ODDFRICTION_2 = "m.sf.oddfriction.2";
    public static final String W_M_SF_ODDFRICTION_3 = "m.sf.oddfriction.3";
    public static final String W_M_SF_ODDFRICTION_4 = "m.sf.oddfriction.4";
    public static final String W_M_SF_ODDFRICTION_5 = "m.sf.oddfriction.5";
    public static final String W_M_SF_ODDFRICTION_6 = "m.sf.oddfriction.6";
    public static final String W_M_SF_ODDFRICTION_7 = "m.sf.oddfriction.7";

    // fast falling (Sf)
    public static final String W_M_SF_FASTFALL_1 = "m.sf.fastfall.1";
    public static final String W_M_SF_FASTFALL_2 = "m.sf.fastfall.2";
    public static final String W_M_SF_FASTFALL_3 = "m.sf.fastfall.3";
    public static final String W_M_SF_FASTFALL_4 = "m.sf.fastfall.4";
    public static final String W_M_SF_FASTFALL_5 = "m.sf.fastfall.5";
    public static final String W_M_SF_FASTFALL_6 = "m.sf.fastfall.6";
    public static final String W_M_SF_FASTFALL_7 = "m.sf.fastfall.7";

    // Bigger move than expected yDistDiffEx > 0.0
    public static final String W_M_SF_OUT_OF_ENVELOPE_1 = "m.sf.out.of.envelope.1";
    public static final String W_M_SF_OUT_OF_ENVELOPE_2 = "m.sf.out.of.envelope.2";
    public static final String W_M_SF_OUT_OF_ENVELOPE_3 = "m.sf.out.of.envelope.3";
    public static final String W_M_SF_OUT_OF_ENVELOPE_4 = "m.sf.out.of.envelope.4";

    // Short move
    public static final String W_M_SF_SHORTMOVE_1 = "m.sf.shortmove.1";
    public static final String W_M_SF_SHORTMOVE_2 = "m.sf.shortmove.2";
    public static final String W_M_SF_SHORTMOVE_3 = "m.sf.shortmove.3";
    public static final String W_M_SF_SHORTMOVE_4 = "m.sf.shortmove.4";

    // TODO: LiquidWorkarounds as well?

    // Vehicle: oddInAirDescend
    /**
     * Vehicle descending in-air, skip one vehicle move event during late in-air
     * phase.
     */
    public static final String W_M_V_ENV_INAIR_SKIP = "m.v.env.inair.skip";
    /** Just a counter for back to surface for in-water moves (water-water). */
    public static final String W_M_V_ENV_INWATER_BTS = "m.v.env.inwater.bts";

    
    
    
    ///////////////////////
    // Group ids.
    ///////////////////////
    // MOVING_SURVIVALFLY
    /**
     * Group: Reset when not in air jump phase. Both used for players and
     * vehicles with players inside.
     */
    public static final String G_RESET_NOTINAIR = "reset.notinair";

    
    
    
    ///////////////////////
    // WorkaroundSet ids.
    ///////////////////////
    // MOVING
    /** WorkaroundSet: for use in MovingData. */
    public static final String WS_MOVING = "moving";

    
    
    
    public WRPT() {
        // Fill in blueprints, groups, workaround sets.

        // MOVING
        final Collection<IWorkaround> ws_moving = new LinkedList<IWorkaround>();

        // MOVING_SURVIVALFLY
        // Reset once on ground or reset-condition.
        final WorkaroundCountDown[] resetNotInAir = new WorkaroundCountDown[] {
                new WorkaroundCountDown(W_M_SF_SLIME_JP_2X0, 1),
                new WorkaroundCountDown(W_M_V_ENV_INAIR_SKIP, 1),
                new WorkaroundCountDown(W_M_SF_ODDGRAVITY_3, 1),
                new WorkaroundCountDown(W_M_SF_ODDLIQUID_13, 1),
                new WorkaroundCountDown(W_M_SF_ODDLIQUID_7, 1),
                new WorkaroundCountDown(W_M_SF_ODDLIQUID_5, 4),
                new WorkaroundCountDown(W_M_SF_ODDLIQUID_12, 1),
        };
        ws_moving.addAll(Arrays.asList(resetNotInAir));
        setWorkaroundBluePrint(resetNotInAir);
        setGroup(G_RESET_NOTINAIR, resetNotInAir);

        // Just counters.
        final String[] counters = new String[] {
                // Player
                // vEnv
                W_M_SF_WEB_0V1,
                W_M_SF_WEB_0V2,
                W_M_SF_WEB_MICROGRAVITY1,
                W_M_SF_WEB_MICROGRAVITY2,
                W_M_SF_SLOW_WEB,
                // Slope
                W_M_SF_SLOPE1,
                W_M_SF_SLOPE2,
                // OddLiquid
                W_M_SF_ODDLIQUID_1,
                W_M_SF_ODDLIQUID_2,
                W_M_SF_ODDLIQUID_3,
                W_M_SF_ODDLIQUID_4,
                W_M_SF_ODDLIQUID_5,
                W_M_SF_ODDLIQUID_6,
                W_M_SF_ODDLIQUID_7,
                W_M_SF_ODDLIQUID_8,
                W_M_SF_ODDLIQUID_9,
                W_M_SF_ODDLIQUID_10,
                W_M_SF_ODDLIQUID_11,
                W_M_SF_ODDLIQUID_12,
                W_M_SF_ODDLIQUID_13,
                W_M_SF_ODDLIQUID_14,
                W_M_SF_ODDLIQUID_15,
                W_M_SF_ODDLIQUID_16,
                // OddGravity
                W_M_SF_ODDGRAVITY_1,
                W_M_SF_ODDGRAVITY_2,
                W_M_SF_ODDGRAVITY_3,
                W_M_SF_ODDGRAVITY_4,
                W_M_SF_ODDGRAVITY_5,
                W_M_SF_ODDGRAVITY_6,
                W_M_SF_ODDGRAVITY_7,
                W_M_SF_ODDGRAVITY_8,
                W_M_SF_ODDGRAVITY_VEL_1,
                W_M_SF_ODDGRAVITY_VEL_2,
                W_M_SF_ODDGRAVITY_VEL_3,
                W_M_SF_ODDGRAVITY_VEL_4,
                W_M_SF_ODDGRAVITY_VEL_5,
                W_M_SF_ODDGRAVITY_VEL_6,
                W_M_SF_ODDGRAVITY_SETBACK,
                W_M_SF_ODDGRAVITY_JUMPEFFECT,
                W_M_SF_ODDGRAVITY_NEAR_0,
                // oddFriction
                W_M_SF_ODDFRICTION_1,
                W_M_SF_ODDFRICTION_2,
                W_M_SF_ODDFRICTION_3,
                W_M_SF_ODDFRICTION_4,
                W_M_SF_ODDFRICTION_5,
                W_M_SF_ODDFRICTION_6,
                W_M_SF_ODDFRICTION_7,
                // FastFalling
                W_M_SF_FASTFALL_1,
                W_M_SF_FASTFALL_2,
                W_M_SF_FASTFALL_3,
                W_M_SF_FASTFALL_4,
                W_M_SF_FASTFALL_5,
                W_M_SF_FASTFALL_6,
                W_M_SF_FASTFALL_7,
                // Shortmove
                W_M_SF_SHORTMOVE_1,
                W_M_SF_SHORTMOVE_2,
                W_M_SF_SHORTMOVE_3,
                W_M_SF_SHORTMOVE_4,
                // Outofenvelope
                W_M_SF_OUT_OF_ENVELOPE_1,
                W_M_SF_OUT_OF_ENVELOPE_2,
                W_M_SF_OUT_OF_ENVELOPE_3,
                W_M_SF_OUT_OF_ENVELOPE_4,
                // Misc. Sf-related
                W_M_SF_OUT_OF_ENVELOPE_NODATA1,
                W_M_SF_OUT_OF_ENVELOPE_NODATA2,
                W_M_SF_OUT_OF_ENVELOPE_NODATA3,
                W_M_SF_OUT_OF_ENVELOPE_NODATA4,
                W_M_SF_ACCEPTED_ENV,
                // Vehicle
                W_M_V_ENV_INWATER_BTS
        };
        for (final String id : counters) {
            final WorkaroundCounter counter = new WorkaroundCounter(id);
            ws_moving.add(counter);
            setWorkaroundBluePrint(counter);
        }

        // Finally register the set.
        setWorkaroundSetByIds(WS_MOVING, getCheckedIdSet(ws_moving), G_RESET_NOTINAIR);

        // TODO: Command to log global and for players.
    }
}
