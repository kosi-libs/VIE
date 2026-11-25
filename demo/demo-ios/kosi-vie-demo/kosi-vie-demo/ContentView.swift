import Shared
import SwiftUI

/// A mixed app: the task list is a SwiftUI view (`TaskListScreen`, the one of the SwiftUI demo) and
/// the task editor is the Compose `EditTask` screen, presented in a sheet. Both run on the same
/// VIE ViewModels and the same `AppModule`, so an edit made in Compose shows in the SwiftUI list.
struct ContentView: View {
    @State private var editing: EditTarget?

    var body: some View {
        NavigationStack {
            TaskListScreen(
                goToNewTask: { editing = EditTarget(taskId: nil) },
                goToTask: { editing = EditTarget(taskId: $0) }
            )
        }
        .sheet(item: $editing) { target in
            EditTaskSheet(taskId: target.taskId) { editing = nil }
                .ignoresSafeArea()
        }
    }
}

/// The task the editor sheet edits: an existing one, or a new one when `taskId` is nil.
private struct EditTarget: Identifiable {
    let taskId: KotlinUuid?
    var id: String { taskId?.description ?? "new" }
}

/// The Compose task editor, presented from SwiftUI.
private struct EditTaskSheet: UIViewControllerRepresentable {
    let taskId: KotlinUuid?
    let goBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        DemoAppKt.EditTaskViewController(taskId: taskId, goBack: goBack)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
