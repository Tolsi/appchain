package ru.tolsi.appchain

import spray.json._

object DataEntry extends DefaultJsonProtocol {
  implicit val dataEntryFormat: JsonFormat[DataEntry[_]] = new JsonFormat[DataEntry[_]] {
    override def write(obj: DataEntry[_]): JsValue = obj.asJson

    override def read(json: JsValue): DataEntry[_] = {
      val objectFields = json.asJsObject.fields
      objectFields.get("key") match {
        case Some(JsString(key)) =>
          objectFields.get("type") match {
            case Some(JsString("integer")) =>
              objectFields.get("value") match {
                case Some(JsNumber(n)) => IntegerDataEntry(key, n.toLong)
                case _ => throw new IllegalStateException("value is missing or not an integer")
              }
            case Some(JsString("boolean")) =>
              objectFields.get("value") match {
                case Some(JsBoolean(b)) => BooleanDataEntry(key, b)
                case _ => throw new IllegalStateException("value is missing or not a boolean value")
              }
            case Some(JsString("binary")) =>
              objectFields.get("value") match {
                case Some(JsString(enc)) =>
                  ByteStr.decodeBase64(enc).fold(ex => throw new IllegalStateException(ex.getMessage), bstr => BinaryDataEntry(key, bstr))
                case _ => throw new IllegalStateException("value is missing or not a string")
              }
            case Some(JsString("string")) =>
              objectFields.get("value") match {
                case Some(JsString(str)) => StringDataEntry(key, str)
                case _ => throw new IllegalStateException("value is missing or not a string")
              }
            case Some(JsString(t)) => throw new IllegalStateException(s"unknown type $t")
            case _ => throw new IllegalStateException("type is missing")
          }
        case _ => throw new IllegalStateException("key is missing")
      }
    }
  }
}

sealed abstract class DataEntry[T](val key: String, val value: T) extends DefaultJsonProtocol {
  def jsValue: JsValue

  def asJson: JsObject = JsObject(
    "key" -> key.toJson,
    "type" -> (this match {
      case _: IntegerDataEntry => "integer"
      case _: BooleanDataEntry => "boolean"
      case _: BinaryDataEntry => "binary"
      case _: StringDataEntry => "string"
    }).toJson,
    "value" -> jsValue
  )
}

case class IntegerDataEntry(override val key: String, override val value: Long) extends DataEntry[Long](key, value) {
  override def jsValue: JsValue = JsNumber(value)
}

case class BooleanDataEntry(override val key: String, override val value: Boolean) extends DataEntry[Boolean](key, value) {
  override def jsValue: JsValue = JsBoolean(value)
}

case class BinaryDataEntry(override val key: String, override val value: ByteStr) extends DataEntry[ByteStr](key, value) {
  override def jsValue: JsValue = JsString(value.base64)
}

case class StringDataEntry(override val key: String, override val value: String) extends DataEntry[String](key, value) {
  override def jsValue: JsValue = JsString(value)
}

