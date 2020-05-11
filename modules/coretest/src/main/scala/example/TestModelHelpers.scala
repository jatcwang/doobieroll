package example

import shapeless.{::, HNil}
import shapeless.Generic.Aux
import zio.random.Random
import zio.test.{Gen, Sized}
import zio.test.magnolia.DeriveGen
import model._

import scala.collection.mutable

object TestModelHelpers {
  implicit private val genString: DeriveGen[String] =
    DeriveGen.instance(Gen.alphaNumericStringBounded(0, 10))

  val genNonEmptyCompany: Gen[Random with Sized, Company] = {
    implicit val genNelEmployee: DeriveGen[Vector[Employee]] = {
      val g = DeriveGen[Employee]
      DeriveGen.instance(Gen.vectorOfBounded(1, 10)(g))
    }
    implicit val genNelDepartment: DeriveGen[Vector[Department]] = {
      val g = DeriveGen[Department]
      DeriveGen.instance(Gen.vectorOfBounded(1, 10)(g))
    }
    DeriveGen[Company]
  }

  val genCompany: Gen[Random with Sized, Company] = {
    DeriveGen[Company]
  }

  def normalizeCompanies(companies: Vector[Company]): Vector[Company] = {
    companies
      .map { c =>
        c.copy(
          departments = c.departments
            .map { d =>
              d.copy(
                employees = d.employees.sortBy(_.id)
              )
            }
            .sortBy(_.id)
        )
      }
      .sortBy(_.id)
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

  def companyToOptDbRows(
    c: Company
  ): Vector[Tuple3[DbCompany, Option[DbDepartment], Option[DbEmployee]]] = {
    val rows =
      mutable.ArrayBuffer.empty[Tuple3[DbCompany, Option[DbDepartment], Option[DbEmployee]]]

    val dbCompany = DbCompany(c.id, c.name)

    c.departments match {
      case Vector() => rows += Tuple3(dbCompany, None, None)
      case nelDepartments =>
        nelDepartments.foreach { d =>
          val dbDep = DbDepartment(id = d.id, companyId = c.id, name = d.name)
          d.employees match {
            case Vector() =>
              rows += Tuple3(
                dbCompany,
                Some(dbDep),
                None
              )
            case nelEmployee => {
              nelEmployee.foreach { em =>
                rows += Tuple3(
                  dbCompany,
                  Some(dbDep),
                  Some(
                    DbEmployee(
                      id = em.id,
                      departmentId = d.id,
                      name = em.name
                    )
                  )
                )
              }
            }
          }
        }
    }

    rows.toVector
  }

  def dbRowToHlist(
    row: (DbCompany, DbDepartment, DbEmployee)
  ): DbCompany :: DbDepartment :: DbEmployee :: HNil =
    implicitly[Aux[
      Tuple3[DbCompany, DbDepartment, DbEmployee],
      DbCompany :: DbDepartment :: DbEmployee :: HNil
    ]].to(row)

  def dbRowToOptHlist(
    row: (DbCompany, Option[DbDepartment], Option[DbEmployee])
  ): DbCompany :: Option[DbDepartment] :: Option[DbEmployee] :: HNil =
    implicitly[Aux[
      Tuple3[DbCompany, Option[DbDepartment], Option[DbEmployee]],
      DbCompany :: Option[DbDepartment] :: Option[DbEmployee] :: HNil
    ]].to(row)

  def wrapperToOptHList(
    wrapper: Wrapper
  ): DbCompany :: Option[DbDepartment] :: Option[DbEmployee] :: HNil = {
    val optDep = if (wrapper.d.name.contains("1")) None else Some(wrapper.d)
    val optEmp = optDep.flatMap { _ =>
      if (wrapper.e.name.contains("1")) None else Some(wrapper.e)
    }

    wrapper.c :: optDep :: optEmp :: HNil
  }

}
