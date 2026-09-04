# TimeMachine: an MVI-VM Inspector
A compose multiplatform desktop app that allows to inspect the behaviour of a multiplatform in development.

## Context
I am developping an MVI library that allows to develop a Compose multiplatform application with the Model View Intent UI pattern. The library is called MVI-VM (because it uses Compose ViewModels for MVI stores).

### Data
An MVI ViewModel listens for Intents, and can emit:
- A **Model**, that represents what the View should display, and that replaces the previous Model. There always is a current Model (the ViewModel starts with a model), and a View that listens for a Model will always immediately receive one upon subscription. A Model should always be a `data class`.
- A **Signal**, that informs the View of an event. That signal may be an **Event** that is broadcasted to all Views listening to that ViewModel Signals, or an **Effect** that will be sent to only one View (at random if there are multiple listeners). A Signal should always be a `sealed interface`.

An MVI View listens for Models, and Signals, and may emit :
- An **Intent**, that informs the ViewModel of UI event, which the ViewModel may want to react to. An Intent should always be a `sealed interface`.

## Application
The goal of that application is to open a WebSocket server and receive, display, and control Models, Intents & Signals sent between Views and ViewModels.

It uses Compose Multiplatform with Material3.

## Protocol
The client will connect to to the application server, and identify its session with a name and unique identifier. There may be multiple connection with different identifiers but the same name (the same application running different instances). Note that there may be only one connection per unique identifier, but that a client may disconnect and reconnect with the same identifier (because of network issues).

### ViewModel information
When a MVI ViewModel is started on client, the client will send:
- The fully qualified type name of the ViewModel
- A unique identifier (to identify it amongst multiple instances)
- The fully qualified type name of the `Model`, it is using, and whether it is `@Serializable`.
- The fully qualified type name of the `Intent`, it is using, and if it is `sealed` and `@Serializable`, the list of serializable subtypes.
- The fully qualified type name of the `Signal`, it is using, and if it is `sealed` and `@Serializable`, the list of serializable subtypes.

That information may also be requested by the server if it receives an observation that happened in a ViewModel it doesn't know about.

### Observation
The client will observe and report to the server all objects (`Model`, `Intent` or `Signal`). An event reported to the server contains:
- The unique identifier of the ViewModel it happened into.
- The fully qualified type of the object.
- The `String` representation of that object (its `toString`).
- The JSON representation of that object _if it is `@Serializable`_.

### Close
When a ViewModel closes, the client will report to the server the end of the ViewModel.

### Intervention
The server may send to the client and object (`Model`, `Intent` or `Signal`) that will be sent or received by the ViewModel. That feature is only available if the object type was reported to be `@Serializable` in the ViewModel information. In that case, the server may send to the client:
- The unique identifier of the ViewModel that will emit or receive the object.
- The fully qualified type of the object.
- A JSON representation of that object.

The client will then report back success or error (for example, bad JSON representation, or ViewModel was closed in the meantime).
