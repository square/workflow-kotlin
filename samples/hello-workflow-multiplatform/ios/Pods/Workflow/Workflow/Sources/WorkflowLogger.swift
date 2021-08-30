/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import os.signpost

private extension OSLog {
    static let workflow = OSLog(subsystem: "com.squareup.Workflow", category: "Workflow")
}

// MARK: -

/// Simple class that can be used to create signpost IDs based on an object pointer.
final class SignpostRef {
    init() {}
}

final class WorkflowLogger {
    // MARK: Workflows

    static func logWorkflowStarted<WorkflowType>(ref: WorkflowNode<WorkflowType>) {
        if #available(iOS 12.0, macOS 10.14, *) {
            let signpostID = OSSignpostID(log: .workflow, object: ref)
            os_signpost(
                .begin,
                log: .workflow,
                name: "Alive",
                signpostID: signpostID,
                "Workflow: %{public}@",
                String(describing: WorkflowType.self)
            )
        }
    }

    static func logWorkflowFinished<WorkflowType>(ref: WorkflowNode<WorkflowType>) {
        if #available(iOS 12.0, macOS 10.14, *) {
            let signpostID = OSSignpostID(log: .workflow, object: ref)
            os_signpost(.end, log: .workflow, name: "Alive", signpostID: signpostID)
        }
    }

    static func logSinkEvent<Action: WorkflowAction>(ref: AnyObject, action: Action) {
        if #available(iOS 12.0, macOS 10.14, *) {
            let signpostID = OSSignpostID(log: .workflow, object: ref)
            os_signpost(
                .event,
                log: .workflow,
                name: "Sink Event",
                signpostID: signpostID,
                "Event for workflow: %{public}@",
                String(describing: Action.WorkflowType.self)
            )
        }
    }

    // MARK: Rendering

    static func logWorkflowStartedRendering<WorkflowType>(ref: WorkflowNode<WorkflowType>) {
        if #available(iOS 12.0, macOS 10.14, *) {
            let signpostID = OSSignpostID(log: .workflow, object: ref)
            os_signpost(
                .begin,
                log: .workflow,
                name: "Render",
                signpostID: signpostID,
                "Render Workflow: %{public}@",
                String(describing: WorkflowType.self)
            )
        }
    }

    static func logWorkflowFinishedRendering<WorkflowType>(ref: WorkflowNode<WorkflowType>) {
        if #available(iOS 12.0, macOS 10.14, *) {
            let signpostID = OSSignpostID(log: .workflow, object: ref)
            os_signpost(.end, log: .workflow, name: "Render", signpostID: signpostID)
        }
    }
}
