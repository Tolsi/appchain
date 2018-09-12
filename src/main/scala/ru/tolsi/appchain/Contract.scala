package ru.tolsi.appchain

case class Contract(appName: String, image: String, version: Int) {
  def containerName = s"$appName-$version"
  def patchedImageName = s"localhost:5000/$appName"
  def patchedTag = s"patched-$version"
  def patchedImageNameWithTag = s"$patchedImageName:$patchedTag"
  def stateContainerName = s"$containerName-state"
  def stateVolumeName = s"$containerName-state-volume"
}
