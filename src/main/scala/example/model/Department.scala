package example.model

import java.util.UUID
import example.Func._
import db._

final case class Department(
  id: UUID,
  name: String,
  employees: Vector[Employee],
)

object Department {
  implicit val dbConv: DbConv[Department, DbDepartment, UUID] =
    new DbConv[Department, DbDepartment, UUID] {
      override def mkNoChild(dt: DbDepartment): Department =
        Department(
          dt.id,
          dt.name,
          employees = Vector.empty,
        )

      override def getId(dt: DbDepartment): UUID = dt.id
    }

  implicit val addChild: AddChild[Department, Employee] = (t, c) =>
    t.copy(employees = c)
}
