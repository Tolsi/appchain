package ru.tolsi.appchain

case class MockKeyValueState(data: Map[String, Seq[DataEntry[_]]] = Map.empty) {
  def update(address: String, newValues: Seq[DataEntry[_]]): MockKeyValueState = {
    def mergeData(first: Seq[DataEntry[_]], second: Seq[DataEntry[_]]): Seq[DataEntry[_]] = {
      val firstMap = first.map(o => o.key -> o).toMap[String, DataEntry[_]]
      val secondMap = second.map(o => o.key -> o).toMap[String, DataEntry[_]]

      val bothKeys = firstMap.keys ++ secondMap.keys

      bothKeys.map(key => secondMap.getOrElse(key, firstMap(key))).toSeq
    }

    val oldValues = data.getOrElse(address, Seq.empty)
    MockKeyValueState(data.updated(address, mergeData(oldValues, newValues)))
  }
}