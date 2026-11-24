import Shared
import SwiftUI

/// Where the task list can go: the editor of a task, or of a new one when `id` is nil.
struct EditRoute: Hashable {
    let id: KotlinUuid?
}

struct ContentView: View {
    @State private var path: [EditRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            TaskListScreen(
                goToNewTask: { path.append(EditRoute(id: nil)) },
                goToTask: { path.append(EditRoute(id: $0)) }
            )
            .navigationDestination(for: EditRoute.self) { route in
                EditTaskScreen(
                    taskId: route.id,
                    goBack: { if !path.isEmpty { path.removeLast() } }
                )
            }
        }
    }
}
