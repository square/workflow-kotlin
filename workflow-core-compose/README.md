# Workflow Runtime with Compose Optimizations.

This module contains extensions on the Workflow Core and Workflow Runtime classes that allow
for the Compose runtime to optimize which workflows are rendered in a render pass.

This is entirely experimental and has no dedicated tests yet, so please do not use unless you
are experimenting.

To use it you can pass the [ComposeRuntimePlugin] to [renderWorkflowIn].
