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

#if canImport(UIKit)

    import ReactiveSwift
    import UIKit
    import Workflow

    /// Drives view controllers from a root Workflow.
    public final class ContainerViewController<ScreenType, Output>: UIViewController where ScreenType: Screen {
        /// Emits output events from the bound workflow.
        public var output: Signal<Output, Never> {
            return workflowHost.output
        }

        internal let rootViewController: DescribedViewController

        private let workflowHost: WorkflowHost<RootWorkflow<ScreenType, Output>>

        private let (lifetime, token) = Lifetime.make()

        public var rootViewEnvironment: ViewEnvironment {
            didSet {
                // Re-render the current rendering with the new environment
                render(screen: workflowHost.rendering.value, environment: rootViewEnvironment)
            }
        }

        public init<W: AnyWorkflowConvertible>(workflow: W, rootViewEnvironment: ViewEnvironment = .empty) where W.Rendering == ScreenType, W.Output == Output {
            self.workflowHost = WorkflowHost(workflow: RootWorkflow(workflow))
            self.rootViewController = DescribedViewController(screen: workflowHost.rendering.value, environment: rootViewEnvironment)
            self.rootViewEnvironment = rootViewEnvironment

            super.init(nibName: nil, bundle: nil)

            workflowHost
                .rendering
                .signal
                .take(during: lifetime)
                .observeValues { [weak self] screen in
                    guard let self = self else { return }
                    self.render(screen: screen, environment: self.rootViewEnvironment)
                }
        }

        /// Updates the root Workflow in this container.
        public func update<W: AnyWorkflowConvertible>(workflow: W) where W.Rendering == ScreenType, W.Output == Output {
            workflowHost.update(workflow: RootWorkflow(workflow))
        }

        public required init?(coder aDecoder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        private func render(screen: ScreenType, environment: ViewEnvironment) {
            rootViewController.update(screen: screen, environment: environment)
        }

        override public func viewDidLoad() {
            super.viewDidLoad()

            view.backgroundColor = .white

            addChild(rootViewController)
            view.addSubview(rootViewController.view)
            rootViewController.didMove(toParent: self)
        }

        override public func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            rootViewController.view.frame = view.bounds
        }

        override public var childForStatusBarStyle: UIViewController? {
            return rootViewController
        }

        override public var childForStatusBarHidden: UIViewController? {
            return rootViewController
        }

        override public var childForHomeIndicatorAutoHidden: UIViewController? {
            return rootViewController
        }

        override public var childForScreenEdgesDeferringSystemGestures: UIViewController? {
            return rootViewController
        }

        override public var supportedInterfaceOrientations: UIInterfaceOrientationMask {
            return rootViewController.supportedInterfaceOrientations
        }
    }

    /// Wrapper around an AnyWorkflow that allows us to have a concrete
    /// WorkflowHost without ContainerViewController itself being generic
    /// around a Workflow.
    fileprivate struct RootWorkflow<Rendering, Output>: Workflow {
        typealias State = Void
        typealias Output = Output
        typealias Rendering = Rendering

        var wrapped: AnyWorkflow<Rendering, Output>

        init<W: AnyWorkflowConvertible>(_ wrapped: W) where W.Rendering == Rendering, W.Output == Output {
            self.wrapped = wrapped.asAnyWorkflow()
        }

        func render(state: State, context: RenderContext<RootWorkflow>) -> Rendering {
            return wrapped
                .mapOutput { AnyWorkflowAction(sendingOutput: $0) }
                .rendered(in: context)
        }
    }

#endif
