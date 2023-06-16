package fr.neatmonster.nocheatplus.checks.moving.envelope;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.moving.Magic;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;

/**
 * Auxiliary methods for moving behaviour modeled after the client or otherwise observed on the server-side.
 */
public class PlayerEnvelopes {

	/**
	 * Jump off the top off a block with the ordinary jumping envelope, however
	 * from a slightly higher position with the initial gain being lower than
	 * typical, but the following move having the y distance as if jumped off
	 * with typical gain.
	 * 
	 * @param yDistance
	 * @param maxJumpGain
	 * @param thisMove
	 * @param lastMove
	 * @param data
	 * @return
	 */
	public static boolean noobJumpsOffTower(final double yDistance, final double maxJumpGain, 
	                                        final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data) {
	    final PlayerMoveData secondPastMove = data.playerMoves.getSecondPastMove();
	    return (
	            data.sfJumpPhase == 1 && lastMove.touchedGroundWorkaround // TODO: Not observed though.
	            || data.sfJumpPhase == 2 && Magic.inAir(lastMove)
	            && secondPastMove.valid && secondPastMove.touchedGroundWorkaround
	            )
	            && Magic.inAir(thisMove)
	            && lastMove.yDistance < maxJumpGain && lastMove.yDistance > maxJumpGain * 0.67
	            && PlayerEnvelopes.fallingEnvelope(yDistance, maxJumpGain, data.lastFrictionVertical, Magic.GRAVITY_SPAN);
	}

	/**
	 * Test if this + last 2 moves are within the gliding envelope (elytra), in
	 * this case with horizontal speed gain.
	 * 
	 * @param thisMove
	 * @param lastMove
	 * @param pastMove1
	 *            Is checked for validity in here (needed).
	 * @return
	 */
	public static boolean glideEnvelopeWithHorizontalGain(final PlayerMoveData thisMove, final PlayerMoveData lastMove, final PlayerMoveData pastMove1) {
	    return pastMove1.toIsValid 
	            && PlayerEnvelopes.glideVerticalGainEnvelope(thisMove.yDistance, lastMove.yDistance)
	            && PlayerEnvelopes.glideVerticalGainEnvelope(lastMove.yDistance, pastMove1.yDistance)
	            && lastMove.hDistance > pastMove1.hDistance && thisMove.hDistance > lastMove.hDistance
	            && Math.abs(lastMove.hDistance - pastMove1.hDistance) < Magic.GLIDE_HORIZONTAL_GAIN_MAX
	            && Math.abs(thisMove.hDistance - lastMove.hDistance) < Magic.GLIDE_HORIZONTAL_GAIN_MAX
	            ;
	}

	/**
	 * Advanced glide phase vertical gain envelope.
	 * 
	 * @param yDistance
	 * @param previousYDistance
	 * @return
	 */
	public static boolean glideVerticalGainEnvelope(final double yDistance, final double previousYDistance) {
	    return  // Sufficient speed of descending.
	            yDistance < Magic.GLIDE_DESCEND_PHASE_MIN && previousYDistance < Magic.GLIDE_DESCEND_PHASE_MIN
	            // Controlled difference.
	            && yDistance - previousYDistance > Magic.GLIDE_DESCEND_GAIN_MAX_NEG 
	            && yDistance - previousYDistance < Magic.GLIDE_DESCEND_GAIN_MAX_POS;
	}

	/**
	 * Friction envelope testing, with a different kind of leniency (relate
	 * off-amount to decreased amount), testing if 'friction' has been accounted
	 * for in a sufficient but not necessarily exact way.<br>
	 * In the current shape this method is meant for higher speeds rather (needs
	 * a twist for low speed comparison).
	 * 
	 * @param thisMove
	 * @param lastMove
	 * @param friction
	 *            Friction factor to apply.
	 * @param minGravity
	 *            Amount to subtract from frictDist by default.
	 * @param maxOff
	 *            Amount yDistance may be off the friction distance.
	 * @param decreaseByOff
	 *            Factor, how many times the amount being off friction distance
	 *            must fit into the decrease from lastMove to thisMove.
	 * @return
	 */
	public static boolean enoughFrictionEnvelope(final PlayerMoveData thisMove, final PlayerMoveData lastMove, final double friction, 
	                                             final double minGravity, final double maxOff, final double decreaseByOff) {
	
	    // TODO: Elaborate... could have one method to test them all?
	    final double frictDist = lastMove.yDistance * friction - minGravity;
	    final double off = Math.abs(thisMove.yDistance - frictDist);
	    return off <= maxOff && Math.abs(thisMove.yDistance - lastMove.yDistance) <= off * decreaseByOff;
	}

	/**
	 * Test if the player is (well) within in-air falling envelope.
	 * 
	 * @param yDistance
	 * @param lastYDist
	 * @param lastFrictionVertical
	 * @param extraGravity Extra amount to fall faster.
	 * @return
	 */
	public static boolean fallingEnvelope(final double yDistance, final double lastYDist, 
	                                      final double lastFrictionVertical, final double extraGravity) {
	    if (yDistance >= lastYDist) {
	        return false;
	    }
	    final double frictDist = lastYDist * lastFrictionVertical - Magic.GRAVITY_MIN;
	    // Extra amount: distinguish pos/neg?
	    return yDistance <= frictDist + extraGravity && yDistance > frictDist - Magic.GRAVITY_SPAN - extraGravity;
	}

	/**
	 * Test if this move is a bunnyhop <br>
	 * (Aka: sprint-jump. Increases the player's speed up to roughly twice the usual base speed)
	 * 
	 * @param data
	 * @param isOnGroundOpportune Checked only during block-change activity, via the block-change-tracker. 
	 * @param sprinting (Required for bunnyhop activation)
	 * @param sneaking
	 * @param fromOnGround
	 * @param toOnGround
	 * @return If true, a 10-ticks long countdown is activated (this phase is referred to as "bunnyfly")
	 *         during which, this method will return false if called, in order to prevent abuse of the speed boost.<br>
	 *         Cases where the player is allowed/able to bunnyhop sooner than usual are defined in SurvivalFly (hDistRel)
	 * 
	 */
	public static boolean isBunnyhop(final MovingData data, final boolean isOnGroundOpportune, boolean sprinting, boolean sneaking,
	                                 final boolean fromOnGround, final boolean toOnGround) {
	    final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
	    final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
	    
	    if (data.bunnyhopDelay > 0) {
	        // (Checked first)
	        // This bunnyfly phase hasn't ended yet. Too soon to apply the boost.
	        return false;
	    }
	    if (data.sfLowJump) {
	        // Blatant cheating: if low-jumping, deny the boost.
	        return false;
	    }
	    if (!sprinting) {
	        // This mechanic is applied only if the player is sprinting
	        return false;
	    }
	    //  if (sneaking) {
	    //      // Minecraft does not allow players to sprint and sneak at the same time.
	    //      return false;
	    //  }
	    if (fromOnGround && toOnGround && thisMove.yDistance > 0.0) {
	        // Lastly, don't allow players to boost themselves on stepping up blocks.
	        return false;
	    }
	
	    return 
	            // 0: Motion speed condition. Demand the player to hit the jumping envelope.
	            (
	                thisMove.yDistance > data.liftOffEnvelope.getJumpGain(data.jumpAmplifier, data.lastStuckInBlockVertical) - Magic.GRAVITY_SPAN
	                // Do note that this headObstructed check uses the step-correction leniency method, while the check used to determine
	                // if the bunnfly phase should be ended sooner (due to the head bump, see SurvivalFly#hdistChecks) does not.
	                || thisMove.headObstructed && thisMove.yDistance >= (0.1 * data.lastStuckInBlockVertical) // 0.1 seems to be the maximum jumping gain if head is obstructed within a 2-blocks high area.
	            )
	            // 0: Ground conditions
	            && (
	                // 1: Ordinary/obvious lift-off. Without any special mechanic, like BCT
	                data.sfJumpPhase == 0 && thisMove.from.onGround
	                // 1: Allow hop on lost-ground or if a past on-ground state can be found due to block change activity.
	                || data.sfJumpPhase <= 1 && (thisMove.touchedGroundWorkaround || isOnGroundOpportune) && !lastMove.bunnyHop
	            )
	        ;
	}

	/**
	 * Test if this movement is a jump.
	 * Minecraft does not offer a direct way to know if players could have jumped
	 * 
	 * @param data
	 * @param hasLevitation
	 * @param jumpGain The jump speed
	 * @return
	 */
	public static boolean isJump(final MovingData data, boolean hasLevitation, double jumpGain, boolean headObstructed) {
	    final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
	    if (hasLevitation) {
	    	return false;
	    }
	    if (headObstructed) {
	    	//TODO: Need Minecraft collision speed logic here.
	    	return MathUtil.almostEqual(thisMove.yDistance, 0.1 * data.lastStuckInBlockVertical, 0.0001)
	    	       && (thisMove.from.onGround && !thisMove.to.onGround || thisMove.touchedGroundWorkaround && data.sfJumpPhase <= 1);
	    }
	    return (thisMove.from.onGround && !thisMove.to.onGround || thisMove.touchedGroundWorkaround && data.sfJumpPhase <= 2) 
	           && thisMove.yDistance > 0.0 && MathUtil.almostEqual(thisMove.yDistance, jumpGain, 0.0001);
	}
    
    /**
     * Test if this was a step movement
     * Minecraft does not offer a direct way to know if players could have stepped up a block
     * 
     * @param data
     * @param stepHeight The step height (0.5, prior to 1.8, 0.6 from 1.8 and onwards)
     * @param couldStep
     * @return
     */
	public static boolean isStep(final MovingData data, double stepHeight, boolean couldStep) {
		final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
	    final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
		if (couldStep) {
			// Special case, allow it.
			return true;
		}
		return thisMove.from.onGround && thisMove.to.onGround && thisMove.yDistance > 0.0
		       && MathUtil.almostEqual(thisMove.yDistance, stepHeight, 0.0001);
	}

	/**
	 * First move after set back / teleport. Originally has been found with
	 * PaperSpigot for MC 1.7.10, however it also does occur on Spigot for MC
	 * 1.7.10.
	 * 
	 * @param thisMove
	 * @param lastMove
	 * @param data
	 * @return
	 */
	public static boolean skipPaper(final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data) {
	    // TODO: Confine to from at block level (offset 0)?
	    final double setBackYDistance;
	    if (data.hasSetBack()) {
	        setBackYDistance = thisMove.to.getY() - data.getSetBackY();
	    }
	    // Skip being all too forgiving here.
	    //        else if (thisMove.touchedGround) {
	    //            setBackYDistance = 0.0;
	    //        }
	    else {
	        return false;
	    }
	    return !lastMove.toIsValid && data.sfJumpPhase == 0 && thisMove.multiMoveCount > 0
	            && setBackYDistance > 0.0 && setBackYDistance < Magic.PAPER_DIST 
	            && thisMove.yDistance > 0.0 && thisMove.yDistance < Magic.PAPER_DIST && Magic.inAir(thisMove);
	}

	/**
	 * Pre conditions: A slime block is underneath and the player isn't really
	 * sneaking. This does not account for pistons pushing (slime) blocks.<br>
	 * 
	 * @param player
	 * @param from
	 * @param to
	 * @param data
	 * @param cc
	 * @return
	 */
	public static boolean checkBounceEnvelope(final Player player, final PlayerLocation from, final PlayerLocation to, 
	                                          final MovingData data, final MovingConfig cc, final IPlayerData pData) {
	    
	    // Workaround/fix for bed bouncing. getBlockY() would return an int, while a bed's maxY is 0.5625, causing this method to always return false.
	    // A better way to do this would to get the maxY through another method, just can't seem to find it :/
	    // Collect block flags at the current location as they may not already be there, and cause NullPointer errors.
	    to.collectBlockFlags();
	    double blockY = ((to.getBlockFlags() & BlockFlags.F_BOUNCE25) != 0) 
	                    && ((to.getY() + 0.4375) % 1 == 0) ? to.getY() : to.getBlockY();
	    return 
	            // 0: Normal envelope (forestall NoFall).
	            (
	                // 1: Ordinary.
	                to.getY() - blockY <= Math.max(cc.yOnGround, cc.noFallyOnGround)
	                // 1: With carpet.
	                || BlockProperties.isCarpet(to.getTypeId()) && to.getY() - to.getBlockY() <= 0.9
	            ) && MovingUtil.getRealisticFallDistance(player, from.getY(), to.getY(), data, pData) > 1.0
	            // 0: Within wobble-distance.
	            || to.getY() - blockY < 0.286 && to.getY() - from.getY() > -0.9
	            && to.getY() - from.getY() < -Magic.GRAVITY_MIN
	            && !to.isOnGround()
	       ;
	}

}
