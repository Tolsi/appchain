package ru.tolsi.appchain

case class Contract(appName: String, image: String, version: Int) {
  def containerName = s"$appName-$version"
  def stateContainerName = s"$containerName-state"
  def stateVolumeName = s"$containerName-state-volume"
}
