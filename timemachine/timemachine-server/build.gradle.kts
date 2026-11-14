import java.util.zip.ZipFile
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())
}

// The types from other modules that the compiler cannot see are immutable (Uuid, Instant, the protocol,
// the JSON tree): listed there, they and the classes that hold them are compared by equals, not identity.
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

// Opt-in: `-Pvie.composeReports=true` writes the Compose compiler's reports and metrics to
// build/compose-reports/, to read which composables skip and which of their parameters are unstable.
// A report is only written for what is compiled: add `--rerun-tasks` when the sources did not change.
if (providers.gradleProperty("vie.composeReports").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose-reports")
        metricsDestination = layout.buildDirectory.dir("compose-reports")
    }
}

dependencies {
    implementation(projects.timemachine.vieTimemachineShared)
    implementation(projects.vieMvi)

    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.swing)

    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.websockets)

    testImplementation(projects.vieTest)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
}

// The package the Compose resources are generated in, which is also the folder they have in a jar.
val resourcesPackageName = "org.kodein.arch.vie.timemachine.server.resources"

// The app icon files are generated from the `AppIcon` composable and never committed. The exporter
// lives in the test source set, so it stays out of the shipped jar.
val exportAppIcon = tasks.register<JavaExec>("exportAppIcon") {
    group = "build"
    description = "Renders AppIcon to build/appIcon/: a 512 px .png, an .ico and an .icns."
    val out = layout.buildDirectory.dir("appIcon")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "org.kodein.arch.vie.timemachine.server.ui.theme.AppIconExporterKt"
    args(out.get().asFile.absolutePath)
    outputs.dir(out)
}

// The window's screens as images, to look at after a change to the views: nothing compares them, and no
// build needs them. The exporter lives in the test source set, beside the fixtures it renders.
tasks.register<JavaExec>("exportShellRenders") {
    group = "verification"
    description = "Renders the inspector window's screens, in both themes, to build/shell-render/."
    val out = layout.buildDirectory.dir("shell-render")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "org.kodein.arch.vie.timemachine.server.ui.shell.ShellRenderExporterKt"
    args(out.get().asFile.absolutePath)
    outputs.dir(out)
}

// The design system's components in every variant, as images, to look at while the views move onto them:
// nothing compares them, and no build needs them. The exporter lives in the test source set, beside the scenes.
tasks.register<JavaExec>("exportGalleryRenders") {
    group = "verification"
    description = "Renders the design system's gallery, in both themes, to build/gallery-render/."
    val out = layout.buildDirectory.dir("gallery-render")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "org.kodein.arch.vie.timemachine.server.ui.designsystem.GalleryRenderExporterKt"
    args(out.get().asFile.absolutePath)
    outputs.dir(out)
}

// One of the files `exportAppIcon` writes. It is derived with `map`, which keeps the dependency on the
// task for whatever reads it: `flatMap` would drop it, and the packaging would find no icon.
fun appIcon(name: String): Provider<File> = exportAppIcon.map { File(it.outputs.files.singleFile, name) }

compose {
    desktop {
        application {
            mainClass = "org.kodein.arch.vie.timemachine.server.MainKt"

            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                // The window's title, in `main.kt`.
                packageName = "VIE TimeMachine"
                // Not the root's 0.1.0: jpackage refuses a version that starts with 0 on macOS, and the plugin's
                // own validation lets it through, so `createDistributable` would fail late.
                packageVersion = "1.0.0"
                description = "Live inspector for VIE ViewModels"
                vendor = "Kodein Koders"
                // The app runs on a runtime image cut from the JDK, which only has the modules it is told to: these are
                // the ones `suggestRuntimeModules` finds the jars using, and the app fails late without them.
                modules("java.instrument", "java.management", "jdk.unsupported")

                macOS {
                    bundleID = "org.kodein.arch.vie.timemachine"
                    appCategory = "public.app-category.developer-tools"
                    iconFile.fileProvider(appIcon("AppIcon.icns"))
                }
                windows {
                    menuGroup = "Kodein"
                    // Fixed once: it is what lets a newer installer replace an older one.
                    upgradeUuid = "7cc10cda-95bf-4fe2-b916-775b6672651c"
                    iconFile.fileProvider(appIcon("AppIcon.ico"))
                }
                linux {
                    // A Debian package name has no capitals and no space.
                    packageName = "vie-timemachine"
                    iconFile.fileProvider(appIcon("AppIcon.png"))
                }
            }
        }
    }

    resources {
        packageOfResClass = resourcesPackageName
        generateResClass = always
    }
}

// On macOS the plugin gives `run` the Dock icon, as a file that has to exist.
tasks.matching { it.name == "run" }.configureEach { dependsOn(exportAppIcon) }

/**
 * What the packaged app has to carry from this module's jar: the fonts, and the licences that must
 * ship with them (the OFL asks for its notice and licence with every copy), each as the repository has it.
 */
abstract class CheckDistributable : DefaultTask() {

    /** The app image `createDistributable` makes, whatever its layout on this OS. */
    @get:InputDirectory
    abstract val app: DirectoryProperty

    /** Where Compose resources are in a jar: `composeResources/<this>/`. */
    @get:Input
    abstract val resourcesPackage: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val licenses: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val fonts: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val root = app.get().asFile
        // The plugin appends a hash to a jar's name, so the module's jar is found by what precedes it.
        val jars = root.walkTopDown().filter { it.isFile && it.extension == "jar" && it.name.startsWith("timemachine-server-") }.toList()
        if (jars.size != 1) throw GradleException("Expected one timemachine-server jar under $root, found ${jars.map { it.name }}")
        val jar = jars.single()
        val prefix = "composeResources/${resourcesPackage.get()}/"
        val problems = buildList {
            ZipFile(jar).use { zip ->
                for ((folder, files) in listOf("files/licenses/" to licenses.files, "font/" to fonts.files)) {
                    for (file in files.sortedBy { it.name }) {
                        val entry = zip.getEntry(prefix + folder + file.name)
                        when {
                            entry == null -> add("missing: $prefix$folder${file.name}")
                            !zip.getInputStream(entry).use { it.readBytes() }.contentEquals(file.readBytes()) ->
                                add("differs from the repository's: ${entry.name}")
                        }
                    }
                }
            }
        }
        if (problems.isNotEmpty()) throw GradleException("${jar.name} does not carry what must ship with it:\n" + problems.joinToString("\n") { "  $it" })
        logger.lifecycle("${jar.name} carries ${licenses.files.size} licence files and ${fonts.files.size} fonts, as the repository has them.")
    }
}

// Opt-in, not part of `check`: it builds the app image. Run it before shipping, or after touching the packaging.
tasks.register<CheckDistributable>("checkDistributable") {
    group = "verification"
    description = "Builds the app image and checks that it carries the bundled fonts and their licences (decision 25)."
    dependsOn("createDistributable")
    app.set(layout.buildDirectory.dir("compose/binaries/main/app"))
    resourcesPackage.set(resourcesPackageName)
    licenses.from(fileTree("src/main/composeResources/files/licenses"))
    fonts.from(fileTree("src/main/composeResources/font"))
}
