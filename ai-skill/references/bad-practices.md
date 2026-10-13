# VIE bad practices — review checklist

For each item: the **smell** to look for, a **grep hint** to find candidates fast (not a
substitute for reading the code), **why** it hurts, and the **fix**. Apply judgment — flag only
what actually matches; don't invent style complaints VIE has no opinion on.

## 1. `Model` is not a `data class`, or has mutable state

**Smell:** a `VIE.Model` implementation declared as a plain `class`, or with `var` properties, or
with mutable collections (`MutableList`, `ArrayList`, …) as fields.

**Grep:** `: VIE\.Model\b` in the file, then check the enclosing declaration keyword and its
properties.

**Why:** the view relies on the model's *structural equality* to skip recomposition when an
update produces an equal model. A plain `class` uses identity equality — every `update {}` call
recomposes the view even when nothing visible changed. A `var` or mutable collection can be
mutated in place without going through `update {}` at all, breaking the serialization guarantee
`update` exists to provide.

**Fix:** `data class Model(val …) : VIE.Model`, immutable collections (`List`, not
`MutableList`).

## 2. `Intent` or `Signal` not `sealed`

**Smell:** `interface Intent : VIE.Intent` or `interface Signal : VIE.Signal` without `sealed`.

**Grep:** `interface (Intent|Signal) ?: VIE\.(Intent|Signal)`

**Why:** `process`'s `when (intent) { … }` and a view's `SignalEffect { when (signal) { … } }`
can no longer be exhaustive-checked by the compiler. A new intent or signal variant added later
silently falls through an `else` (or isn't caught until runtime).

**Fix:** `sealed interface Intent : VIE.Intent { … }` / `sealed interface Signal : VIE.Signal { … }`.

## 3. `process` or `SignalEffect` has an `else` (or other catch-all) branch

**Smell:** `process`'s `when (intent) { … else -> … }`, or `SignalEffect { signal -> when (signal)
{ … else -> … } }` — any catch-all branch instead of one arm per concrete subtype.

**Grep:** `else ->` inside `process`'s body or inside a `SignalEffect { }` block.

**Why:** since Kotlin 1.7, a non-exhaustive `when` over a `sealed` type is a compiler **error** —
whether the `when` is a statement (as in `process`) or an expression (as in `SignalEffect`'s
lambda). That's the entire value of writing them as `when` in the first place: add a new `Intent`
or `Signal` later without a matching branch, and the build fails until it's handled. An `else`
branch throws that guarantee away — a forgotten case compiles silently and does whatever the
`else` does (often nothing).

**Fix:** delete the `else`; enumerate every concrete subtype explicitly.

## 4. `process` or `SignalEffect` contains anything besides the `when`

**Smell:** `process`'s body has a statement before or after the `when (intent) { … }` — not
inside one of its branches, but alongside it — or `SignalEffect { signal -> … }`'s lambda body
has a statement before or after its `when (signal) { … }`.

**Grep:** `override suspend fun process` — check whether the function body is anything other
than a single `when` construct; `SignalEffect \{` — check whether the lambda body has more than
one top-level statement.

**Why:** `process` and `SignalEffect` exist to dispatch per intent/signal type. A statement
outside the `when` runs unconditionally, for every case — which makes it a cross-cutting concern,
not per-case handling, and VIE already has a dedicated home for that:
`VIEMiddleware.interceptIntents`/`interceptSignals`, tapping the flow with `onEach { … }` and
passing it through unchanged (the same shape `VIELoggingMiddleware` already uses). Left inline,
the same tracking/logging has to be re-added to every ViewModel or view that needs it, and it's
easy to miss, reading the `when`, that something runs on every case regardless of branch.

**Fix:** move the cross-cutting behavior into a `VIEMiddleware` (see `good-practices.md` §
"Middlewares" for the shape). If it genuinely can't be expressed as a middleware — it needs this
ViewModel's own private state, say — leaving it inline is fine, but only with a comment
explaining why it's there and why it isn't a middleware.

## 5. A concrete `Signal` implements neither `Event` nor `Effect`

**Smell:** `data class Foo(...) : Signal` with no `VIE.Signal.Event` / `VIE.Signal.Effect`
supertype.

**Grep:** look at every `: Signal` (or `Signal,`) declaration inside a `sealed interface Signal`
and check it also lists `Event` or `Effect`.

**Why:** `VIEViewModel.signal()` is bounded `where S : Signal, S : VIE.Signal.Type` — this
actually fails to *compile*, so it can't ship, but it's worth flagging early with a clear
message rather than letting the user hunt through a generic type-inference error.

**Fix:** add `, VIE.Signal.Event` or `, VIE.Signal.Effect` to the signal's supertype list, having
first decided which delivery semantics it needs (see #8).

## 6. A `@Composable` takes a ViewModel or the `VIE` contract type directly

**Smell:** a `@Composable` function parameter typed `XxxVM`, `XxxViewModel`, or the bare `XxxVIE`
contract interface (rather than `VIEViewScope<XxxVIE.Intent, XxxVIE.Signal>`).

**Grep:** `@Composable` functions whose parameter list contains `: \w*VM\b` or `: \w*VIE\b`
(excluding the `VIEView(newVM = { … })` wrapper itself, which legitimately constructs the VM).

**Why:** defeats the reason `VIEViewScope` exists. The view gains access to `modelFlow`,
`update`, `signal`, `process` — none of which it should call — and can no longer be rendered
against `EmptyVIE`/`MockVIE` for previews and tests, since those aren't `VIEViewModel`s.

**Fix:** make the inner composable an extension on `VIEViewScope<Intent, Signal>` taking `model`
plus plain callbacks, called from a thin wrapper that calls `VIEView`. See
`good-practices.md` § "The view".

**Not this:** a composable typed `model: Model, post: (Intent) -> Unit` — with no `VIEViewScope`
at all — is a deliberate, equally valid alternative for a screen whose `Signal` is
`NoSignal`, not an instance of this smell. This entry is specifically about a parameter
typed as the ViewModel or the `VIE` contract itself; see `good-practices.md` § "The view".

## 7. Manual `modelFlow.collectAsState()` instead of `VIEView`

**Smell:** `viewModel { … }` combined with `.modelFlow.collectAsState()` written out by hand at a
call site, instead of `VIEView(newVM = { … }) { model -> … }`.

**Grep:** `collectAsState()` near a `VIEViewModel` type, outside of `VIEView`'s own
implementation.

**Why:** re-implements `VIEView`'s entire job, and is usually found paired with #6 — the
hand-rolled version tends to keep the ViewModel reference around and hand it to child
composables.

**Fix:** use `VIEView`.

## 8. Navigation or a must-not-repeat action modeled as `Event`, observed by multiple collectors

**Smell:** a `Signal.Event` (not `Effect`) used for "navigate back", "show this dialog once", or
similar, where more than one view/composable can be collecting `signalFlow` for the same
ViewModel at once.

**Grep:** find the signal's declaration (`: Signal, VIE.Signal.Event`) and check every
`SignalEffect { }` site that could observe it.

**Why:** `Event` is broadcast to *every* active collector — two observers means the action fires
twice (e.g., navigating back twice).

**Fix:** switch that signal to `VIE.Signal.Effect` (exactly one collector).

## 9. A signal emitted before any collector is listening

**Smell:** `signal(...)` called from an `init` block, or synchronously right after `VIEViewModel`
construction — before the view has had a chance to compose and start its `SignalEffect`.

**Grep:** `signal(` inside `init {}`.

**Why:** if the signal is an `Event`, it is dropped outright (`signalFlow` has no replay buffer).
If it's an `Effect`, the emitting coroutine suspends on the underlying rendezvous channel until a
collector eventually shows up — the signal is delivered late, and any signal emitted after it
queues up behind it on the same channel.

**Fix:** don't signal from `init`. If the ViewModel must announce something as soon as it starts,
put it in the initial `Model` instead (e.g. a `loading`/`error` field), or delay the emission
until triggered by an intent the view sends once composed.

## 10. A view ignores a non-empty `Signal` type

**Smell:** a `XxxVIE.Signal` with one or more concrete members (i.e. not `NoSignal`), whose
corresponding view has no `SignalEffect { }` call anywhere. This is a cross-file check: read the
contract to see whether `Signal` is non-trivial, then read the view that renders it.

**Grep:** `sealed interface Signal : VIE.Signal { … }` with at least one member, then confirm the
view file(s) that take `VIEViewScope<XxxVIE.Intent, XxxVIE.Signal>` have no `SignalEffect(`.

**Why:** an `Event` emitted with nobody collecting is silently dropped; an `Effect` emitted with
nobody collecting leaves the emitting coroutine suspended forever, waiting for a collector that
will never arrive. Either way, a `Signal` type with real members and no view handling it is very
likely a bug, not a deliberate design.

**Fix:** add a `SignalEffect { }` with an exhaustive `when` (see #3, #4) to the view. If the
signals truly are meant to be handled elsewhere (a parent composable, an observer outside this
view), document that with a comment at the point signals are (not) handled, so the omission reads
as deliberate rather than missed. If nothing should ever react, the contract's `Signal` should be
`NoSignal` instead.

## 11. Work awaited directly in `process` that shouldn't block the intent stream

**Smell:** a slow or independent operation (e.g. a per-row action in a list, a background sync)
awaited with a plain suspend call inside `process`, with no `viewModelScope.launch { … }`.

**Grep:** `override suspend fun process` body — look for suspend calls not wrapped in
`viewModelScope.launch`.

**Why:** `process` handles one intent at a time; a slow call there stalls every subsequent intent
until it finishes. This is *not* always wrong — awaiting directly is correct when the next intent
genuinely should wait (e.g. a save that must finish before further edits are processed) — but
independent, concurrent operations (delete this row, update that row) need to run without
blocking each other.

**Fix:** `viewModelScope.launch { … }` around the independent work, updating the model with
`update {}` from inside the launched coroutine when it completes.

## 12. Suspending or non-trivial computation inside `update {}`

**Smell:** a suspend call (a repository/service call, `delay(…)`, …) or an expensive computation
written directly inside an `update { … }` lambda, rather than something that is purely a function
of the current model snapshot (`this`).

**Grep:** every `update {` block in the file — check whether its body calls anything suspending
or reaches out to a repository/service, versus just `copy(...)`/local computation over `this`.

**Why:** `update {}` calls are serialized through one dedicated consumer coroutine inside
`VIEViewModel`. A suspending or slow call placed *inside* the lambda blocks that single
coroutine — which stalls *every other* pending `update {}` call, from any source (other intents,
independently launched work, `collectToModel`), not just this one. That's a wider stall than #11:
it isn't limited to this ViewModel's intent stream.

**Fix:** compute the value *before* or *after* the `update {}` call (in `process`, or in whichever
coroutine needs it), and pass the already-computed result in:
```kotlin
update { copy(saving = true) }        // small, synchronous
val result = repository.save(...)      // suspends *outside* update {}
update { copy(saving = false) }        // small, synchronous
```
A computation that genuinely only depends on the current model (e.g.
`copy(list = list.sortedBy { it.priority })`) is fine inside `update {}` — the rule is about
suspending calls and non-trivial external computation, not about `update {}` doing nothing at all.

## 13. Business logic in the composable instead of `process`

**Smell:** a view computing derived business state, validating input, or calling a
repository/service directly, rather than posting an `Intent` and letting the ViewModel decide.

**Grep:** repository/service types referenced directly inside a `@Composable` function body.

**Why:** untestable without spinning up a UI; duplicates logic if more than one view needs it;
defeats the whole point of routing events through `process`.

**Fix:** move the decision into `process`; the view only posts the intent and renders the
resulting model.

## 14. A separate sealed `UiState`/`LoadingState` wrapper around the model

**Smell:** `sealed interface Model : VIE.Model { data object Loading : Model; data class
Content(...) : Model }` or similar, forcing every consumer to `when`/`is` before reading a field.

**Grep:** `sealed (interface|class) Model.*VIE\.Model`.

**Why:** fights VIE's "model is always complete" design (a `VIEViewModel` always has a
`firstModel`). Every reader has to unwrap the state before it can render anything, and it becomes
easy to lose fields across transitions (e.g. showing a spinner loses the previously-loaded list).

**Fix:** a single `data class Model(...)`, with loading/error captured as ordinary fields
(`val tasks: List<Task>? = null`, `val error: String? = null`).

## 15. Several `VIEView`s of one class under one owner without distinct `key`s

**Smell:** two or more `VIEView(newVM = { … })` calls composed under the same
`ViewModelStoreOwner` (one window, one screen, one navigation entry), building the same ViewModel
class with no distinct `key` — instances in a loop, for example. Or a `newVM` lambda typed
`VIEViewModel<…>` instead of the concrete class, which hides the class from `VIEView`.

**Grep:** every `VIEView(` with no `key` inside `items(...)`/`forEach`, and every `newVM` lambda
whose return type is written out as `VIEViewModel<`.

**Why:** a ViewModel is identified under its owner by its concrete class and its `key`. Two views
of one class and one `key` (or none) get one ViewModel, so instances collapse onto the first. A
lambda typed `VIEViewModel<…>` is filed under `VIEViewModel` whatever it builds, so two such views
of different classes with the same `key` collide, and the second fails with a `ClassCastException`.

**Fix:** pass a distinct `key` to each instance: derived from the item's identity for a list
(`key = id.toString()`). Let `newVM` return the concrete class.

## 16. Treating `MockVIE.pendingIntents()` as an accumulating log

**Smell:** a test that calls `pendingIntents()` more than once and expects earlier intents to
still be present in a later call's result.

**Grep:** multiple `pendingIntents()` calls in one test without an intervening `assertEquals`
that accounts for draining.

**Why:** `pendingIntents()` *drains* the channel — each call returns only what was posted since
the previous call.

**Fix:** assert after each batch of `post(...)` calls, or accumulate the returned lists yourself
if a full history is actually needed.

## 17. Emitting into `MockVIE.signalFlow` before the view has composed

**Smell:** `mockVie.signalFlow.emit(...)` called immediately after constructing the `MockVIE`,
before rendering the view under test (or before the test framework has pumped composition).

**Grep:** `signalFlow.emit(` on a `MockVIE` instance, check it comes after the view is rendered.

**Why:** `MockVIE.signalFlow` is a `MutableSharedFlow` with no replay — a signal emitted before a
collector (the view's `SignalEffect`) is active is simply lost.

**Fix:** render the view first, then emit; or await confirmation the view has started collecting
before emitting.

## 18. Contract types written qualified instead of imported bare, or a base marker imported bare

**Smell:** in a `*VIE.kt`, `*VM.kt` or view file, the contract's own `Model`/`Intent`/`Signal`
written qualified (`CounterVIE.Model`, `CounterVIE.Intent.Increment`) where importing them and
using the bare name would do — including inside the VIE file itself, which should read
`VIE<Model, Intent, Signal>`, not `VIE<CounterVIE.Model, CounterVIE.Intent, CounterVIE.Signal>`.
The opposite mistake also counts: an `import` line pulling in the base marker `VIE.Model`,
`VIE.Intent` or `VIE.Signal` unqualified, rather than always referencing it with the `VIE.` prefix.

**Grep:** `^import .*\.VIE\.(Model|Intent|Signal)$` for the bad import; `\w+VIE\.(Model|Intent|Signal)\b`
inside a file's body for needless qualification.

**Why:** purely a hygiene concern, not a correctness one — none of this fails to compile. Bare
names keep the ViewModel/view signatures readable (`VIEViewScope<Intent, Signal>`, not
`VIEViewScope<CounterVIE.Intent, CounterVIE.Signal>`). Importing a base marker bare would collide
with the contract's own `Model`/`Intent`/`Signal` imports in the same file and erase the visual
distinction between "this screen's type" and "the library's marker" (`VIE.Signal.Event`,
`VIE.Signal.Effect`).

**Fix:** import the contract's `Model`/`Intent`/`Signal` and use the bare names; keep the `VIE.`
prefix on every base marker.

**Not this:**
- A file that genuinely depends on more than one contract at once (e.g. a parent composable
  hosting two child VIEs) can only import one contract's types bare — the rest have to stay
  qualified. That's not this smell.
- `import org.kodein.arch.vie.VIE.NoIntent` / `.NoSignal`, and using `NoIntent`/`NoSignal` bare, is
  correct — not the base-marker mistake this entry describes. `VIE.NoIntent`/`VIE.NoSignal` are a
  deliberate exception to the "keep the `VIE.` prefix" rule (see `good-practices.md` § "The
  contract"): unlike `Model`/`Intent`/`Signal`, they never collide with a contract's own type name.

## 19. The TimeMachine probe is enabled in a release build

**Smell:** `TimeMachine.start(TimeMachineConfig(…))` whose `enabled` is left at its default
(`true`), or is tied to something that is also `true` in a release build.

**Grep:** `TimeMachine\.start` and `TimeMachineConfig\(` — read the `enabled` argument.

**Why:** the probe streams every Model, Intent and Signal in cleartext — in a real app that means
tokens and personal data — with no authentication, and lets whatever listens on the other end send
objects into the app's ViewModels. By default that is anything listening on the device's own
`localhost`. A release build has no reason to carry that.

**Fix:** tie `enabled` to a debug flag the build already has: `ApplicationInfo.FLAG_DEBUGGABLE` on
Android, `Platform.isDebugBinary` on iOS, and on desktop and the web, which have no build types, a
flag the app sets for developer runs. A disabled probe opens no socket and does no work.

## 20. The TimeMachine probe is not last in `middlewares`

**Smell:** `TimeMachine.middleware()` with another middleware after it in the same list.

**Grep:** `TimeMachine\.middleware` — check it is the last element of its `listOf(…)`.

**Why:** the list folds from the end, so the last middleware sits nearest the source and sees
intents and signals first. A probe listed earlier sees them only after the middlewares behind it
have filtered or transformed them, so the inspector shows a stream that is no longer what the view
posted or the ViewModel emitted: an intent a later middleware drops never appears in it. Models are
reported as `update {}` produced them, wherever the probe sits.

**Fix:** move `TimeMachine.middleware()` to the end of the list.

## 21. One middleware instance shared between several ViewModels

**Smell:** a middleware built once and handed to more than one ViewModel: a DI singleton
(`single`, `Binding.single`), a top-level `val`, a `companion object` property. It matters most for
a middleware that overrides `onAttached`, as `TimeMachine.middleware()` does.

**Grep:** `TimeMachine\.middleware` and every `onAttached` override; then follow where the
middleware is created, looking for a singleton scope.

**Why:** `onAttached(vm)` gives the middleware the ViewModel it is installed on, and the middleware
keeps it. A second ViewModel overwrites what the first stored, so the shared instance silently
drives the wrong ViewModel. The TimeMachine probe guards itself: it observes the first ViewModel
only, prints a notice for each other one, and still reports models under the first one's identity.

**Fix:** build the list where each ViewModel is built, so each ViewModel gets its own instances: a
factory or `Binding.provider`, Koin's `viewModel { }` definition, or the constructor call itself.

## 22. A ViewModel's `middlewares` constructor parameter has a default

**Smell:** a ViewModel that takes `middlewares` as a constructor parameter with a default value —
`middlewares: List<VIEMiddleware<Model, Intent, Signal>> = emptyList()` — in a project that builds
its ViewModels with a DI framework (Inara's `@DIFactory`, Koin's compiler plugin).

**Grep:** `middlewares\s*:\s*List<.*>\s*=` in a ViewModel's constructor.

**Why:** those tools prefer a Kotlin default to what the DI module provides (Koin's compiler plugin
does so under its `skipDefaultValues` option, according to its documentation). The list the module
builds is then never used, and TimeMachine is silently left out of that ViewModel: no error, and no
session for it in the inspector.

**Fix:** remove the default, so that the module supplies the list; see `good-practices.md` §
"Middlewares".

**Not this:** `VIEViewModel`'s own `middlewares = emptyList()` default, and a ViewModel built by
hand with no DI framework.

## Reporting

Group findings by severity (compile-breaking, like #5, first; then security, like #19;
correctness next; then hygiene).
For each: `file:line`, a one-line statement of what's wrong, and a short fix — a snippet, not a
lecture. If a scanned file has none of the above, say so rather than manufacturing a finding.
