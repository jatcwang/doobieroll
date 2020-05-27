package orutest

import shapeless.{::, HNil}
import zio.random.Random
import zio.test.{Gen, Sized}
import zio.test.magnolia.DeriveGen
import model._

import scala.collection.mutable

object TestModelHelpers {
  private implicit val genString: DeriveGen[String] =
    DeriveGen.instance(Gen.alphaNumericStringBounded(0, 10))

  val genNelEmployee: DeriveGen[Vector[Employee]] = {
    val g = DeriveGen[Employee]
    DeriveGen.instance(Gen.vectorOfBounded(1, 10)(g))
  }

  val genNelDepartment: DeriveGen[Vector[Department]] = {
    implicit val emp = genNelEmployee
    val g = DeriveGen[Department]
    DeriveGen.instance(Gen.vectorOfBounded(1, 10)(g))
  }
  val genNelInvoice: DeriveGen[Vector[Invoice]] = {
    val g = DeriveGen[Invoice]
    DeriveGen.instance(Gen.vectorOfBounded(1, 10)(g))
  }

  val genNonEmptyCompany: Gen[Random with Sized, Company] = {
    implicit val dep = genNelDepartment
    DeriveGen[Company]
  }

  val genNonEmptyEnterprise: Gen[Random with Sized, Enterprise] = {
    implicit val dep = genNelDepartment
    implicit val inv = genNelInvoice
    DeriveGen[Enterprise]
  }

  val genCompany: Gen[Random with Sized, Company] = {
    DeriveGen[Company]
  }

  def normalizeDepartments(departments: Vector[Department]): Vector[Department] = {
    departments
      .map { d =>
        d.copy(
          employees = d.employees.sortBy(_.id),
        )
      }
      .sortBy(_.id)
  }

  def normalizeCompanies(companies: Vector[Company]): Vector[Company] = {
    companies
      .map { c =>
        c.copy(
          departments = normalizeDepartments(c.departments),
        )
      }
      .sortBy(_.id)
  }

  def normalizeEnterprise(enterprises: Vector[Enterprise]): Vector[Enterprise] =
    enterprises
      .map { c =>
        c.copy(
          departments = normalizeDepartments(c.departments),
          invoices = c.invoices.sortBy(_.id),
        )
      }
      .sortBy(_.id)

  def companyToDbRows(
    c: Company,
  ): Vector[Tuple3[DbCompany, DbDepartment, DbEmployee]] = {

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

  def enterpriseToDbRows(
    c: Enterprise,
  ): Vector[Tuple4[DbCompany, DbDepartment, DbEmployee, DbInvoice]] = {
    val rows = mutable.ArrayBuffer.empty[Tuple4[DbCompany, DbDepartment, DbEmployee, DbInvoice]]

    val dbCompany = DbCompany(id = c.id, name = c.name)

    assert(c.departments.nonEmpty)
    assert(c.departments.forall(_.employees.nonEmpty))
    assert(c.invoices.nonEmpty)

    // Note: In the database, inner-joining multiple child tables may result
    // in duplicate elements if size of child A and child B differ,
    // (Some elements from the smaller child list will be reused to 'pad-out'
    // the shorter child list)
    // We're trying to emulate the same behaviour here
    c.departments.foreach { d =>
      val dbDpmtAndEmp = {
        val dbDepartment = DbDepartment(id = d.id, companyId = c.id, name = d.name)
        d.employees.map(e =>
          dbDepartment -> DbEmployee(id = e.id, departmentId = d.id, name = e.name),
        )
      }

      dbDpmtAndEmp.zipAll(c.invoices, dbDpmtAndEmp.last, c.invoices.last).foreach {
        case ((dbDep, dbEmp), inv) =>
          rows += Tuple4(
            dbCompany,
            dbDep,
            dbEmp,
            DbInvoice(id = inv.id, amount = inv.amount),
          )
      }
    }

    rows.toVector
  }

  def companyToOptDbRows(
    c: Company,
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
                None,
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
                      name = em.name,
                    ),
                  ),
                )
              }
            }
          }
        }
    }

    rows.toVector
  }

  def wrapperToOptHList(
    wrapper: Wrapper,
  ): DbCompany :: Option[DbDepartment] :: Option[DbEmployee] :: HNil = {
    val optDep = if (wrapper.d.name.contains("1")) None else Some(wrapper.d)
    val optEmp = optDep.flatMap { _ =>
      if (wrapper.e.name.contains("1")) None else Some(wrapper.e)
    }

    wrapper.c :: optDep :: optEmp :: HNil
  }

}
