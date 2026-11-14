package org.kodein.arch.vie.timemachine.server.ui.designsystem


/**
 * Every scene of the gallery, in the order they are rendered. A component's scene is added here in the
 * commit that adds the component, and removed in the one that deletes it.
 */
internal val GalleryScenes: List<GalleryScene> = listOf(
    ActionScene,
    TextActionScene,
    RuleScene,
)
