// VIE for SwiftUI - https://github.com/kosi-libs/VIE
//
// Copy the files of this directory into your iOS app, all of them, and change the import below to
// the name of the Kotlin framework your app links. That framework must export
// org.kodein.arch.vie:vie-mvi (`export(...)` in its `binaries.framework`), so that VIEViewModel and
// VIEHost are part of its header. Together, the files require iOS 17 (Observation) and a Swift 6.2
// toolchain (Xcode 26, for `isolated deinit`): VIEContainer.swift is the one that needs both.

import Shared // ← Replace with the name of your Kotlin framework.
import SwiftUI


extension View {

    /// Reacts to the signals of `scope` while this view is on screen: calls `perform` with each one,
    /// in order, and not before the previous call has returned. A Kotlin sealed hierarchy reaches
    /// Swift as distinct classes, so tell the signals apart with
    /// `switch signal { case let s as MyVIESignalX: … }`, and a `default`, which Swift cannot check
    /// for exhaustiveness.
    ///
    /// `perform` is the one of the latest rendering of the view, as `SignalEffect` calls the lambda
    /// of its latest composition: what it captures, a callback from the parent for instance, is
    /// never stale.
    ///
    /// It collects between the appearance and the disappearance of the view, as `SignalEffect` does
    /// between the composition and the disposal of a composable: an Effect emitted in between waits
    /// for the view to come back, an Event emitted with no view on screen is lost, and so is an
    /// Effect that the view is handling when it disappears, before `perform` returned, together
    /// with the Effects already queued behind it.
    ///
    /// The collection restarts if the view is given another scope.
    ///
    /// `perform` runs in the task of the view, so it is cancelled when the view disappears. As any
    /// cancellation in Swift, it is cooperative: a `perform` that never awaits something
    /// cancellable and never checks `Task.isCancelled` still runs to its end. The next signal is
    /// not handed to it either way.
    ///
    /// It works on any `VIEScope`: the one `VIEContainer` gives, or `.empty` in a preview, which
    /// never signals. A scope wrapped by hand around the Kotlin scope of another screen makes the
    /// first signal fail a precondition, since Kotlin erases the signal type and Swift can only
    /// check it when the signal arrives.
    func onVIESignal<Intent: VIEIntent, Signal: VIESignal>(
        of scope: VIEScope<Intent, Signal>,
        perform onSignal: @escaping @MainActor (Signal) async -> Void
    ) -> some View {
        modifier(VIESignalModifier(scope: scope, perform: onSignal))
    }
}


/// Holds the `perform` of the latest rendering. A plain class, not observable: writing it while a
/// body is computed invalidates nothing, and the collection, started once, reads it at each signal.
@MainActor
private final class LatestPerform<Signal> {
    var perform: (@MainActor (Signal) async -> Void)?
}


private struct VIESignalModifier<Intent: VIEIntent, Signal: VIESignal>: ViewModifier {

    let scope: VIEScope<Intent, Signal>
    let perform: @MainActor (Signal) async -> Void

    // `@State` keeps the same box across the renderings of the view: this is what the collection,
    // started once, reads.
    @State private var latest = LatestPerform<Signal>()

    func body(content: Content) -> some View {
        latest.perform = perform
        let latest = latest
        // The identity of the Kotlin scope restarts the collection when the view is kept by SwiftUI
        // but given another scope: it would otherwise go on collecting the old one, whose ViewModel
        // may be cleared. The base scope is always a Kotlin object.
        return content.task(id: ObjectIdentifier(scope.base as AnyObject)) {
            await collectVIESignals(of: scope.base) { signal in
                guard let signal = signal as? Signal else {
                    preconditionFailure(
                        "\(type(of: signal)) is not a \(Signal.self): this VIEScope wraps the scope of another screen"
                    )
                }
                await latest.perform?(signal)
            }
        }
    }
}


/// A signal Kotlin handed over, and the `done` callback to call once it is handled. Both only ever
/// move on the main thread, where Kotlin calls back and where the collection runs.
private struct PendingSignal: @unchecked Sendable {
    let signal: any VIESignal
    let done: () -> Any?
}


/// Collects the signals of `scope` until the calling task is cancelled, handling each one in that
/// task: cancelling it cancels `onSignal` too, and stops the collection.
@MainActor
private func collectVIESignals(
    of scope: any VIEViewScope,
    _ onSignal: @escaping @MainActor (any VIESignal) async -> Void
) async {
    // Made before `collectSignals`, which may call back before it returns. Kotlin hands over the
    // next signal only after `done`, so the stream holds one at most.
    let (pending, continuation) = AsyncStream.makeStream(of: PendingSignal.self)
    let collection = VIESignalsKt.collectSignals(scope) { signal, done in
        continuation.yield(PendingSignal(signal: signal, done: { done() }))
    }
    defer {
        collection.cancel()
        continuation.finish()
    }
    // The loop ends when the calling task is cancelled, even while it waits for a signal.
    for await next in pending {
        await onSignal(next.signal)
        // A handler cancelled with the view must not release the next signal: Kotlin would hand it
        // over to a collection that is going away.
        // Qualified: a Kotlin framework can export a class named Task.
        if _Concurrency.Task.isCancelled { break }
        _ = next.done()
    }
}
