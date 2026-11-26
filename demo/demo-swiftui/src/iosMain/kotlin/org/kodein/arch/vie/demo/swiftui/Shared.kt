// Apart from the start of the TimeMachine probe (DemoTimeMachine.kt) and the `testing` package, which
// holds the ViewModel that the tests of the Swift helpers drive, this module has no Kotlin code of
// the demo itself: it only gathers the modules the SwiftUI demo shares, and exports them in the
// `Shared` framework. Gradle links no framework from a source set without a single source file,
// hence this one.
package org.kodein.arch.vie.demo.swiftui
