package org.kodein.arch.vie.demo.data

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val title: String,
    val description: String,
    val completed: Boolean
)
