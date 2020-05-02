package example.model

import java.util.UUID
import example.Func._
import db._

final case class Employee(
  id: UUID,
  name: String,
)

object Employee {

  implicit val dbConv: DbConv[Employee, DbEmployee, UUID] =
    new DbConv[Employee, DbEmployee, UUID] {
      override def mkNoChild(dt: DbEmployee): Employee =
        Employee(
          id = dt.id,
          name = dt.name,
        )

      override def getId(dt: DbEmployee): UUID = dt.id
    }
}
