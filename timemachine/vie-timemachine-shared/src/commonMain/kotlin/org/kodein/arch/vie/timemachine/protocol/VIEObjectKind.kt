package org.kodein.arch.vie.timemachine.protocol

import kotlinx.serialization.Serializable


/**
 * The kind of VIE object being observed or intervened.
 *
 * This also implies a direction: a [Model] and a [Signal] are emitted *by* the ViewModel, while an
 * [Intent] is received *by* it.
 */
@Serializable
public enum class VIEObjectKind {
    Model,
    Intent,
    Signal,
}
