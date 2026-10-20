package org.kodein.arch.vie.timemachine.server.ui.core

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.kodein.arch.vie.VIE
import org.kodein.arch.vie.VIEViewModel


/**
 * A pane's ViewModel. Its model is its controls, which [process] sets on the main thread at once so a
 * text field never waits for a thread hop, plus a projection, a pure function of the store, the
 * selection and the controls, which [project] merges in as it lands.
 */
internal abstract class PaneViewModel<Model : VIE.Model, Intent : VIE.Intent, Signal : VIE.Signal>(
    firstModel: Model,
) : VIEViewModel<Model, Intent, Signal>(firstModel) {

    /** Merges each value [projections] emits into the model with [merge]. */
    protected fun <P> project(projections: Flow<P>, merge: Model.(P) -> Model) {
        viewModelScope.launch { projections.collect { projection -> update { merge(projection) } } }
    }
}
