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
import Foundation


enum Player: Equatable {
    case x
    case o
}

struct Board: Equatable {
    private(set) var rows: [[Cell]]

    enum Cell: Equatable {
        case empty
        case taken(Player)
    }

    
    init(board: [[Any]]) {
        self.rows = [
            [Player.fromPlayerKt(player: board[0][0] as? shared.Player), Player.fromPlayerKt(player: board[0][1] as? shared.Player), Player.fromPlayerKt(player: board[0][2] as? shared.Player)],
            [Player.fromPlayerKt(player: board[1][0] as? shared.Player), Player.fromPlayerKt(player: board[1][1] as? shared.Player), Player.fromPlayerKt(player: board[1][2] as? shared.Player)],
            [Player.fromPlayerKt(player: board[2][0] as? shared.Player), Player.fromPlayerKt(player: board[2][1] as? shared.Player), Player.fromPlayerKt(player: board[2][2] as? shared.Player)],
        ]
    }
}

extension Board {
    static func fromBoardKt(board: [[Any]]) -> [[Board.Cell]] {
        return Board(board: board).rows
    }
}

