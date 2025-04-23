package com.cobblespawners.utils

import com.everlastingutils.utils.logDebug
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.mob.MobEntity
import net.minecraft.util.math.Vec3d
import java.util.EnumSet
import kotlin.math.sqrt

/**
 * Optimized wander-back goal with queued pathfinding to reduce CPU usage.
 * Entities farthest away have higher priority.
 */
class WanderBackToSpawnerGoal(
    private val entity: MobEntity,
    wanderingSettings: WanderingSettings?,
    private val spawnerCenter: Vec3d,
    private val speed: Double,
    private val tickDelay: Int = 55
) : Goal() {

    private val wanderMode: String = wanderingSettings?.wanderType ?: "RADIUS"
    private val allowedRadius: Double = (wanderingSettings?.wanderDistance ?: 6).toDouble()
    private val allowedRadiusSquared = allowedRadius * allowedRadius
    private val isRadiusMode = wanderMode.equals("RADIUS", ignoreCase = true)

    // Ticks since last pathfinding check
    private var ticksSinceCheck = entity.random.nextInt(tickDelay)

    init {
        controls = EnumSet.of(Control.MOVE)
    }

    /**
     * Determines if the goal should start based on distance and navigation status.
     */
    override fun canStart(): Boolean {
        if (--ticksSinceCheck > 0) return false
        ticksSinceCheck = tickDelay
        val distanceSq = entity.pos.squaredDistanceTo(spawnerCenter)
        val shouldStart = distanceSq > allowedRadiusSquared * 1.1 && entity.navigation.isIdle
        logDebug(
            "WanderBack check for ${entity.uuid}: distance=${sqrt(distanceSq)}, " +
                    "threshold=${sqrt(allowedRadiusSquared * 1.1)}, " +
                    "navIdle=${entity.navigation.isIdle}, shouldStart=$shouldStart",
            "cobblespawners"
        )
        return shouldStart
    }

    /**
     * Starts the goal, moving the entity to the spawner or teleporting if necessary.
     */
    override fun start() {
        logDebug("WanderBack started for ${entity.uuid}, moving to $spawnerCenter", "cobblespawners")
        val distanceSq = entity.pos.squaredDistanceTo(spawnerCenter)
        val teleportThresholdSq = (allowedRadius * 2) * (allowedRadius * 2)
        if (distanceSq > teleportThresholdSq && !entity.navigation.startMovingTo(spawnerCenter.x, spawnerCenter.y, spawnerCenter.z, speed)) {
            entity.setPosition(spawnerCenter.x, spawnerCenter.y, spawnerCenter.z)
            logDebug("Teleported ${entity.uuid} back to spawner due to pathfinding failure", "cobblespawners")
        } else {
            entity.navigation.startMovingTo(spawnerCenter.x, spawnerCenter.y, spawnerCenter.z, speed)
        }
    }

    /**
     * Checks if the goal should continue running.
     */
    override fun shouldContinue(): Boolean {
        val distanceSq = entity.pos.squaredDistanceTo(spawnerCenter)
        val shouldContinue = if (isRadiusMode) {
            !entity.navigation.isIdle && distanceSq > allowedRadiusSquared
        } else {
            !entity.navigation.isIdle
        }
        logDebug(
            "WanderBack continue for ${entity.uuid}: distance=${sqrt(distanceSq)}, " +
                    "threshold=$allowedRadius, navIdle=${entity.navigation.isIdle}, " +
                    "shouldContinue=$shouldContinue",
            "cobblespawners"
        )
        return shouldContinue
    }

    /**
     * Stops the goal and halts navigation.
     */
    override fun stop() {
        logDebug("WanderBack stopped for ${entity.uuid}", "cobblespawners")
        entity.navigation.stop()
    }
}