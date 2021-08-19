//
//  Extensions.swift
//  TicTacToe
//
//  Created by Brady Aiello on 8/18/21.
//

import shared
import Foundation

extension Player {
    static func fromPlayerKt(player: shared.Player?) -> Board.Cell {
        if (player != nil) {
            let playerSwift = player == shared.Player.x ? Player.x : Player.o
            return .taken(playerSwift)
        } else {
            return .empty
        }
    }
}
