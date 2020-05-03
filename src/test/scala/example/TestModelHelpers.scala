package example

import example.model.{Company, Department, Employee}
import zio.random.Random
import zio.test.{Gen, Sized}
import zio.test.magnolia.DeriveGen

object TestModelHelpers {
  implicit val genNelEmployee: DeriveGen[List[Employee]] = {
    val g = DeriveGen[Employee]
    DeriveGen.instance(Gen.listOfBounded(1, 10)(g))
  }
  implicit val genNelDepartment: DeriveGen[List[Department]] = {
    val g = DeriveGen[Department]
    DeriveGen.instance(Gen.listOfBounded(1, 10)(g))
  }
  implicit val genCompany: Gen[Random with Sized, Company] = DeriveGen[Company]

  def normalizeCompanies(companies: List[Company]): List[Company] = {
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


}
