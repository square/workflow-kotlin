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
import Workflow
import WorkflowUI

// MARK: Input and Output

public struct TakeTurnsWorkflow: Workflow {
    public typealias State = Turn
    
//    public typealias Rendering = GamePlayScreen
    
    var playerX: String
    var playerO: String
    var realTakeTurnsWorkflow: RealTakeTurnsWorkflow

    public typealias Output = Never
}


// MARK: State and Initialization

extension TakeTurnsWorkflow {

    public func makeInitialState() -> TakeTurnsWorkflow.State {
        let playerInfo = PlayerInfo(xName: playerX, oName: playerO)
        let takeTurnsProps = TakeTurnsProps.Companion().doNewGame(playerInfo: playerInfo)
        return realTakeTurnsWorkflow.initialState(props: takeTurnsProps, snapshot: nil)
    }
}

// MARK: Actions

extension RealTakeTurnsWorkflow.ActionTakeSquare : WorkflowAction {
    public func apply(toState state: inout Turn) -> Never? {
        print("Before:\n\(state.board)")
        let updater = shared.Workflow_coreWorkflowActionUpdater(self, props: nil, state: state)
        
        self.apply(updater)
        state = updater.state!
        print("After:\n\(state.board)")
        return nil
    }
    
    public typealias WorkflowType = TakeTurnsWorkflow
}

// MARK: Rendering

extension TakeTurnsWorkflow {
    public typealias Rendering = GamePlayScreen

    public func render(state: TakeTurnsWorkflow.State, context: RenderContext<TakeTurnsWorkflow>) -> GamePlayScreen {
        let sink = context.makeSink(of: RealTakeTurnsWorkflow.ActionTakeSquare.self)

        let gameState: GameState = GameState.fromTurn(turn: state)
        let board: [[Board.Cell]] = Board.fromBoardKt(board: state.board)
        
        
        let gamePlayScreen: GamePlayScreen = GamePlayScreen(
            gameState: gameState,
            playerX: playerX,
            playerO: playerO,
            board: board,
            onSelected: { row, col in
                switch (gameState) {
                case .ongoing:
                    sink.send(RealTakeTurnsWorkflow.ActionTakeSquare(row: Int32(row), col: Int32(col)))
                case .win(_):
                    break
                case .tie:
                    break
                }
                return
            }
        )
        
        return gamePlayScreen
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
