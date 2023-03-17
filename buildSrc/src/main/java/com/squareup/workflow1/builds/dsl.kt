package com.squareup.workflow1.builds

import org.gradle.api.DomainObjectCollection
import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

inline fun TaskContainer.register(
  name: String,
  crossinline configurationAction: (Task) -> Unit
): TaskProvider<out Task> {
  return register(name) { configurationAction(this) }
}

inline fun <reified T : Task> TaskContainer.register(
  name: String,
  clazz: Class<out T>,
  crossinline configurationAction: (Task) -> Unit
): TaskProvider<out Task> {
  return register(name, clazz) { configurationAction(this) }
}

inline fun <S : Any> DomainObjectCollection<in S>.withType(
  clazz: Class<out S>,
  crossinline configuration: (S) -> Unit
): DomainObjectCollection<out S> = withType(clazz) { configuration(this) }

inline fun <reified T : Any> ExtensionContainer.configure(
  clazz: Class<out T>,
  noinline action: (T) -> Unit
) {
  configure(clazz) { action(this) }
}
