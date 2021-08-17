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

import shared
import AlertContainer
import BackStackContainer
import ModalContainer
import ReactiveSwift
import Workflow
import WorkflowReactiveSwift
import WorkflowUI

// MARK: Input and Output

struct AuthenticationWorkflow: Workflow {
    var authenticationService: RealAuthService

    enum Output {
        case authorized(session: String)
    }
}

// MARK: State and Initialization

extension AuthenticationWorkflow {
    enum State: Equatable {
        case emailPassword
        case authenticationErrorAlert(error: RealAuthService.AuthenticationError?)
        case authorizingEmailPassword(email: String, password: String)
        case twoFactor(intermediateSession: String, authenticationError: RealAuthService.AuthenticationError?)
        case authorizingTwoFactor(twoFactorCode: String, intermediateSession: String)
    }

    func makeInitialState() -> AuthenticationWorkflow.State {
        return .emailPassword
    }
}

// MARK: Actions

extension AuthenticationWorkflow {
    enum Action: WorkflowAction {
        typealias WorkflowType = AuthenticationWorkflow

        case back
        case login(email: String, password: String)
        case verifySecondFactor(intermediateSession: String, twoFactorCode: String)
        // Cannot convert value of type 'AuthServiceAuthResponse' to expected argument type 'RealAuthService.AuthenticationResponse'
        case authenticationSucceeded(response: RealAuthService.AuthenticationResponse)
        case authenticationError(RealAuthService.AuthenticationError)
        case dismissAuthenticationAlert

        func apply(toState state: inout AuthenticationWorkflow.State) -> AuthenticationWorkflow.Output? {
            switch self {
            case .back:
                switch state {
                case .twoFactor:
                    state = .emailPassword

                default:
                    fatalError("Unexpected back in state \(state)")
                }

            case .login(email: let email, password: let password):
                state = .authorizingEmailPassword(email: email, password: password)

            case .verifySecondFactor(intermediateSession: let intermediateSession, twoFactorCode: let twoFactorCode):
                state = .authorizingTwoFactor(twoFactorCode: twoFactorCode, intermediateSession: intermediateSession)

            case .authenticationSucceeded(response: let response):
                if response.secondFactorRequired {
                    state = .twoFactor(intermediateSession: response.token, authenticationError: nil)
                } else {
                    return .authorized(session: response.token)
                }

            case .dismissAuthenticationAlert:
                state = .emailPassword

            case .authenticationError(let error):
                switch state {
                case .authorizingEmailPassword:
                    state = .authenticationErrorAlert(error: error)
                case .authorizingTwoFactor(twoFactorCode: _, intermediateSession: let intermediateSession):
                    state = .twoFactor(intermediateSession: intermediateSession, authenticationError: error)

                default:
                    fatalError("Unexpected authentication error in state \(state)")
                }
            }
            return nil
        }
    }
}

// MARK: Workers

extension AuthenticationWorkflow {
    struct AuthorizingEmailPasswordWorker: Worker {
        typealias Output = Action

        var authenticationService: RealAuthService
        var email: String
        var password: String

        func run() -> SignalProducer<Output, Never> {
            let authRequest = AuthServiceAuthRequest(email: email, password: password)
            return SignalProducer<AuthenticationWorkflow.Action, Never> { observer, disposable in
                authenticationService
                    .login(request: authRequest, onLogin: { response in
                        if (!response.errorMessage.isEmpty) {
                            observer.send(
                                value: .authenticationError(
                                    response.errorMessage.toAuthError()
                                )
                            )
                        } else {
                            observer.send(
                                value: .authenticationSucceeded(
                                    response: RealAuthService.AuthenticationResponse(
                                        token: response.token,
                                        secondFactorRequired: response.twoFactorRequired
                                    )
                                )
                            )
                        }
                    },
                    onError: { error in
                        observer.send(
                            value: .authenticationError(
                                error.description().toAuthError()
                            )
                        )
                    })
            }
            
//                .map { response -> Action in
//                    .authenticationSucceeded(response: response)
//                }
//                .flatMapError {
//                    SignalProducer(value: .authenticationError($0))
//                }
        }

        func isEquivalent(to otherWorker: AuthorizingEmailPasswordWorker) -> Bool {
            return email == otherWorker.email
                && password == otherWorker.password
        }
    }

    struct AuthorizingTwoFactorWorker: Worker {
        typealias Output = Action

        var authenticationService: RealAuthService
        var intermediateToken: String
        var twoFactorCode: String

        func run() -> SignalProducer<Output, Never> {
            let secondFactorReq = AuthServiceSecondFactorRequest(
                token: intermediateToken,
                secondFactor: twoFactorCode
            )
            return SignalProducer<AuthenticationWorkflow.Action, Never> { observer, disposable in
                authenticationService
                    .secondFactor(request: secondFactorReq, onLogin: { response in
                        if (!response.errorMessage.isEmpty) {
                            observer.send(value: .authenticationError(response.errorMessage.toAuthError()))
                        } else {
                            observer.send(
                                value: .authenticationSucceeded(
                                    response: RealAuthService.AuthenticationResponse(
                                        token: response.token,
                                        secondFactorRequired: response.twoFactorRequired
                                    )
                                )
                            )
                        }
                            
                    }, onError: { error in
                        observer.send(value: .authenticationError(error.description().toAuthError()))
                    }
                )
            }
        }


        func isEquivalent(to otherWorker: AuthenticationWorkflow.AuthorizingTwoFactorWorker) -> Bool {
            return intermediateToken == otherWorker.intermediateToken
                && twoFactorCode == otherWorker.twoFactorCode
        }
    }
}

// MARK: Rendering

extension AuthenticationWorkflow {
    typealias Rendering = AlertContainerScreen<ModalContainerScreen<BackStackScreen<AnyScreen>>>

    func render(state: AuthenticationWorkflow.State, context: RenderContext<AuthenticationWorkflow>) -> Rendering {
        let sink = context.makeSink(of: Action.self)

        var backStackItems: [BackStackScreen<AnyScreen>.Item] = []
        var modals: [ModalContainerScreenModal] = []
        var alert: Alert?

        let loginScreen = LoginWorkflow().mapOutput { output -> Action in
            switch output {
            case .login(email: let email, password: let password):
                return .login(email: email, password: password)
            }
        }.rendered(in: context)
        backStackItems.append(BackStackScreen.Item(screen: loginScreen.asAnyScreen(), barVisibility: .hidden))

        switch state {
        case .emailPassword:
            break

        case .authenticationErrorAlert(error: let error):
            if let error = error {
                alert = Alert(
                    title: "Error",
                    message: error.localizedDescription,
                    actions: [AlertAction(
                        title: "Ok",
                        style: AlertAction.Style.default,
                        handler: {
                            sink.send(.dismissAuthenticationAlert)
                        }
                    )]
                )
            }

        case .authorizingEmailPassword(email: let email, password: let password):
            AuthorizingEmailPasswordWorker(
                authenticationService: authenticationService,
                email: email,
                password: password
            ).running(in: context)
            modals.append(ModalContainerScreenModal(screen: AnyScreen(LoadingScreen()), style: .fullScreen, key: "", animated: false))

        case .twoFactor(intermediateSession: let intermediateSession, authenticationError: let authenticationError):
            backStackItems.append(twoFactorScreen(
                error: authenticationError,
                intermediateSession: intermediateSession,
                sink: sink
            ))

        case .authorizingTwoFactor(twoFactorCode: let twoFactorCode, intermediateSession: let intermediateSession):
            AuthorizingTwoFactorWorker(
                authenticationService: authenticationService,
                intermediateToken: intermediateSession,
                twoFactorCode: twoFactorCode
            ).running(in: context)

            backStackItems.append(twoFactorScreen(error: nil, intermediateSession: intermediateSession, sink: sink))
            modals.append(ModalContainerScreenModal(screen: AnyScreen(LoadingScreen()), style: .fullScreen, key: "", animated: false))
        }
        return AlertContainerScreen(
            baseScreen: ModalContainerScreen(
                baseScreen: BackStackScreen(
                    items: backStackItems
                ),
                modals: modals
            ),
            alert: alert
        )
    }

    private func twoFactorScreen(error: RealAuthService.AuthenticationError?, intermediateSession: String, sink: Sink<Action>) -> BackStackScreen<AnyScreen>.Item {
        let title: String
        if let authenticationError = error {
            title = authenticationError.localizedDescription
        } else {
            title = "Enter the one time code to continue"
        }

        let twoFactorScreen = TwoFactorScreen(
            title: title,
            onLoginTapped: { twoFactorCode in
                sink.send(.verifySecondFactor(
                    intermediateSession: intermediateSession,
                    twoFactorCode: twoFactorCode
                ))
            }
        )

        return BackStackScreen.Item(
            screen: twoFactorScreen.asAnyScreen(),
            barVisibility: .visible(BackStackScreen.BarContent(
                leftItem: BackStackScreen.BarContent.BarButtonItem.button(BackStackScreen.BarContent.Button(
                    content: .text("Cancel"),
                    handler: {
                        sink.send(.back)
                    }
                ))
            ))
        )
    }
}
