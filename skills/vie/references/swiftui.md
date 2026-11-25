# VIE in SwiftUI

How a SwiftUI app uses VIE: the contract and the ViewModel stay in Kotlin, shared with the other
platforms, and the view is written in SwiftUI. VIE needs no Compose for that: `vie-mvi`, the core,
holds everything the iOS framework needs.

## What there is

| Where | What |
|---|---|
| `vie-mvi`, iosMain | `VIEHost` owns a `VIEViewModel` for a Swift view: creates it on first use, clears it on `clear()`. `VIEViewScope.collectSignals(onSignal)` turns the signals of a scope into callbacks. Swift can neither collect a `Flow` nor clear a ViewModel by itself. |
| `swift/VIEContainer.swift` | `VIEContainer(newVM:content:)`: the SwiftUI counterpart of `VIEView`, and the single entry point. The observable object that holds the ViewModel, `VIEState`, is private to the file. |
| `swift/VIEScope.swift` | `VIEScope<Intent, Signal>`: what a view sees of its ViewModel, typed with the intents it can post and the signals it receives. `VIEScope.empty` is the scope of a preview. |
| `swift/View+onVIESignal.swift` | `.onVIESignal(of:perform:)`: the SwiftUI counterpart of `SignalEffect`. |

The three Swift files are meant to be **copied into the app**, not depended on: a Swift package would
force the consumer to name its framework as the package imports it. Only the `import` line changes.

## Installing the helpers

The user asks for it ("install the VIE helpers for SwiftUI"). Do these steps in order, and stop where
a step says so.

1. **Find the framework.** The Gradle module whose `binaries.framework`, `XCFramework("…")` or
   `cocoapods { framework { … } }` builds the framework the Xcode app links. Note its `baseName`
   (or the `XCFramework` name): that is the name the Swift files import. Several candidates: ask.
2. **Make `vie-mvi` part of the framework header.** Only the declarations of an *exported* module
   reach it, so Swift could not name `VIEViewModel` or `VIEHost` otherwise. In the framework module:
   - `vie-mvi` must be an `api` dependency, and be passed to `export`. Use the same notation for both:
     `api("org.kodein.arch.vie:vie-mvi:<version>")` and `export("org.kodein.arch.vie:vie-mvi:<version>")`,
     or `api(libs.vie.mvi)` and `export(libs.vie.mvi)` with a version catalog, or `projects.…` for a
     project dependency.
   - Every other module whose classes the Swift code names, as the one that holds the contracts and
     the ViewModels, must be exported the same way.
   - Show the change and apply it; never touch a version the user pinned.
3. **Check the deployment target and the toolchain.** `IPHONEOS_DEPLOYMENT_TARGET` in the `.pbxproj`
   (or the `.iOS(…)` platform of a `Package.swift`) must be **17 or more**: the helpers use
   Observation. They also need a **Swift 6.2 toolchain, so Xcode 26 or more**, for `isolated deinit`:
   with an older one, `VIEContainer.swift` fails on an `#error` that says so. Below either,
   **stop** and tell the user; do not raise the target on your own.
4. **Choose the destination.** The directory of the app's other Swift files. In an Xcode project with
   folder-synchronised groups (`PBXFileSystemSynchronizedRootGroup`, the default since Xcode 16),
   copying the files there is enough. In a classic project the files must also be added to the app
   target: say so, and let the user add them in Xcode, or edit the `.pbxproj` only if they agree.
5. **Copy the files** of this skill's `swift/` directory, and change the line `import Shared` of each
   to `import <baseName>`. **Never overwrite** an existing file: ask first.
6. **Report** the files created, the Gradle lines added, and what the user still has to do (add the
   files to the target, resync the Gradle project, rebuild the framework).

## Writing a screen

Same split as in Compose: a container that owns the ViewModel, and a content view that knows nothing
but a `model` and a scope, so that a preview renders it against `VIEScope.empty`. Name the scope of
each screen with a `typealias`, or the signatures get long.

```swift
struct CounterScreen: View {
    var body: some View {
        VIEContainer(newVM: { CounterVM(store: store) }) { model, scope in
            CounterContent(model: model, scope: scope)
        }
    }
}

typealias CounterScope = VIEScope<CounterVIEIntent, CounterVIESignal>

struct CounterContent: View {
    let model: CounterVIEModel
    let scope: CounterScope

    var body: some View {
        Button("Count: \(model.count)") { scope.post(CounterVIEIntentIncrement.shared) }
            .onVIESignal(of: scope) { signal in
                switch signal {
                case is CounterVIESignalSaved: showSaved()
                default: break
                }
            }
    }
}

#Preview {
    CounterContent(model: CounterVIEModel(count: 3), scope: .empty)
}
```

To scaffold one, use `templates/Xxx.swift.template` with `{{Name}}` and `{{Framework}}` (the
`baseName` of step 1). Never overwrite an existing file.

## How Kotlin names arrive in Swift

| Kotlin | Swift |
|---|---|
| `CounterVIE.Model` | `CounterVIEModel` (nested names are joined) |
| `CounterVIE.Intent.Add(amount)`, a data class | `CounterVIEIntentAdd(amount:)` |
| `CounterVIE.Intent.Increment`, a data object | `CounterVIEIntentIncrement.shared` |
| `CounterVIE.Signal.Saved` | `CounterVIESignalSaved` |
| a property named `description` | `description_` (it would clash with `NSObject`) |
| `Uuid`, `Pair<A, B>`, `Unit` | `KotlinUuid`, `KotlinPair<A, B>` (its `first` and `second` are optional), `KotlinUnit` |
| `Int`, `Long` | `Int32`, `Int64` |

## Limits to know

- **Intents are typed, signals are cast.** Kotlin exports `VIEViewScope` to Swift as a protocol
  without type parameters, so `VIEScope` puts them back: `scope.post(_:)` takes the screen's own
  `Intent`, and an intent of another screen does not compile. A sealed hierarchy of signals still
  arrives as distinct classes: cast each one in a `switch`, and keep the `default`, as Swift cannot
  check that `switch` for exhaustiveness, unlike the `when` of a `SignalEffect`. A `VIEScope` that
  is wrapped by hand around the Kotlin scope of another screen fails a precondition at its first
  signal.
- **`onVIESignal` calls the closure of the latest rendering**, as `SignalEffect` does, and restarts
  its collection when the view is given another scope.
- **Signals follow the visibility of the view**, as `SignalEffect` follows the composition. An
  Effect emitted while the view is not on screen waits for it, an Event is lost, and an Effect
  that the view is handling when it disappears, before `perform` returned, is lost, together with
  the Effects already queued behind it. `perform` runs in the task of the view and is cancelled
  with it: cancellation is cooperative, so a `perform` that never awaits anything cancellable
  still runs to its end, though the next signal is not handed to it.
- **A Kotlin class named `Task` hides Swift's `Task`.** Write `_Concurrency.Task` for the latter, and
  `<framework>.Task` for the former.
- **Create the ViewModel in `newVM`, never in the `init` of a view.** SwiftUI creates views again and
  again, and keeps the state of the first one only: `VIEContainer` calls `newVM` once, when the model
  is first read.
- **A new ViewModel needs a new identity.** `VIEContainer(…).id(item.id)` for the item of a list.
- The helpers compile in the Swift 5 and Swift 6 language modes, with a Swift 6.2 toolchain.

## Mixing with Compose

A Compose screen can be one screen of a SwiftUI app, presented from a `UIViewControllerRepresentable`
that returns a `ComposeUIViewController`. Give that screen a `ViewModelStoreOwner` of its own, and
clear its store when the composition is disposed:

```kotlin
fun EditViewController(goBack: () -> Unit): UIViewController = ComposeUIViewController {
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { /* the screen */ }
}
```

The store that `ComposeUIViewController` provides is not cleared when SwiftUI dismisses the
controller (Compose Multiplatform 1.12.0), so without this the ViewModel outlives the screen.
