package com.cobblespawners.utils

import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.mob.MobEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import java.util.EnumSet
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class WanderBackToSpawnerGoal(
    private val entity: MobEntity,
    private val spawnerCenter: Vec3d,
    private val speed: Double,
    private val settings: WanderingSettings, // Pass the whole settings object
    private val tickDelay: Int = 10 // How often to check (in ticks)
) : Goal() {

    // Use wanderDistance from settings for the allowed radius
    private val allowedRadius: Double = settings.wanderDistance.toDouble()
    private val allowedRadiusSquared = allowedRadius * allowedRadius
    private var targetPos: Vec3d? = null // Store the calculated target position
    private var ticksSinceCheck = entity.random.nextInt(tickDelay)


    init {
        // Ensure this goal controls movement exclusively
        controls = EnumSet.of(Control.MOVE)
    }

    /**
     * Check if the entity is outside the allowed radius based on wanderDistance.
     */
    override fun canStart(): Boolean {
        // Don't start if wandering is disabled in settings
        if (!settings.enabled) return false

        if (--ticksSinceCheck > 0) return false
        ticksSinceCheck = tickDelay

        val distanceSq = entity.pos.squaredDistanceTo(spawnerCenter)
        // Use the radius from WanderingSettings
        return distanceSq > allowedRadiusSquared
    }

    /**
     * Start the goal by stopping current movement and heading towards the appropriate target.
     */
    override fun start() {
        // Stop any ongoing navigation immediately
        entity.navigation.stop()

        // Determine the target based on wanderType
        targetPos = if (settings.wanderType.equals("RADIUS", ignoreCase = true)) {
            // Find a random point within the allowed radius
            findRandomTargetInRadius()
        } else {
            // Default to SPAWNER type: return to the exact center
            spawnerCenter
        }

        // Start moving to the calculated target position
        if (targetPos != null) {
            // Attempt pathfinding to the target position
            val path = entity.navigation.findPathTo(targetPos!!.x, targetPos!!.y, targetPos!!.z, 0) // 0 range means find path *to* the point
            if (path != null && path.reachesTarget()) {
                entity.navigation.startMovingAlong(path, speed)
            } else {
                // Fallback if path is bad or null: Move directly, might get stuck
                entity.navigation.startMovingTo(targetPos!!.x, targetPos!!.y, targetPos!!.z, speed)
                // Consider logging a warning here if pathfinding fails often
            }
        } else {
            // If targetPos is null (e.g., failed random point generation), maybe default to spawner center?
            entity.navigation.startMovingTo(spawnerCenter.x, spawnerCenter.y, spawnerCenter.z, speed)
            // Consider logging a warning here
        }
    }

    /**
     * Calculates a random position within the allowed radius from the spawner center.
     * Tries to find a safe Y-level.
     */
    private fun findRandomTargetInRadius(): Vec3d? {
        for (i in 0..9) { // Try up to 10 times to find a valid point
            val randomAngle = entity.random.nextDouble() * 2.0 * Math.PI
            // Use sqrt for more uniform distribution within the circle area
            val randomDist = sqrt(entity.random.nextDouble()) * allowedRadius

            val offsetX = cos(randomAngle) * randomDist
            val offsetZ = sin(randomAngle) * randomDist

            val potentialX = spawnerCenter.x + offsetX
            val potentialZ = spawnerCenter.z + offsetZ

            // Try to find a suitable Y level using the entity's current world
            // Use surface heightmap or entity's Y as a fallback
            val targetBlockPos = BlockPos.ofFloored(potentialX, entity.y, potentialZ) // Start search around entity's Y
            val targetY = entity.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, targetBlockPos.x, targetBlockPos.z)

            val potentialTarget = Vec3d(potentialX, targetY.toDouble(), potentialZ)

            // Basic check: ensure the target is within the radius (should always be true by calculation)
            // More complex checks could be added here (e.g., pathfinding feasibility)
            if (potentialTarget.squaredDistanceTo(spawnerCenter) <= allowedRadiusSquared) {
                // A simple check to see if pathfinding to this point is even possible
                if (entity.navigation.findPathTo(potentialTarget.x, potentialTarget.y, potentialTarget.z, 0) != null) {
                    return potentialTarget
                }
            }
        }
        // If 10 attempts failed, return null (will default to spawner center in start())
        println("Warning: Could not find suitable random point in radius for entity ${entity.uuidAsString}, returning to spawner center.") // Or use your logger
        return null
    }


    /**
     * Continue the goal until the entity is back within radius AND navigation finishes.
     * Note: We check against the originally calculated target position's completion.
     */
    override fun shouldContinue(): Boolean {
        // Don't continue if wandering is disabled
        if (!settings.enabled) return false

        // Stop if navigation is idle (meaning it reached the target or gave up)
        // OR if the entity somehow got back inside the radius *before* reaching the target
        // (e.g., pushed by another entity). Let other goals take over then.
        val isInsideRadius = entity.pos.squaredDistanceTo(spawnerCenter) <= allowedRadiusSquared
        val navigationIdle = entity.navigation.isIdle

        // Continue if navigation is NOT idle AND the entity is still outside the radius
        // OR if navigation is NOT idle and the target was the spawner center (ensure it fully returns)
        // OR if navigation is NOT idle and target was random (ensure it reaches the random point)
        return !navigationIdle // Keep going until navigation completes or fails
        // Add this condition if you want it to stop *immediately* upon re-entering the radius,
        // even if it hasn't reached the calculated target yet.
        // && entity.pos.squaredDistanceTo(spawnerCenter) > allowedRadiusSquared
    }

    /**
     * Stop the goal and halt navigation.
     */
    override fun stop() {
        // Clear the target and stop navigating
        targetPos = null
        entity.navigation.stop()
    }
}