package example

import java.util.UUID

import example.model.db.{DbCompany, DbDepartment, DbEmployee}
import example.model.{db, Company, Department, Employee}
import zio.test.Assertion._
import zio.test._
import TestModelHelpers._
import TestData._

object HelloSpec extends DefaultRunnableSpec {
  override def spec = suite("HelloWorldSpec")(
    test("LOL") {

      val res: Vector[Company] = Func.assembleOrdered[
        Company,
        Department,
        Employee,
        DbCompany,
        DbDepartment,
        DbEmployee,
        UUID,
        UUID,
        UUID,
      ](dbRows)

      assert(normalizeCompanies(res))(equalTo(expectedCompanies))
    },
    testM("assmebleOrdered works") {
      check(Gen.listOf(genCompany).map(_.toVector)) { origCompanies =>
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
      checkNM(50)(Gen.listOf(genCompany).map(_.toVector)) { companies =>
        zio.random
          .shuffle(
            companies.flatMap(companyToDbRows).toList,
          )
          .map(_.toVector)
          .map { rows =>
            val orig: Vector[Company] = Func.assembleUnordered[
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
  ): Vector[Tuple3[DbCompany, DbDepartment, DbEmployee]] = {
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

    rows.toVector
  }

}
