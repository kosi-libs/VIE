// VIE for SwiftUI - https://github.com/kosi-libs/VIE
//
// Copy the files of this directory into your iOS app, all of them, and change the import below to
// the name of the Kotlin framework your app links. That framework must export
// org.kodein.arch.vie:vie-mvi (`export(...)` in its `binaries.framework`), so that VIEViewModel and
// VIEHost are part of its header. Together, the files require iOS 17 (Observation) and a Swift 6.2
// toolchain (Xcode 26, for `isolated deinit`): VIEContainer.swift is the one that needs both.

import Shared // ← Replace with the name of your Kotlin framework.


/// What a view sees of its ViewModel, typed with the intents it can post and the signals it can
/// receive: `scope.post(…)` takes an `Intent`, and `onVIESignal(of:perform:)` delivers a `Signal`.
///
/// Kotlin exports `VIEViewScope` to Swift as a protocol without type parameters, so
/// `any VIEViewScope` accepts any `VIEIntent`: an intent of another screen compiles, then fails at
/// run time on the Kotlin side, where generics are erased. This wrapper puts the check back at
/// compile time.
///
/// `VIEContainer` gives one. Name its type once per screen, or each view signature gets long:
///
/// ```swift
/// typealias CounterScope = VIEScope<CounterVIEIntent, CounterVIESignal>
/// ```
struct VIEScope<Intent: VIEIntent, Signal: VIESignal> {

    /// The Kotlin scope this wraps. Use `init(_:)` to wrap another one.
    let base: any VIEViewScope

    /// Wraps a Kotlin scope: a fake one in a test, for instance. The `Intent` and `Signal` types are
    /// not checked against it, they are the contract you promise the scope keeps.
    init(_ base: any VIEViewScope) {
        self.base = base
    }

    /// Sends `intent` up to the ViewModel for processing.
    func post(_ intent: Intent) {
        base.post(intent: intent)
    }

    /// A scope with nothing behind it, for a preview: `post` discards, and no signal ever fires.
    static var empty: Self {
        Self(EmptyVIE<Intent, Signal>())
    }
}
