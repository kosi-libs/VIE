import Shared
import SwiftUI

/// The editor of a task, or of a new one when `taskId` is nil: owns its ViewModel through a
/// `VIEContainer`, and hands the model and the scope to `EditTaskContent`.
struct EditTaskScreen: View {
    let taskId: KotlinUuid?
    let goBack: () -> Void

    var body: some View {
        VIEContainer(newVM: { DemoViewModels.shared.editTask(taskId: taskId) }) { model, scope in
            EditTaskContent(model: model, scope: scope, goBack: goBack, new: taskId == nil)
        }
    }
}

/// What `EditTaskScreen` renders. It knows nothing of the ViewModel, only a model and a scope, so
/// a preview renders it against `EmptyVIE`.
struct EditTaskContent: View {
    let model: EditTaskVIEModel
    let scope: any VIEViewScope
    let goBack: () -> Void
    let new: Bool

    @State private var title = ""
    @State private var description = ""
    @State private var errorMessage: String?

    var body: some View {
        Group {
            if model.loading {
                ProgressView()
            } else {
                Form {
                    TextField("Title", text: $title)
                    TextField("Description", text: $description, axis: .vertical)
                }
            }
        }
        .navigationTitle(new ? "New Task" : "Edit Task")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    scope.post(intent: EditTaskVIEIntentEdit(task: Shared.Task(
                        title: title,
                        description: description,
                        completed: model.task?.completed ?? false
                    )))
                }
                .disabled(model.loading || title.isEmpty)
            }
        }
        // The loaded task fills the fields, once it arrives.
        .onChange(of: model.task, initial: true) { _, task in
            guard let task else { return }
            title = task.title
            description = task.description_
        }
        .onVIESignal(of: scope) { signal in
            switch signal {
            case is EditTaskVIESignalEdited, is EditTaskVIESignalLoadFail:
                goBack()
            case let error as EditTaskVIESignalError:
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
}

#Preview("New") {
    NavigationStack {
        EditTaskContent(
            model: EditTaskVIEModel(loading: false, task: nil),
            scope: EmptyVIE<AnyObject, AnyObject>(),
            goBack: {},
            new: true
        )
    }
}

#Preview("Loading") {
    NavigationStack {
        EditTaskContent(
            model: EditTaskVIEModel(loading: true, task: nil),
            scope: EmptyVIE<AnyObject, AnyObject>(),
            goBack: {},
            new: false
        )
    }
}
