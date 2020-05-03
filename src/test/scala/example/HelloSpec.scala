package example

import java.util.UUID

import example.model.db.{DbCompany, DbDepartment, DbEmployee}
import example.model.{db, Company, Department, Employee}
import zio.test.Assertion._
import zio.test._
import TestModelHelpers._

object HelloSpec extends DefaultRunnableSpec {
  override def spec = suite("HelloWorldSpec")(
    test("LOL") {
      val example: List[(DbCompany, DbDepartment, DbEmployee)] = List(
        (c1db, d1db, e1db),
        (c1db, d1db, e2db),
        (c2db, d2db, e3db),
      )

      val res: List[Company] = Func.assembleOrdered[
        Company,
        Department,
        Employee,
        DbCompany,
        DbDepartment,
        DbEmployee,
        UUID,
        UUID,
        UUID,
      ](example)

      val companies = List(
        model.Company(
          c1,
          "Comp1",
          departments = List(
            model.Department(
              d1,
              "Dep1",
              employees = List(
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
          departments = List(
            model.Department(
              d2,
              "Dep2",
              employees = List(
                model.Employee(
                  e3,
                  "Emp3",
                ),
              ),
            ),
          ),
        ),
      )

      assert(normalizeCompanies(res))(equalTo(companies))
    },
    testM("assmebleOrdered works") {
      check(Gen.listOf(genCompany)) { origCompanies =>
        val rows = origCompanies.flatMap(companyToDbRows).sortBy {
          case (t1, t2, t3) => (t1.id, t2.id, t3.id)
        }

        val result = Func.assembleOrdered[
          Company,
          Department,
          Employee,
          DbCompany,
          DbDepartment,
          DbEmployee,
          UUID,
          UUID,
          UUID,
        ](rows)

        assert(normalizeCompanies(result))(
          equalTo(normalizeCompanies(origCompanies)),
        )

      }
    },
    testM("con conversion works") {
      checkNM(50)(Gen.listOf(genCompany)) { companies =>
        zio.random
          .shuffle(
            companies.flatMap(companyToDbRows),
          )
          .map { rows =>
            val orig: List[Company] = Func.assembleUnordered[
              Company,
              Department,
              Employee,
              DbCompany,
              DbDepartment,
              DbEmployee,
              UUID,
              UUID,
              UUID,
            ](rows)

            assert(normalizeCompanies(orig))(equalTo(normalizeCompanies(orig)))
          }
      }
    },
  )

  private def companyToDbRows(
    c: Company,
  ): List[Tuple3[DbCompany, DbDepartment, DbEmployee]] = {
    import scala.collection.mutable

    val rows =
      mutable.ArrayBuffer.empty[Tuple3[DbCompany, DbDepartment, DbEmployee]]

    val dbCompany = db.DbCompany(c.id, c.name)
    c.departments.foreach { d =>
      val dbDepartment =
        db.DbDepartment(id = d.id, companyId = c.id, name = d.name)
      d.employees.foreach { e =>
        rows += Tuple3(
          dbCompany,
          dbDepartment,
          db.DbEmployee(id = e.id, departmentId = d.id, name = e.name),
        )
      }
    }

    rows.toList
  }

  val c1 = UUID.fromString("00000000-0000-0001-0000-000000000000")
  val c2 = UUID.fromString("00000000-0000-0002-0000-000000000000")
  val c3 = UUID.fromString("00000000-0000-0003-0000-000000000000")
  val d1 = UUID.fromString("00000000-0000-0000-0001-000000000000")
  val d2 = UUID.fromString("00000000-0000-0000-0002-000000000000")
  val d3 = UUID.fromString("00000000-0000-0000-0003-000000000000")
  val e1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val e2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
  val e3 = UUID.fromString("00000000-0000-0000-0000-000000000003")

  val c1db = db.DbCompany(c1, "Comp1")
  val c2db = db.DbCompany(c2, "Comp2")

  val d1db = db.DbDepartment(d1, c1, "Dep1")
  val d2db = db.DbDepartment(d2, c2, "Dep2")

  val e1db = db.DbEmployee(e1, d1, "Emp1")
  val e2db = db.DbEmployee(e2, d1, "Emp2")
  val e3db = db.DbEmployee(e3, d2, "Emp3")

}
