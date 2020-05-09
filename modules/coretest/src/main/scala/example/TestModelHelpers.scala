package example

import shapeless.{::, HNil}
import shapeless.Generic.Aux
import zio.random.Random
import zio.test.{Gen, Sized}
import zio.test.magnolia.DeriveGen
import model._

object TestModelHelpers {
  private implicit val genString: DeriveGen[String] = DeriveGen.instance(Gen.alphaNumericStringBounded(0, 10))

  private implicit val genNelEmployee: DeriveGen[Vector[Employee]] = {
    val g = DeriveGen[Employee]
    DeriveGen.instance(Gen.vectorOfBounded(1, 10)(g))
  }
  private implicit val genNelDepartment: DeriveGen[Vector[Department]] = {
    val g = DeriveGen[Department]
    DeriveGen.instance(Gen.vectorOfBounded(1, 10)(g))
  }
  implicit val genCompany: Gen[Random with Sized, Company] = DeriveGen[Company]

  def normalizeCompanies(companies: Vector[Company]): Vector[Company] = {
    companies.map { c =>
      c.copy(
        departments = c.departments.map { d =>
          d.copy(
            employees = d.employees.sortBy(_.id)
          )
        }.sortBy(_.id)
      )
    }.sortBy(_.id)
  }

  def companyToDbRows(
    c: Company,
  ): Vector[Tuple3[DbCompany, DbDepartment, DbEmployee]] = {
    import scala.collection.mutable

    val rows =
      mutable.ArrayBuffer.empty[Tuple3[DbCompany, DbDepartment, DbEmployee]]

    val dbCompany = DbCompany(c.id, c.name)
    c.departments.foreach { d =>
      val dbDepartment =
        DbDepartment(id = d.id, companyId = c.id, name = d.name)
      d.employees.foreach { e =>
        rows += Tuple3(
          dbCompany,
          dbDepartment,
          DbEmployee(id = e.id, departmentId = d.id, name = e.name),
        )
      }
    }

    rows.toVector
  }

  def dbRowsToHlist(row: (DbCompany, DbDepartment, DbEmployee)): DbCompany :: DbDepartment :: DbEmployee :: HNil = implicitly[Aux[
    Tuple3[DbCompany, DbDepartment, DbEmployee],
    DbCompany :: DbDepartment :: DbEmployee :: HNil
  ]].to(row)


}
