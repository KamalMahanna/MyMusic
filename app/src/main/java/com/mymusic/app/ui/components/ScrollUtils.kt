package com.mymusic.app.ui.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.AnimationState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Creates and remembers a custom FlingBehavior that mimics iOS/BouncingScrollPhysics style
 * gliding. It caps the maximum initial velocity to prevent scrolling too far, while using
 * a lower friction decay multiplier to achieve a long, premium, and gradual slowing-down tail
 * that drifts smoothly all the way to 0 velocity.
 *
 * @param frictionMultiplier Lower values (e.g. 0.7f) allow the list to slide longer and slow
 *                           down very gradually at the end.
 */
@Composable
fun rememberFrictionFlingBehavior(frictionMultiplier: Float = 0.7f): FlingBehavior {
    val density = LocalDensity.current
    val maxVelocityPx = remember(density) {
        with(density) { 3500.dp.toPx() }
    }
    val velocityThreshold = remember(density) {
        with(density) { 30.dp.toPx() }
    }
    
    val decaySpec = remember(frictionMultiplier) {
        exponentialDecay<Float>(frictionMultiplier = frictionMultiplier)
    }
    
    return remember(decaySpec, maxVelocityPx, velocityThreshold) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                // Clamp maximum fling velocity to prevent flying too far with low friction
                val clampedVelocity = if (initialVelocity > 0) {
                    minOf(initialVelocity, maxVelocityPx)
                } else {
                    maxOf(initialVelocity, -maxVelocityPx)
                }
                
                var lastValue = 0f
                var velocityLeft = clampedVelocity
                
                AnimationState(
                    initialValue = 0f,
                    initialVelocity = clampedVelocity
                ).animateDecay(decaySpec) {
                    val delta = value - lastValue
                    val consumed = scrollBy(delta)
                    lastValue = value
                    velocityLeft = velocity
                    
                    // Cancel the fling animation if we hit the bounds of the list
                    if (abs(delta - consumed) > 0.5f) {
                        cancelAnimation()
                    } else if (abs(clampedVelocity) > velocityThreshold && abs(velocity) < velocityThreshold) {
                        // Cancel the fling animation early if velocity is extremely low
                        // to prevent the long tail from blocking subsequent clicks.
                        // We only cancel if we started above the threshold and have now decayed below it,
                        // and we return 0f to signal that the scroll has finished.
                        cancelAnimation()
                        velocityLeft = 0f
                    }
                }
                return velocityLeft
            }
        }
    }
}
