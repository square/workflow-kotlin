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

enum GameState: Equatable {
    case ongoing(turn: Player)
    case win(Player)
    case tie

    mutating func toggle() {
        switch self {
        case .ongoing(turn: let player):
            switch player {
            case .x:
                self = .ongoing(turn: .o)
            case .o:
                self = .ongoing(turn: .x)
            }
        default:
            break
        }
    }
}



extension GameState {
    static func fromTurn(turn: Turn) -> GameState {
        if (BoardKt.hasVictory(turn.board)) {
            let player = turn.playing == shared.Player.x ? Player.x : Player.o
            return .win(player)
        } else if (BoardKt.isFull(turn.board)) {
            return .tie
        } else {
            let player = turn.playing == shared.Player.x ? Player.x : Player.o
            return .ongoing(turn: player)
        }
    }
}
