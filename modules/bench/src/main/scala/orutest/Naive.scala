package orutest

import orutest.model._
import shapeless._

object Naive {
  def assembleUngrouped(
    rows: Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil],
  ): Vector[Company] =
    rows
      .groupBy(_.head.id)
      .view
      .values
      .map { sameCompany =>
        val departmentsOfSameCompany = sameCompany
          .groupBy(_.tail.head.id)
          .values
          .map { sameDep =>
            val dep = sameDep.head.tail.head
            val ems = sameDep.map(_.tail.tail.head).distinct.map(Employee.fromDb)
            Department.fromDb(dep, ems)
          }
          .toVector
        val dbComp = sameCompany.head.head
        Company.fromDb(dbComp, departmentsOfSameCompany)
      }
      .toVector
}
