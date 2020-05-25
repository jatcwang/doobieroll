package example.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import shapeless._

case class Wrapper(c: DbCompany, d: DbDepartment, e: DbEmployee) {
  def asHList: DbCompany :: DbDepartment :: DbEmployee :: HNil =
    c :: d :: e :: HNil
}

object Wrapper {
  private implicit val employeeCodec: Codec.AsObject[DbEmployee] = deriveCodec[DbEmployee]
  private implicit val departmentCodec: Codec.AsObject[DbDepartment] = deriveCodec[DbDepartment]
  private implicit val companyCodec = deriveCodec[DbCompany]
  implicit val wrapperCodec: Codec.AsObject[Wrapper] = deriveCodec[Wrapper]
}
