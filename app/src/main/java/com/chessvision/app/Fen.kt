package com.chessvision.app

/**
 * FEN parsing + Lichess URL helpers. Piece placement now comes from
 * [ChessQueriesRecognizer] (real ONNX inference); this file only deals with
 * the FEN string itself, so it doesn't care whether that FEN is real or fake.
 */

/**
 * Unicode glyph for each FEN piece letter. Deliberately using the same
 * *solid* glyph shape for both colors (white pieces don't use the hollow
 * "outline" Unicode variants, e.g. ♔) — those render with near-zero contrast
 * once colored to match a theme, which was the actual bug. Piece color is
 * applied separately based on [BoardSquare.isWhite], with a stroke outline,
 * the same way real chess sets (and Lichess' own piece art) stay readable
 * on both light and dark squares.
 */
private val PIECE_GLYPHS = mapOf(
    'r' to "♜", 'n' to "♞", 'b' to "♝", 'q' to "♛", 'k' to "♚", 'p' to "♟",
    'R' to "♜", 'N' to "♞", 'B' to "♝", 'Q' to "♛", 'K' to "♚", 'P' to "♟"
)

/** One square of a parsed board: its glyph (or blank), light/dark flag, and piece color. */
data class BoardSquare(val glyph: String, val isLight: Boolean, val isWhite: Boolean)

/** Parses the piece-placement field of a FEN into 8 rows of 8 squares. */
fun parseBoard(fen: String): List<List<BoardSquare>> {
    val placement = fen.substringBefore(' ')
    return placement.split("/").mapIndexed { rank, row ->
        val squares = mutableListOf<BoardSquare>()
        var file = 0
        for (ch in row) {
            if (ch.isDigit()) {
                repeat(ch.digitToInt()) {
                    squares.add(BoardSquare("", (rank + file) % 2 == 0, isWhite = false))
                    file++
                }
            } else {
                squares.add(
                    BoardSquare(
                        glyph = PIECE_GLYPHS[ch] ?: "",
                        isLight = (rank + file) % 2 == 0,
                        isWhite = ch.isUpperCase()
                    )
                )
                file++
            }
        }
        squares
    }
}

/** Per the project brief: only the piece-placement field is sent to Lichess;
 *  the editor lets the user fix side-to-move/castling/en-passant themselves. */
fun lichessEditorUrl(fen: String): String =
    "https://lichess.org/editor/" + fen.substringBefore(' ')
