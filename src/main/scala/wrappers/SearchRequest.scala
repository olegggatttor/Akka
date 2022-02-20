package wrappers

case class SearchRequest(searchQuery : String, searchers : Seq[SearchEngine])
