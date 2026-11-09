package ldv.shuuen.features.training.common

import androidx.compose.ui.graphics.Color

/** The colors that mark a right or wrong answer, shared by the play screens and level stats. */
enum class AnswerColors(val color: Color) {
  Correct(Color(0xff32cc73)),
  Incorrect(Color(0xffe74d3c)),
}
