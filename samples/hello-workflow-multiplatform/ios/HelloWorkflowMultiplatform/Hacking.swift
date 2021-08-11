//
//  Hacking.swift
//  HelloWorkflowMultiplatform
//
//  Created by Russell Wolf on 8/9/21.
//

import Workflow
import WorkflowUI
import shared

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

struct HelloIosWorkflow: Workflow {
    
    let delegate: HelloWorkflow<HelloScreen>

    typealias State = HelloWorkflowState
    
    typealias Rendering = HelloScreen
    typealias Output = Never
    
    enum Action : WorkflowAction {
        
        typealias WorkflowType = HelloIosWorkflow
        
        case toggle
        
        func apply(toState state: inout HelloWorkflowState) -> HelloIosWorkflow.Output? {
            switch state {
            case .hello:
                state = .goodbye
            case .goodbye:
                state = .hello
            default:
                fatalError("received invalid state \(state)")
            }
            return nil
        }
    }
    
    func makeInitialState() -> HelloWorkflowState {
        return .hello
    }
    
    func render(state: HelloWorkflowState, context: RenderContext<HelloIosWorkflow>) -> HelloScreen {
        let sink = context.makeSink(of: Action.self)

        return HelloScreen(
            message: "\(state)",
            onClick: { sink.send(.toggle) }
        )
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
