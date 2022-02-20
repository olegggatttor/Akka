package server

object ServerUtils {
  def serverResponseFor: String => Seq[String] = searcher =>
    Seq(
      s"${searcher}_response_1",
      s"${searcher}_response_2",
      s"${searcher}_response_3",
      s"${searcher}_response_4",
      s"${searcher}_response_5")
}
