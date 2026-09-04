# VIE good practices

Each item states the convention and *why* — VIE's types allow you to break every one of these,
so the reason is what makes it worth following.

## The contract

Declare one interface — extending `VIE<Model, Intent, Signal>` — per self-contained unit of state
you want to drive with a single ViewModel. That's most often a whole screen, but it doesn't have
to be: a complex screen with independent sections (say, a filter panel and a results list) can
just as reasonably be split into one `VIE` + ViewModel per section, each mounted with its own
`VIEView`. The right granularity is a call the developer makes per screen, not a rule VIE imposes.

In the VIE, ViewModel and view files alike, import the contract's `Model`, `Intent` and `Signal`
and refer to them by their bare names — even the VIE file itself imports its own nested types,
which is what makes `VIE<Model, Intent, Signal>` read cleanly instead of
`VIE<CounterVIE.Model, CounterVIE.Intent, CounterVIE.Signal>`:

```kotlin
import org.kodein.arch.vie.VIE
import com.example.counter.CounterVIE.Intent
import com.example.counter.CounterVIE.Model
import com.example.counter.CounterVIE.Signal

interface CounterVIE : VIE<Model, Intent, Signal> {
    data class Model(val count: Int, val saving: Boolean = false) : VIE.Model
    sealed interface Intent : VIE.Intent {
        data object Increment : Intent
        data object Save : Intent
    }
    sealed interface Signal : VIE.Signal {
        data object Saved : Signal, VIE.Signal.Event
        data class Error(val message: String) : Signal, VIE.Signal.Effect
    }
}
```

Never import `VIE.Model`, `VIE.Intent` or `VIE.Signal` themselves — always keep the `VIE.` prefix
on those base markers (`: VIE.Model`, `VIE.Signal.Event`, `VIE.Signal.Effect`, the generic bound on
a `VIEMiddleware`). Importing them bare would collide with the contract's own
`Model`/`Intent`/`Signal` imports above, and erase the visual distinction between "this screen's
type" and "the library's marker". The one exception is a file that genuinely depends on more than
one contract at once (say, a parent composable hosting two child VIEs) — only one contract's types
can be imported bare there, so the rest stay qualified.

`VIE.NoIntent` and `VIE.NoSignal` — aliases for `VIE.Intent.None` and `VIE.Signal.None` — are a
deliberate exception to that prefix rule: import and use them bare (`NoIntent`, `NoSignal`). Unlike
`Model`/`Intent`/`Signal`/`Event`/`Effect`, these names are never also the contract's own type, so a
bare import can't collide with anything or blur which type belongs to which screen.

A view depends on `CounterVIE.Model`/`Intent`/`Signal` (through `VIEViewScope`), never on
`CounterVM`. Swapping in a fake for a preview or a test is a different constructor call, nothing
more.

## Model

Always a `data class`, never a plain `class` or one with `var` properties or mutable
collections. The view relies on structural equality to skip recomposition when an update produces
an equal model; break that and every emission recomposes even when nothing visible changed.

A model is always *complete* — there is no "nothing yet" state, since a `VIEViewModel` is always
constructed with a `firstModel`. Model loading as an ordinary field:

```kotlin
data class Model(
    val tasks: List<Task>? = null,   // null = still loading
    val saving: Boolean = false,
) : VIE.Model
```

Do **not** wrap the model in a separate sealed `Loading`/`Content`/`Error` hierarchy — that fights
the "always complete" design and forces every consumer to unwrap it before reading a field.

## Intent

A `sealed interface`, so `process` can `when` over it and the compiler catches a missed branch
when a new intent is added later. An intent is most often something the user did, but not
always — a lifecycle callback, a timer, an incoming deep link all qualify.

A screen that takes no input at all uses the built-in `VIE.Intent.None`, aliased `NoIntent` — it
has a private constructor, so `post` becomes uncallable at that type. Don't invent an empty sealed
interface for this; the library already provides it. Import and use the bare alias:
`VIE<Model, NoIntent, Signal>`.

## Signal

A `sealed interface`; every concrete signal also picks a delivery kind:

- **`VIE.Signal.Event`** — broadcast to *every* active collector of `signalFlow`; **dropped** if
  none are currently collecting (there is no replay buffer). Use for things every listener cares
  about: "logged out", "data changed elsewhere".
- **`VIE.Signal.Effect`** — delivered to *exactly one* collector. If several views observe the
  same ViewModel, whichever collects first gets it. Use for one-shot navigation and anything that
  must not happen twice — a "navigate back" broadcast to two observers would fire twice.

A screen with nothing to signal uses the built-in `VIE.Signal.None`, aliased `NoSignal` — import
and use the bare alias: `VIE<Model, Intent, NoSignal>`.

## The ViewModel

```kotlin
import com.example.counter.CounterVIE.Intent
import com.example.counter.CounterVIE.Model
import com.example.counter.CounterVIE.Signal

class CounterVM(
    private val store: CounterStore,
) : CounterVIE, VIEViewModel<Model, Intent, Signal>(
    firstModel = Model(count = 0),
    middlewares = listOfNotNull(VIELoggingMiddleware("Counter").takeIf { isDebug }),
) {
    override suspend fun process(intent: Intent) {
        when (intent) {
            is Intent.Increment -> update { copy(count = count + 1) }
            is Intent.Save -> {
                update { copy(saving = true) }
                store.save(model.count)
                    .onSuccess { signal(Signal.Saved) }
                    .onFailure { signal(Signal.Error(it.message ?: "Save failed")) }
                update { copy(saving = false) }
            }
        }
    }
}
```

- Mutate the model **only** through `update { }` — never build a model by hand and assign it
  elsewhere. `update` serializes against every other call, so two intents racing on
  `copy(count = count + 1)` cannot lose an increment.
- Emit signals **only** through `signal(…)`.
- `process` is called one intent at a time, in order, on `viewModelScope`. Awaiting a suspend call
  directly inside `process` is correct when later intents should genuinely wait behind it (a save
  that must complete before the next action is processed, as in `Save` above). Launch into
  `viewModelScope` explicitly when the work should *not* block the intent stream — for example,
  a list of independent per-row actions that should run concurrently.
- **`process`'s `when` must be exhaustive, with no `else`.** Enumerate every concrete `Intent`
  subtype explicitly:
  ```kotlin
  override suspend fun process(intent: Intent) {
      when (intent) {
          is Intent.Increment -> update { copy(count = count + 1) }
          is Intent.Save -> { /* … */ }
      }
  }
  ```
  This isn't a style preference — since Kotlin 1.7, a non-exhaustive `when` over a `sealed` type
  is a compiler **error**, whether the `when` is a statement (as here) or an expression. Add a new
  `Intent` later without a matching branch, and the ViewModel fails to compile until you handle
  it — an `else` branch would silently swallow it instead.
- **`process`'s body should be *only* that `when` — nothing before or after it.** A statement
  outside the `when` runs unconditionally for every intent, which makes it a cross-cutting
  concern, not per-case handling — and VIE already has a dedicated home for that:
  `VIEMiddleware.interceptIntents`, tapping the flow with `onEach { }` and passing it through
  unchanged (the same shape `VIELoggingMiddleware` already uses — see "Middlewares" below).
  ```kotlin
  // Don't: analytics runs inline, mixed in with dispatch.
  override suspend fun process(intent: Intent) {
      analytics.track(intent)
      when (intent) { /* … */ }
  }

  // Do: analytics is a tap on the intent flow, process stays only the when.
  class AnalyticsMiddleware<M : VIE.Model, I : VIE.Intent, S : VIE.Signal>(
      private val analytics: Analytics,
  ) : VIEMiddleware<M, I, S> {
      override fun interceptIntents(flow: Flow<I>) = flow.onEach { analytics.track(it) }
  }
  ```
  If something genuinely can't be expressed as a middleware — it needs this ViewModel's own
  private state, say — it's fine to leave it inline, but say so with a comment explaining why.
- **Keep `update { }` bodies minimal — a pure, synchronous function of the current model.**
  `update {}` calls are serialized through one dedicated consumer coroutine inside
  `VIEViewModel`; a suspending call or expensive computation placed *inside* the `update {}`
  lambda blocks that single coroutine, which stalls *every other* pending `update {}` call —
  from other intents, from independently `launch`ed work, from `collectToModel` — regardless of
  which coroutine issued it. This is a wider stall than just blocking `process`. Compute anything
  that needs a suspending call *before* or *after* `update {}`, and pass the already-computed
  value in:
  ```kotlin
  is Intent.Save -> {
      update { copy(saving = true) }          // small, synchronous
      val result = store.save(model.count)     // suspends *outside* update {}
      update { copy(saving = false) }          // small, synchronous
  }
  ```
  The `Save` branch in the example above already follows this: the suspending `store.save(…)`
  call sits between two small `update {}` calls, never inside one.
- `listOfNotNull(…)` for `middlewares` lets a middleware be conditional (e.g. logging only in
  debug builds) without an `if`/`else` list literal.
- **`update`/`signal` are public** on `VIEViewModel`, on purpose, so `collectToModel`/
  `collectToSignal` and code in the same ViewModel subclass can call them — not so a view or
  caller outside the ViewModel should ever reach them. A view should never hold a `VIEViewModel`
  reference at all; see "The view" below.

### Feeding an external Flow

Prefer the two context-parameter helpers over hand-rolling `viewModelScope.launch { flow.collect { update { … } } }`:

```kotlin
init {
    viewModelScope.launch {
        taskRepository.flow().collectToModel { tasks -> copy(tasks = tasks) }
    }
}
```

`collectToModel` and `collectToSignal` exist specifically to remove that boilerplate — reach for
them before writing the manual form.

## The view

A thin `@Composable` that only calls `VIEView`, plus a second `@Composable` that takes the model
and plain callbacks — no `VIEViewModel` in sight. There are two equally valid shapes for that
second composable; pick whichever fits.

**As a `VIEViewScope` extension** — the default, and the only option once the screen actually has
signals to react to:

```kotlin
import com.example.counter.CounterVIE.Intent
import com.example.counter.CounterVIE.Model
import com.example.counter.CounterVIE.Signal

@Composable
fun Counter(store: CounterStore) {
    VIEView(newVM = { CounterVM(store) }) { model ->
        Counter(model = model)   // extension below
    }
}

@Composable
fun VIEViewScope<Intent, Signal>.Counter(model: Model) {
    SignalEffect { signal ->
        when (signal) {
            is Signal.Saved -> /* … */ Unit
            is Signal.Error -> /* show snackbar */ Unit
        }
    }
    Column {
        Text("Count: ${model.count}")
        Button(onClick = { post(Intent.Increment) }) { Text("+") }
    }
}
```

**As a plain function taking `model` and a `post` callback** — fully independent of VIE, worth it
when the screen's `Signal` is `NoSignal` (nothing to react to) and the composable should have zero
VIE dependency at all, e.g. for reuse outside a VIE screen or a non-VIE preview harness.
Taking `CounterVIE` itself as an example wouldn't fit here — its `Signal` isn't `NoSignal` — so
picture a simpler `SwitchVIE : VIE<Model, Intent, NoSignal>` instead, with `NoSignal` imported bare
straight from `VIE` in the `SwitchVIE.kt` contract file. This `Switch` view file itself never
mentions `Signal` at all — a plain-callback view has nothing to import there:

```kotlin
import com.example.switch.SwitchVIE.Intent
import com.example.switch.SwitchVIE.Model

@Composable
fun Switch(model: Model, post: (Intent) -> Unit) {
    Checkbox(
        checked = model.on,
        onCheckedChange = { post(Intent.Toggle) },
    )
}
```

The caller wires it explicitly, since `VIEViewScope`'s implicit `post` isn't available:
`VIEView(newVM = { … }) { model -> Switch(model = model, post = ::post) }`. A
`VIEViewScope<Intent, NoSignal>` extension is the equivalent middle ground — still an
extension, but on a scope that can never emit anything to react to.

Both shapes keep the layout testable and previewable; the `VIEViewScope` one is also why
`VIEViewScope` exists at all — inside it, `this` has exactly `post(intent)` and `signalFlow`, no
way to reach the ViewModel even by accident. **This is not the same thing as taking `model` and
`post` as plain parameters** — that shape isn't "missing" `VIEViewScope`, it's a deliberate,
equally valid alternative for a screen with nothing to signal.

Navigation out of the screen arrives either as a plain callback parameter (`goBack: () -> Unit`)
supplied by the caller, or as a `Signal.Effect` handled inside `SignalEffect { }` — not by the view
reaching into the ViewModel or a navigation singleton directly.

If several instances of the same ViewModel need to coexist in one composition (e.g. a list of
detail screens), pass a distinct `key` to each `VIEView` call — without it, instances with the
same call site collapse onto a single ViewModel.

### Signals in the view

If a screen's `Signal` has any concrete member (i.e. it isn't `NoSignal`), its view should
call `SignalEffect { }` somewhere — an `Event` emitted with nobody collecting is silently dropped,
and an `Effect` emitted with nobody collecting leaves the emitting coroutine suspended forever
waiting for a collector that will never arrive. If a screen truly has nothing to react to, prefer
declaring `Signal = NoSignal` in the contract in the first place — that's exactly when the
plain `model`/`post` view shape above becomes attractive.

`SignalEffect`'s `when`, like `process`'s, must be exhaustive with no `else`:

```kotlin
SignalEffect { signal ->
    when (signal) {
        is Signal.Saved -> /* … */ Unit
        is Signal.Error -> /* show snackbar */ Unit
    }
}
```

`SignalEffect`'s callback is itself a lambda, so a `when` as its last expression is already
exhaustiveness-checked the same way `process`'s is — no special handling needed to get the same
guarantee.

**`SignalEffect`'s lambda body should be only that `when`, too** — nothing before or after it, for
the same reason as `process`: a statement outside the `when` runs for every signal regardless of
type, which makes it a cross-cutting concern. That belongs in a `VIEMiddleware.interceptSignals`
tap on the ViewModel (see `good-practices.md` § "The ViewModel" above), not duplicated into every
view that happens to collect the signal. If it genuinely has to stay in the view, document why.

## Previews and tests

`EmptyVIE<Intent, Signal>()` for previews — it has no model, so pass one to the view's own
`model` parameter:

```kotlin
@Preview
@Composable
fun CounterPreview() {
    EmptyVIE<Intent, Signal>().Counter(model = Model(count = 3))
}
```

`MockVIE<Model, Intent, Signal>(model)` for tests — assert on `pendingIntents()` after driving the
view, remembering it *drains* (each call returns only what was posted since the previous call).
Emit into `MockVIE.signalFlow` only after the view has composed and started collecting — it has no
replay, so an earlier emission is lost.

Testing `process` itself — how a `VIEViewModel` subclass reacts to an intent — is a separate,
ordinary unit test against that subclass; it has nothing to do with `MockVIE`.

## Middlewares

`VIEMiddleware` intercepts the three flows as plain `Flow` transformations; every method defaults
to identity, so override only what you need. When several are supplied, the list folds from the
end: the *last* middleware wraps the raw source flow and sees it first; the *first* sits closest
to the ViewModel and view and sees whatever every other middleware already passed through.

```kotlin
middlewares = listOf(
    VIELoggingMiddleware("Counter"),  // first: closest to the VM/view, logs the final values
    MetricsMiddleware(),              // last: wraps the raw source, sees it before logging does
)
```

Order the list by what each middleware needs to see, not by habit — a middleware that must
observe the truly raw values (before any other middleware can filter or transform them) goes
last; one that should reflect what actually reaches the ViewModel/view goes first.
