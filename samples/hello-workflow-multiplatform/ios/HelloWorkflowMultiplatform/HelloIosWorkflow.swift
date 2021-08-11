//
//  Hacking.swift
//  HelloWorkflowMultiplatform
//
//  Created by Russell Wolf on 8/9/21.
//

import Workflow
import WorkflowUI
import shared

struct HelloIosWorkflow: Workflow {
    
    let delegate = HelloWorkflow<HelloScreen>(renderingFactory: HelloIosRenderingFactory())

    typealias State = HelloWorkflowState
    
    typealias Rendering = HelloScreen
    typealias Output = Never
    
    func makeInitialState() -> HelloWorkflowState {
        return delegate.initialState(props: KotlinUnit(), snapshot: nil)
    }
    
    func render(state: HelloWorkflowState, context: RenderContext<HelloIosWorkflow>) -> HelloScreen {
        let sink = context.makeSink(of: GenericAction.self)

        return HelloScreen(
            message: "\(state)",
            onClick: { sink.send(GenericAction(delegate.helloAction)) }
        )
    }
}

/// This could be a library-level helper class. As written it doesn't work for actions with non-null output, but it could probably be adjusted
class GenericAction<WorkflowType : Workflow>: WorkflowAction {
    let action: (WorkflowType.State) -> WorkflowType.State
    
    init(_ action: @escaping (WorkflowType.State) -> WorkflowType.State) {
        self.action = action
    }
    
    func apply(toState state: inout WorkflowType.State) -> WorkflowType.Output? {
        state = action(state)
        return nil
    }
}

class HelloScreen : HelloRendering, Screen {
    var message: String
    var onClick: () -> Void
    
    init(message: String, onClick: @escaping () -> Void) {
        self.message = message
        self.onClick = onClick
    }
    
    func viewControllerDescription(environment: ViewEnvironment) -> ViewControllerDescription {
        return HelloScreenViewController.description(for: self, environment: environment)
    }
}

class HelloIosRenderingFactory : HelloRenderingFactory {
    func createRendering(message: String, onClick: @escaping () -> Void) -> HelloRendering {
        HelloScreen(message: message, onClick: onClick)
    }
}

class HelloScreenViewController: ScreenViewController<HelloScreen> {

    private let button: UIButton

    required init(screen: HelloScreen, environment: ViewEnvironment) {
        button = UIButton()
        button.setTitleColor(.black, for: .normal)
        super.init(screen: screen, environment: environment)

        update(screen: screen)
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        button.addTarget(self, action: #selector(buttonPressed(sender:)), for: .touchUpInside)
        view.addSubview(button)
    }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()

        button.frame = view.bounds
    }

    override func screenDidChange(from previousScreen: HelloScreen, previousEnvironment: ViewEnvironment) {
        super.screenDidChange(from: previousScreen, previousEnvironment: previousEnvironment)
        update(screen: screen)
    }

    private func update(screen: HelloScreen) {
        button.setTitle(screen.message, for: .normal)
    }

    @objc private func buttonPressed(sender: UIButton) {
        screen.onClick()
    }

}
