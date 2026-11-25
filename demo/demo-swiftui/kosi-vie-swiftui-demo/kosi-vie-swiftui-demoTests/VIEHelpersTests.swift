import Shared
import SwiftUI
import XCTest
@testable import kosi_vie_swiftui_demo

// The run-time behaviour of the helpers of skills/vie/swift, which the demo app compiles as they
// are. Each test mounts a real view in a window of the app that hosts the tests, and drives it with
// HelperTestVM, a Kotlin ViewModel of the `Shared` framework that only exists for these tests.
//
// The helpers keep their types private: the tests only go through what an app uses, `VIEContainer`,
// `VIEScope` and `onVIESignal`.

private typealias TestModel = HelperTestVIEModel
private typealias TestIntent = HelperTestVIEIntent
private typealias TestSignal = HelperTestVIESignal
private typealias TestScope = VIEScope<TestIntent, TestSignal>
private typealias TestHost = VIEHost<TestModel, TestIntent, TestSignal>

/// What a test changes to make the mounted view render again, lose a child, or take another scope.
@MainActor
@Observable
private final class Stage {
    var generation = 0
    var isShown = true
    var usesSecondScope = false
}

/// What the mounted view tells the test. Not observable: writing to it while a body is computed
/// invalidates nothing.
@MainActor
private final class Log<Element> {
    private(set) var entries: [Element] = []
    func append(_ element: Element) { entries.append(element) }
}

@MainActor
private final class Box<Value> {
    var value: Value?
}

/// Holds a `perform` until the test lets it go, whether its task is cancelled or not: a `perform`
/// that never checks for cancellation.
@MainActor
private final class Gate {
    private var isOpen = false
    private var waiting: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        if isOpen { return }
        await withCheckedContinuation { waiting.append($0) }
    }

    func open() {
        isOpen = true
        waiting.forEach { $0.resume() }
        waiting = []
    }
}

/// Raised from a cancellation handler, which runs on whichever thread cancels the task.
private final class Flag: @unchecked Sendable {
    private let lock = NSLock()
    private var raised = false

    var isRaised: Bool { lock.withLock { raised } }
    func raise() { lock.withLock { raised = true } }
}

/// The root of what a test mounts. `content` reads the `Stage` of the test, so this body is what
/// SwiftUI computes again when the test changes it.
private struct Root<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View { content() }
}

private func shownId(_ signal: any VIESignal) -> Int32 {
    (signal as! HelperTestVIESignalShown).id
}

@MainActor
final class VIEHelpersTests: XCTestCase {

    private var window: UIWindow?

    override func tearDown() async throws {
        window?.isHidden = true
        window?.rootViewController = nil
        window = nil
    }

    // MARK: VIEContainer

    func testAModelUpdateRendersTheViewAgain() async throws {
        let rendered = Log<Int32>()
        let scope = Box<TestScope>()
        try await mount {
            VIEContainer(newVM: { HelperTestVM() }) { model, containerScope in
                let _ = rendered.append(model.count)
                let _ = (scope.value = containerScope)
                Color.clear
            }
        }
        await eventually("the first rendering") { rendered.entries.last == 0 }

        scope.value?.post(HelperTestVIEIntentIncrement.shared)

        await eventually("the rendering of the updated model") { rendered.entries.last == 1 }
        XCTAssertEqual(withoutRepeats(rendered.entries), [0, 1])
    }

    func testTheViewModelIsCreatedOnceHoweverOftenTheParentRenders() async throws {
        let stage = Stage()
        let created = Log<HelperTestVM>()
        let rendered = Log<Int>()
        try await mount {
            // Read here, in the parent: each change makes a new VIEContainer, with a new `@State`
            // initial value that SwiftUI discards.
            let generation = stage.generation
            VIEContainer(
                newVM: {
                    let vm = HelperTestVM()
                    created.append(vm)
                    return vm
                }
            ) { model, _ in
                let _ = rendered.append(generation)
                Text("\(generation) \(model.count)")
            }
        }
        await eventually("the first rendering") { rendered.entries.last == 0 }

        for generation in 1...3 {
            stage.generation = generation
            await eventually("the rendering of generation \(generation)") { rendered.entries.last == generation }
        }

        XCTAssertEqual(created.entries.count, 1)
    }

    func testTheViewModelIsClearedWhenTheContainerGoesAway() async throws {
        let stage = Stage()
        let vm = HelperTestVM()
        let rendered = Log<Int32>()
        try await mount {
            if stage.isShown {
                VIEContainer(newVM: { vm }) { model, _ in
                    let _ = rendered.append(model.count)
                    Color.clear
                }
            }
        }
        await eventually("the first rendering") { !rendered.entries.isEmpty }
        XCTAssertEqual(vm.clearedCount, 0)

        stage.isShown = false

        await eventually("the ViewModel to be cleared") { vm.clearedCount > 0 }
        XCTAssertEqual(vm.clearedCount, 1)
    }

    // MARK: onVIESignal

    func testASignalIsHandledByThePerformOfTheLatestRendering() async throws {
        let stage = Stage()
        let vm = HelperTestVM()
        let rendered = Log<Int>()
        let handledBy = Log<Int>()
        try await mount {
            let generation = stage.generation
            VIEContainer(newVM: { vm }) { _, scope in
                let _ = rendered.append(generation)
                // Each rendering gives a new closure, which captures its own generation.
                Color.clear.onVIESignal(of: scope) { _ in handledBy.append(generation) }
            }
        }
        // The collection is started, with the closure of the first rendering.
        vm.post(intent: HelperTestVIEIntentShow(id: 1))
        await eventually("the first signal") { handledBy.entries == [0] }

        stage.generation = 1
        await eventually("the second rendering") { rendered.entries.last == 1 }
        vm.post(intent: HelperTestVIEIntentShow(id: 2))

        await eventually("the second signal") { handledBy.entries.count == 2 }
        XCTAssertEqual(handledBy.entries, [0, 1])
    }

    func testTheSignalAfterTheOneBeingHandledWhenTheViewDisappearsIsNotHandedToPerform() async throws {
        let stage = Stage()
        let host = TestHost(newVM: { HelperTestVM() })
        defer { host.clear() }
        let scope = TestScope(host.scope)
        let handled = Log<Int32>()
        let gate = Gate()
        let cancellation = Flag()
        let returned = Flag()
        try await mount {
            // The host outlives the view: only the collection of the view stops.
            if stage.isShown {
                Color.clear.onVIESignal(of: scope) { signal in
                    handled.append(shownId(signal))
                    await withTaskCancellationHandler {
                        await gate.wait()
                    } onCancel: {
                        cancellation.raise()
                    }
                    returned.raise()
                }
            }
        }
        scope.post(HelperTestVIEIntentShow(id: 1))
        scope.post(HelperTestVIEIntentShow(id: 2))
        await eventually("the first signal to be handled") { handled.entries == [1] }

        // The view disappears while `perform` handles the first signal, which then returns.
        stage.isShown = false
        await eventually("the task of the view to be cancelled") { cancellation.isRaised }
        gate.open()
        await eventually("perform to return") { returned.isRaised }

        // A signal posted afterwards reaches another collection: by then, the one of the view
        // would have been handed the second signal, had it asked Kotlin for it.
        let later = Log<Int32>()
        let collection = VIESignalsKt.collectSignals(host.scope) { signal, done in
            later.append(shownId(signal as! any VIESignal))
            _ = done()
        }
        defer { collection.cancel() }
        scope.post(HelperTestVIEIntentShow(id: 3))
        await eventually("the later signal to reach the other collection") { later.entries.contains(3) }

        XCTAssertEqual(handled.entries, [1])
    }

    func testAnEffectEmittedAfterTheViewDisappearedIsLeftToAnotherCollection() async throws {
        let stage = Stage()
        let host = TestHost(newVM: { HelperTestVM() })
        defer { host.clear() }
        let scope = TestScope(host.scope)
        let handled = Log<Int32>()
        let gate = Gate()
        defer { gate.open() }
        let cancellation = Flag()
        try await mount {
            if stage.isShown {
                Color.clear.onVIESignal(of: scope) { signal in
                    handled.append(shownId(signal))
                    await withTaskCancellationHandler {
                        await gate.wait()
                    } onCancel: {
                        cancellation.raise()
                    }
                }
            }
        }
        scope.post(HelperTestVIEIntentShow(id: 1))
        await eventually("the first signal to be handled") { handled.entries == [1] }

        // The view disappears, and its `perform` ignores the cancellation: it still runs.
        stage.isShown = false
        await eventually("the task of the view to be cancelled") { cancellation.isRaised }

        // The collection of the view is over all the same: an Effect emitted now goes to the
        // collection that listens, as it would wait for the view to come back.
        let later = Log<Int32>()
        let collection = VIESignalsKt.collectSignals(host.scope) { signal, done in
            later.append(shownId(signal as! any VIESignal))
            _ = done()
        }
        defer { collection.cancel() }
        scope.post(HelperTestVIEIntentShow(id: 2))

        await eventually("the later signal to reach the other collection") { later.entries == [2] }
        XCTAssertEqual(handled.entries, [1])
    }

    func testAnotherScopeRestartsTheCollection() async throws {
        let stage = Stage()
        let first = TestHost(newVM: { HelperTestVM() })
        let second = TestHost(newVM: { HelperTestVM() })
        defer {
            first.clear()
            second.clear()
        }
        let firstScope = TestScope(first.scope)
        let secondScope = TestScope(second.scope)
        let rendered = Log<Bool>()
        let handled = Log<Int32>()
        try await mount {
            // The same view, kept by SwiftUI, with one scope and then the other.
            let usesSecondScope = stage.usesSecondScope
            let _ = rendered.append(usesSecondScope)
            Color.clear.onVIESignal(of: usesSecondScope ? secondScope : firstScope) { signal in
                handled.append(shownId(signal))
            }
        }
        firstScope.post(HelperTestVIEIntentShow(id: 1))
        await eventually("the signal of the first scope") { handled.entries == [1] }

        stage.usesSecondScope = true
        await eventually("the rendering with the second scope") { rendered.entries.last == true }
        secondScope.post(HelperTestVIEIntentShow(id: 2))
        await eventually("the signal of the second scope") { handled.entries == [1, 2] }

        // The first scope is no longer collected: its signal waits, and the next one of the second
        // scope arrives without it.
        firstScope.post(HelperTestVIEIntentShow(id: 3))
        secondScope.post(HelperTestVIEIntentShow(id: 4))
        await eventually("the next signal of the second scope") { handled.entries.contains(4) }
        XCTAssertEqual(handled.entries, [1, 2, 4])
    }

    // MARK: Support

    /// Mounts `content` in a visible window of the host app: `task` and `onAppear` only run for a
    /// view that is on screen.
    private func mount<Content: View>(@ViewBuilder _ content: @escaping () -> Content) async throws {
        let scene = try await windowScene()
        let window = UIWindow(windowScene: scene)
        window.rootViewController = UIHostingController(rootView: Root(content: content))
        window.makeKeyAndVisible()
        self.window = window
    }

    private func windowScene() async throws -> UIWindowScene {
        var scene: UIWindowScene?
        await eventually("a window scene of the host app") {
            scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
            return scene != nil
        }
        return try XCTUnwrap(scene)
    }

    /// Waits for `condition`, which the main actor makes true between two checks, and fails the
    /// test if it still does not hold after `timeout`. The timeout is long on purpose: CI runs on a
    /// machine that other jobs load, and a test that passes never waits for it.
    private func eventually(
        _ what: String,
        timeout: TimeInterval = 60,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ condition: () -> Bool
    ) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() {
            guard Date() < deadline else {
                XCTFail("Timed out waiting for \(what)", file: file, line: line)
                return
            }
            // Qualified: the `Shared` framework exports a class named Task.
            try? await _Concurrency.Task.sleep(nanoseconds: 10_000_000)
        }
    }

    private func withoutRepeats<Element: Equatable>(_ elements: [Element]) -> [Element] {
        elements.reduce(into: []) { result, element in
            if result.last != element { result.append(element) }
        }
    }
}
