// VIE for SwiftUI - https://github.com/kosi-libs/VIE
//
// Copy the files of this directory into your iOS app, and change the import below to the name of
// the Kotlin framework your app links. That framework must export org.kodein.arch.vie:vie-mvi
// (`export(...)` in its `binaries.framework`), so that VIEViewModel and VIEHost are part of its
// header. Requires iOS 17 (Observation).

import Shared // ← Replace with the name of your Kotlin framework.
import SwiftUI


extension View {

    /// Reacts to the signals of `scope` while this view is on screen: calls `perform` with each one,
    /// in order, and not before the previous call has returned. A Kotlin sealed hierarchy reaches
    /// Swift as distinct classes, so tell the signals apart with
    /// `switch signal { case let s as MyVIESignalX: … }`, and a `default`, which Swift cannot check
    /// for exhaustiveness.
    ///
    /// It collects between the appearance and the disappearance of the view, as `SignalEffect` does
    /// between the composition and the disposal of a composable: an Effect emitted in between waits
    /// for the view to come back, an Event emitted with no view on screen is lost, and so is an
    /// Effect that the view is handling when it disappears, before `perform` returned, together
    /// with the Effects already queued behind it.
    ///
    /// It works on any `VIEScope`: the one `VIEContainer` gives, or `.empty` in a preview, which
    /// never signals. A scope wrapped by hand around the Kotlin scope of another screen makes the
    /// first signal fail a precondition, since Kotlin erases the signal type and Swift can only
    /// check it when the signal arrives.
    func onVIESignal<Intent: VIEIntent, Signal: VIESignal>(
        of scope: VIEScope<Intent, Signal>,
        perform onSignal: @escaping @MainActor (Signal) async -> Void
    ) -> some View {
        task {
            await collectVIESignals(of: scope.base) { signal in
                guard let signal = signal as? Signal else {
                    preconditionFailure(
                        "\(type(of: signal)) is not a \(Signal.self): this VIEScope wraps the scope of another screen"
                    )
                }
                await onSignal(signal)
            }
        }
    }
}


/// Collects the signals of `scope` until the calling task is cancelled.
@MainActor
private func collectVIESignals(
    of scope: any VIEViewScope,
    _ onSignal: @escaping @MainActor (any VIESignal) async -> Void
) async {
    let collection = VIESignalsKt.collectSignals(scope) { signal, done in
        // Qualified: a Kotlin framework can export a class named Task.
        _Concurrency.Task { @MainActor in
            await onSignal(signal)
            // A Kotlin `() -> Unit` passed to a block returns KotlinUnit.
            _ = done()
        }
    }
    // Suspends until the calling task is cancelled, which makes the sleep throw.
    try? await _Concurrency.Task.sleep(nanoseconds: .max)
    collection.cancel()
}
