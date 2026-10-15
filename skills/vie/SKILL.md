---
name: vie
description: Guidance for the VIE Kotlin Multiplatform MVI library (org.kodein.arch.vie). Use when writing, explaining, or reviewing code that declares or uses VIE, VIEViewModel, VIEViewScope, VIEView, VIEMiddleware, SignalEffect, MockVIE, runVMTest, or EmptyVIE; or when the user asks to create a new VIE screen, scaffold a VIE ViewModel/View, or check VIE code for best practices.
---

# VIE

VIE is Model-View-Intent for Compose Multiplatform: one state flow down (`Model`), intents up
(`Intent`), one-off signals sideways (`Signal`). A screen declares the three types as one
interface extending `VIE<Model, Intent, Signal>`; a `VIEViewModel` implements it; a `VIEView`
renders it. The view only ever depends on the interface — via `VIEViewScope` — never on the
ViewModel class.

The library's types make bad shapes *awkward*, not impossible: nothing stops a `Model` from being
a plain mutable class, or a view from taking the ViewModel directly. That's what this skill is
for — apply it in three ways:

- **Explaining or writing new VIE code** → read `references/good-practices.md` and follow it.
- **Reviewing existing VIE code** → read `references/bad-practices.md` and apply the checklist below.
- **Creating a new screen** → follow the scaffolding steps below.

## API cheat-sheet

| Declaration | What it is |
|---|---|
| `VIE<Model, Intent, Signal>` | The contract. `Model`/`Intent`/`Signal` are marker interfaces bounding the three type params. |
| `VIE.Model` | Marker for the state type. Implement as a `data class`. |
| `VIE.Intent` | Marker for the UI-event type. Implement as a `sealed interface`. `VIE.Intent.None` (aliased `VIE.NoIntent`) — uninstantiable, for a screen with no input. |
| `VIE.Signal` | Marker for the one-off type. Implement as a `sealed interface`; each concrete signal also picks `VIE.Signal.Event` or `VIE.Signal.Effect`. `VIE.Signal.None` (aliased `VIE.NoSignal`) — for a screen with nothing to signal. |
| `VIE.Signal.Event` | Broadcast to **every** active collector of `signalFlow`; **dropped** if none are collecting (no replay buffer). |
| `VIE.Signal.Effect` | Delivered to **exactly one** collector. Use for one-shot navigation and anything that must not fire twice. |
| `VIE.NoIntent` | Alias for `VIE.Intent.None`. Import bare (`import ….VIE.NoIntent`) and use unqualified: `VIE<Model, NoIntent, Signal>`. |
| `VIE.NoSignal` | Alias for `VIE.Signal.None`. Import bare (`import ….VIE.NoSignal`) and use unqualified: `VIE<Model, Intent, NoSignal>`. |
| `VIEViewScope<Intent, Signal>` | What a view sees: `post(intent)` and `signalFlow`. No `modelFlow`, no `update`, no `process`. |
| `SignalEffect { signal -> … }` | `@Composable` — collects `signalFlow` for the composition's lifetime; put an exhaustive `when` inside. |
| `VIEViewModel<Model, Intent, Signal>(firstModel, middlewares)` | Abstract base, extends `androidx.lifecycle.ViewModel`. Implement `process(intent)`. Call `update { copy(...) }` to mutate, `signal(s)` to emit. |
| `update { suspend Model.() -> Model }` | Atomically replaces the model; serialized against every other `update` call; suspends until applied. |
| `signal(s)` | Emits `s`; `s`'s type must be `Signal` and `VIE.Signal.Event`/`Effect` (enforced by a `where` bound). |
| `collectToModel { … }` / `collectToSignal { … }` | `Flow<T>` extensions, called with a `VIEViewModel` in context (`init` block or `viewModelScope` coroutine) — feed an external flow straight into the model or a signal. |
| `VIEView(newVM = { … }, key = null) { model -> … }` | The entry point: creates/remembers the ViewModel, collects `modelFlow` as Compose state, invokes `content` with a `VIEViewScope` receiver. |
| `VIEMiddleware<Model, Intent, Signal>` | Intercepts the model/intent/signal flows as plain `Flow` transformations. In a list, the *last* middleware is closest to where values come from, the *first* to where they go: models and signals run ViewModel → last → … → first → view; intents run view → last → … → first → `process`. |
| `VIELoggingMiddleware(name)` | Ships in the box: prints every model/intent/signal plus `START`/`END`, prefixed with `name`. |
| `MockVIE<Model, Intent, Signal>(model)` | A full `VIE` for tests: mutable `modelFlow`/`signalFlow`, records posted intents in `intentsChannel`. |
| `MockVIE.pendingIntents()` | **Drains** `intentsChannel`: returns intents posted since the previous call, then empties. Not an accumulating log. |
| `runVMTest(newVM = { … }) { … }` | `org.kodein.arch.vie.test` (`vie-test`). Tests a `VIEViewModel` on virtual time: `post(intent)`, `model`, `pendingModels()`, `pendingSignals()` (both drain), `advanceUntilIdle()`, `runCurrent()`, `advanceTimeBy(d)`. Return its result from the test function. |
| `EmptyVIE<Intent, Signal>()` | A `VIEViewScope` with nothing behind it, for `@Preview`s: `post` discards, `signalFlow` is always empty. Carries no model — pass one to the view's own `model` parameter. |

## Reviewing VIE code

1. Read `references/bad-practices.md`.
2. Read every file that declares or touches a VIE contract, ViewModel, or view in scope.
3. For each anti-pattern in the checklist, decide if it actually applies — don't flag something
   the checklist doesn't cover, and don't invent stylistic complaints VIE has no opinion on.
4. Report findings grouped by severity, each with `file:line`, what's wrong, why it matters, and
   the concrete fix (a short snippet, not a lecture). If nothing is wrong, say so plainly.

## Creating a new VIE screen

Given a screen name (e.g. "Counter") and optionally a package:

1. **Determine the name.** `{{Name}}` = the screen name in PascalCase (produces `{{Name}}VIE`,
   `{{Name}}VM`, `{{Name}}`).
2. **Find the Kotlin source root.** Prefer a `src/commonMain/kotlin` tree (Kotlin Multiplatform);
   fall back to `src/main/kotlin` or `src/main/java` for a JVM-only Android/Compose Desktop project.
3. **Match the existing layout, and determine three package placeholders.** Look for other
   `*VIE.kt` files in the project:
   - If VIE contracts, ViewModels and views live in *separate* packages (e.g. `<feature>/vie/`,
     `<feature>/vie/vm/`, `<feature>/view/` — the layout this library's own demo uses), set
     `{{viePackage}}`, `{{vmPackage}}`, `{{viewPackage}}` to the three packages that match that
     split for the new screen.
   - If a project keeps a screen's contract, ViewModel and view together in one package (e.g.
     `<feature>/`), set all three placeholders to that same package.
   - If this is the first VIE screen in the project, default to the split layout under the given
     (or inferred) base package: `{{base}}.vie` / `{{base}}.vie.vm` / `{{base}}.view`.
   If the base package can't be inferred, ask.
4. **Read the three templates** in this skill's `templates/` directory (`XxxVIE.kt.template`,
   `XxxVM.kt.template`, `Xxx.kt.template`) and substitute `{{Name}}`, `{{viePackage}}`,
   `{{vmPackage}}`, `{{viewPackage}}` throughout. Each template only uses the placeholders
   relevant to its own file (see the template headers), but every placeholder must resolve so the
   cross-file imports line up.
5. **Write the three files** to paths matching the packages chosen in step 3. Never overwrite an
   existing file — ask first if one is in the way.
6. **Report** the paths created and what's left as `TODO` for the user to fill in (the model's
   fields, the intents, the signals, `process`, and the layout).
