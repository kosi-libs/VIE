// VIE for SwiftUI - https://github.com/kosi-libs/VIE
//
// Copy the files of this directory into your iOS app, and change the import below to the name of
// the Kotlin framework your app links. That framework must export org.kodein.arch.vie:vie-mvi
// (`export(...)` in its `binaries.framework`), so that VIEViewModel and VIEHost are part of its
// header. Requires iOS 17 (Observation) and a Swift 6.2 toolchain (Xcode 26, for `isolated deinit`).

import Observation
import Shared // ← Replace with the name of your Kotlin framework.
import SwiftUI


/// The state of a VIE screen in SwiftUI: owns a `VIEViewModel` through a Kotlin `VIEHost`, exposes
/// its current `model` to SwiftUI's observation, and gives the `scope` a view posts intents to.
///
/// The ViewModel is created on the first read of `model`, and cleared when this object is
/// deinitialized. Like `VIEHost`, which it wraps, it lives on the main actor, `deinit` included:
/// whichever thread releases the last reference, the cleanup runs on the main thread.
///
/// SwiftUI evaluates a view's `@State` initial value every time the parent re-creates the view,
/// but keeps only the first one: the discarded ones never read `model`, so they never create a
/// ViewModel.
///
/// It is private to this file: `VIEContainer` holds one in its `@State`, and is the only way to get
/// one.
@MainActor
fileprivate final class VIEState<Model: VIEModel, Intent: VIEIntent, Signal: VIESignal>: Observable {

    // Observable by hand rather than with @Observable: the first read of `model` stores the model
    // without notifying, since it happens while SwiftUI is computing a body.
    private let registrar = ObservationRegistrar()
    private let host: VIEHost<Model, Intent, Signal>
    private var current: Model?
    private var modelObservation: VIECancellable?

    init(newVM: @escaping () -> VIEViewModel<Model, Intent, Signal>) {
        host = VIEHost(newVM: newVM)
    }

    isolated deinit {
        modelObservation?.cancel()
        host.clear()
    }

    /// The current model. Always complete: there is no "nothing yet" state.
    var model: Model {
        registrar.access(self, keyPath: \.model)
        if let current { return current }
        let first = host.model
        current = first
        // Kotlin calls back with the current model first: the identical instance is no change.
        modelObservation = host.observeModels { [weak self] model in
            guard let self, model !== self.current else { return }
            self.registrar.withMutation(of: self, keyPath: \.model) {
                self.current = model
            }
        }
        return first
    }

    /// What a view sees of the ViewModel: post intents to it, and collect its signals with
    /// `onVIESignal(of:perform:)`. Never the ViewModel itself.
    var scope: VIEScope<Intent, Signal> { VIEScope(host.scope) }
}


/// The single entry point for rendering a VIE screen in SwiftUI: creates the ViewModel with
/// `newVM`, once for as long as this view keeps its SwiftUI identity, and renders `content` with
/// the current model and the scope to post intents to.
///
/// `content` is best a view of its own that knows nothing but a `model` and a `scope`, so that a
/// preview can render it against `VIEScope.empty`:
///
/// ```swift
/// VIEContainer(newVM: { CounterVM(store: store) }) { model, scope in
///     CounterContent(model: model, scope: scope)
/// }
///
/// typealias CounterScope = VIEScope<CounterVIEIntent, CounterVIESignal>
///
/// struct CounterContent: View {
///     let model: CounterVIEModel
///     let scope: CounterScope
///     var body: some View {
///         Button("Count: \(model.count)") { scope.post(CounterVIEIntentIncrement.shared) }
///             .onVIESignal(of: scope) { signal in
///                 if signal is CounterVIESignalSaved { showSaved() }
///             }
///     }
/// }
///
/// #Preview {
///     CounterContent(model: CounterVIEModel(count: 3), scope: .empty)
/// }
/// ```
///
/// To get a new ViewModel, give the view a new identity with `.id(…)`.
///
/// `Model`, `Intent` and `Signal` are bound to the VIE marker protocols to state the contract. Swift
/// does not enforce it at the call site: Kotlin/Native drops the bounds of a type parameter when it
/// exports to Objective-C, so `VIEHost<NSString, …>` still compiles.
struct VIEContainer<Model: VIEModel, Intent: VIEIntent, Signal: VIESignal, Content: View>: View {

    @State private var state: VIEState<Model, Intent, Signal>
    private let content: (Model, VIEScope<Intent, Signal>) -> Content

    init(
        newVM: @escaping () -> VIEViewModel<Model, Intent, Signal>,
        @ViewBuilder content: @escaping (Model, VIEScope<Intent, Signal>) -> Content
    ) {
        _state = State(initialValue: VIEState(newVM: newVM))
        self.content = content
    }

    var body: some View {
        content(state.model, state.scope)
    }
}
