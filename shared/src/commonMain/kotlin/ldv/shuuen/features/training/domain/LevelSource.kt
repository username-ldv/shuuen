package ldv.shuuen.features.training.domain

enum class LevelSource(val dbValue: String) {
  BuiltIn("built_in"),
  User("user"),
  Imported("imported"),
}