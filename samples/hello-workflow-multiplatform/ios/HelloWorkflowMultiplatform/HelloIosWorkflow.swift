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
    
    let delegate: HelloWorkflow<HelloScreen>
    let helloAction: (State) -> State

    typealias State = HelloWorkflowState
    
    typealias Rendering = HelloScreen
    typealias Output = Never
    
    init() {
        self.delegate = HelloWorkflow(renderingFactory: HelloIosRenderingFactory())
        self.helloAction = delegate.helloAction
    }
    
    enum Action : WorkflowAction {
        
        typealias WorkflowType = HelloIosWorkflow
        
        case hello(action: (State) -> State)
        
        func apply(toState state: inout HelloWorkflowState) -> HelloIosWorkflow.Output? {
            switch self {
            case .hello(let action):
                state = action(state)
            }
            return nil
        }
    }
    
    func makeInitialState() -> HelloWorkflowState {
        return delegate.initialState(props: KotlinUnit(), snapshot: nil)
    }
    
    func render(state: HelloWorkflowState, context: RenderContext<HelloIosWorkflow>) -> HelloScreen {
        let sink = context.makeSink(of: Action.self)

        return HelloScreen(
            message: "\(state)",
            onClick: { sink.send(.hello(action: helloAction)) }
        )
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
