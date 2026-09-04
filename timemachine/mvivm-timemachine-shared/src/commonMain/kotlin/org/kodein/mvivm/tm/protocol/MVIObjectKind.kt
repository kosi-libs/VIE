package org.kodein.mvivm.tm.protocol

import kotlinx.serialization.Serializable


/**
 * The kind of MVI object being observed or injected.
 *
 * This also implies a direction: a [Model] and a [Signal] are emitted *by* the ViewModel, while an
 * [Intent] is received *by* it.
 */
@Serializable
public enum class MVIObjectKind {
    Model,
    Intent,
    Signal,
}
