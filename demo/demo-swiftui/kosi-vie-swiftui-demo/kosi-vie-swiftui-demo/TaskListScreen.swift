import Shared
import SwiftUI

/// The task list: owns its ViewModel through a `VIEContainer`, and hands the model and the scope
/// to `TaskListContent`.
struct TaskListScreen: View {
    let goToNewTask: () -> Void
    let goToTask: (KotlinUuid) -> Void

    var body: some View {
        VIEContainer(newVM: { DemoViewModels.shared.taskList() }) { model, scope in
            TaskListContent(
                model: model,
                scope: scope,
                goToNewTask: goToNewTask,
                goToTask: goToTask
            )
        }
    }
}

/// What `TaskListScreen` renders. It knows nothing of the ViewModel, only a model and a scope, so
/// a preview renders it against `EmptyVIE`.
struct TaskListContent: View {
    let model: TaskListVIEModel
    let scope: any VIEViewScope
    let goToNewTask: () -> Void
    let goToTask: (KotlinUuid) -> Void

    @State private var errorMessage: String?

    var body: some View {
        content
            .navigationTitle("Task List")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button(action: goToNewTask) {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add a task")
                }
            }
            .onVIESignal(of: scope) { signal in
                switch signal {
                case let error as TaskListVIESignalError:
                    errorMessage = error.message
                default:
                    break
                }
            }
            .alert(
                "Error",
                isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorMessage ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        if let tasks = model.tasks {
            if tasks.isEmpty {
                Text("No tasks").foregroundStyle(.secondary)
            } else {
                List(rows(of: tasks)) { row in
                    HStack {
                        Button {
                            scope.post(intent: TaskListVIEIntentUpdateTaskCompletion(
                                taskId: row.id,
                                completed: !row.task.completed
                            ))
                        } label: {
                            Image(systemName: row.task.completed ? "checkmark.circle.fill" : "circle")
                        }
                        .buttonStyle(.borderless)
                        .disabled(row.updating)

                        Text(row.task.title)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                            .onTapGesture { goToTask(row.id) }

                        Button(role: .destructive) {
                            scope.post(intent: TaskListVIEIntentDeleteTask(taskId: row.id))
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                        .disabled(row.deleting)
                    }
                }
            }
        } else {
            ProgressView()
        }
    }

    /// A task, with whether the ViewModel is working on it.
    private struct Row: Identifiable {
        let id: KotlinUuid
        let task: Shared.Task
        let updating: Bool
        let deleting: Bool
    }

    private func rows(of tasks: [KotlinPair<KotlinUuid, Shared.Task>]) -> [Row] {
        tasks.compactMap { pair in
            guard let id = pair.first, let task = pair.second else { return nil }
            let work = model.work.filter { $0.first == id }.compactMap { $0.second }
            return Row(
                id: id,
                task: task,
                updating: work.contains { $0 == TaskListVIEModel.Work.updating },
                deleting: work.contains { $0 == TaskListVIEModel.Work.deleting }
            )
        }
    }
}

#Preview("Loading") {
    NavigationStack {
        TaskListContent(
            model: TaskListVIEModel(tasks: nil, work: []),
            scope: EmptyVIE<AnyObject, AnyObject>(),
            goToNewTask: {},
            goToTask: { _ in }
        )
    }
}

#Preview("Empty") {
    NavigationStack {
        TaskListContent(
            model: TaskListVIEModel(tasks: [], work: []),
            scope: EmptyVIE<AnyObject, AnyObject>(),
            goToNewTask: {},
            goToTask: { _ in }
        )
    }
}
