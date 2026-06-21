package ldv.shuuen.core.music

enum class ContextSource(val dbValue: String) {
  // bundled with this app
  BuiltIn("built_in"),
  // custom user created, globally shared
  UserGlobal("user"),
  // custom user created, local and bundled to the level
  UserLocal("local"),
  // imported from other source (custom remote repository?)
  Imported("imported"),
}