package oru

import orutest.model._
import cats.implicits._
import shapeless._

object Naive {
  def assembleUngrouped(
    rows: Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil]
  ): Vector[Either[EE, Company]] =
    rows
      .groupBy(_.head.id)
      .view
      .values
      .map { sameCompany =>
        sameCompany
          .groupBy(_.tail.head.id)
          .values
          .map { sameDep =>
            val dep = sameDep.head.tail.head
            val ems = sameDep.map(_.tail.tail.head).distinct.map(Employee.fromDb).sequence
            ems.flatMap { es =>
              Department.fromDb(dep, es)
            }
          }
          .toVector
          .sequence
          .flatMap { departmentsOfSameCompany =>
            val dbComp = sameCompany.head.head
            Company.fromDb(dbComp, departmentsOfSameCompany)
          }
      }
      .toVector
}
