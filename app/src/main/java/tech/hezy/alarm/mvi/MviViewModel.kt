package tech.hezy.alarm.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

abstract class MviViewModel<S, I>(initial: S) : ViewModel() {
    private val states = MutableStateFlow(initial)
    val state: StateFlow<S> = states.asStateFlow()

    fun dispatch(intent: I) = reduce(intent)

    protected fun update(block: S.() -> S) {
        states.update(block)
    }

    protected abstract fun reduce(intent: I)
}
