package example

import java.util.UUID

import shapeless.{::, HNil}
import TestModelHelpers._
import model._

object TestData {

  val c1 = UUID.fromString("00000000-0000-0001-0000-000000000000")
  val c2 = UUID.fromString("00000000-0000-0002-0000-000000000000")
  val c3 = UUID.fromString("00000000-0000-0003-0000-000000000000")
  val d1 = UUID.fromString("00000000-0000-0000-0001-000000000000")
  val d2 = UUID.fromString("00000000-0000-0000-0002-000000000000")
  val d3 = UUID.fromString("00000000-0000-0000-0003-000000000000")
  val e1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val e2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
  val e3 = UUID.fromString("00000000-0000-0000-0000-000000000003")

  val c1db = DbCompany(c1, "Comp1")
  val c2db = DbCompany(c2, "Comp2")

  val d1db = DbDepartment(d1, c1, "Dep1")
  val d2db = DbDepartment(d2, c2, "Dep2")

  val e1db = DbEmployee(e1, d1, "Emp1")
  val e2db = DbEmployee(e2, d1, "Emp2")
  val e3db = DbEmployee(e3, d2, "Emp3")

  val dbRows: Vector[(DbCompany, DbDepartment, DbEmployee)] = Vector(
    (c1db, d1db, e1db),
    (c1db, d1db, e2db),
    (c2db, d2db, e3db),
  )

  val dbRowsHList: Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil] = {
    dbRows.map(dbRowsToHlist)
  }

  val expectedCompanies = Vector(
    model.Company(
      c1,
      "Comp1",
      departments = Vector(
        model.Department(
          d1,
          "Dep1",
          employees = Vector(
            model.Employee(
              e1,
              "Emp1",
            ),
            model.Employee(
              e2,
              "Emp2",
            ),
          ),
        ),
      ),
    ),
    model.Company(
      c2,
      "Comp2",
      departments = Vector(
        model.Department(
          d2,
          "Dep2",
          employees = Vector(
            model.Employee(
              e3,
              "Emp3",
            ),
          ),
        ),
      ),
    ),
  )


}
