package tech.hezy.glassalarmslider

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf

@Stable
class GlassAlarmSettings {
    private val values: Array<MutableFloatState> = Array(GlassParameter.entries.size) { index ->
        mutableFloatStateOf(GlassParameter.entries[index].defaultValue)
    }
    private val revisionState = mutableIntStateOf(0)

    internal val revision: Int
        get() = revisionState.intValue

    operator fun get(parameter: GlassParameter): Float = values[parameter.ordinal].floatValue

    operator fun set(parameter: GlassParameter, value: Float) {
        require(value.isFinite()) { "Glass parameter values must be finite." }
        val next = value.coerceIn(parameter.min, parameter.max)
        val state = values[parameter.ordinal]
        if (state.floatValue != next) {
            state.floatValue = next
            revisionState.intValue++
        }
    }

    fun reset() {
        var changed = false
        for (parameter in GlassParameter.entries) {
            val state = values[parameter.ordinal]
            if (state.floatValue != parameter.defaultValue) {
                state.floatValue = parameter.defaultValue
                changed = true
            }
        }
        if (changed) revisionState.intValue++
    }
}
