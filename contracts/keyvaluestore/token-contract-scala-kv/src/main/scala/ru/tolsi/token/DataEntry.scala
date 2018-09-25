package ru.tolsi.token

import play.api.libs.json._

object DataEntry {
  implicit object Format extends Format[DataEntry[_]] {
    def reads(jsv: JsValue): JsResult[DataEntry[_]] = {
      jsv \ "key" match {
        case JsDefined(JsString(key)) =>
          jsv \ "type" match {
            case JsDefined(JsString("integer")) =>
              jsv \ "value" match {
                case JsDefined(JsNumber(n)) => JsSuccess(IntegerDataEntry(key, n.toLong))
                case _                      => JsError("value is missing or not an integer")
              }
            case JsDefined(JsString(t)) => JsError(s"unknown type $t")
            case _                      => JsError("type is missing")
          }
        case _ => JsError("key is missing")
      }
    }

    def writes(item: DataEntry[_]): JsValue = item.toJson
  }
}

sealed abstract class DataEntry[T](val key: String, val value: T) {
  def toJson: JsObject = Json.obj("key" -> key)
  def valid: Boolean   = key.length <= 100
}

case class IntegerDataEntry(override val key: String, override val value: Long) extends DataEntry[Long](key, value) {
  override def toJson: JsObject = super.toJson + ("type" -> JsString("integer")) + ("value" -> JsNumber(value))
}