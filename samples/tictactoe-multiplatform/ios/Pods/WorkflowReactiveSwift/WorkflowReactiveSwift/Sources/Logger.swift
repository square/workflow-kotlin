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
    static let worker = OSLog(subsystem: "com.squareup.WorkflowReactiveSwift", category: "Worker")
}

// MARK: -

/// Logs Worker events to OSLog
final class WorkerLogger<WorkerType: Worker> {
    init() {}

    @available(iOS 12.0, macOS 10.14, *)
    var signpostID: OSSignpostID { OSSignpostID(log: .worker, object: self) }

    // MARK: - Workers

    func logStarted() {
        if #available(iOS 12.0, macOS 10.14, *) {
            os_signpost(
                .begin,
                log: .worker,
                name: "Running",
                signpostID: self.signpostID,
                "Worker: %{private}@",
                String(describing: WorkerType.self)
            )
        }
    }

    func logFinished(status: StaticString) {
        if #available(iOS 12.0, macOS 10.14, *) {
            os_signpost(.end, log: .worker, name: "Running", signpostID: signpostID, status)
        }
    }

    func logOutput() {
        if #available(iOS 12.0, macOS 10.14, *) {
            os_signpost(
                .event,
                log: .worker,
                name: "Worker Event",
                signpostID: signpostID,
                "Event: %{private}@",
                String(describing: WorkerType.self)
            )
        }
    }
}
