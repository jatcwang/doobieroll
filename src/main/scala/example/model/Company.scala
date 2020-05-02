package example.model

import java.util.UUID

import example.Func.{AddChild, DbConv}
import example.model.db.DbCompany

final case class Company(
  id: UUID,
  name: String,
  departments: List[Department],
)

object Company {
  implicit val dbConv: DbConv[Company, DbCompany, UUID] =
    new DbConv[Company, DbCompany, UUID] {
      override def mkNoChild(dt: DbCompany): Company =
        Company(
          dt.id,
          dt.name,
          departments = List.empty,
        )

      override def getId(dt: DbCompany): UUID =
        dt.id
    }

  implicit val addChild: AddChild[Company, Department] = (t, c) =>
    t.copy(departments = c)
}
