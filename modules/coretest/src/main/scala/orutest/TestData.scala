package orutest

import java.util.UUID

import model._

object TestData {

  val n1 = UUID.fromString("00000000-0000-0100-0000-000000000000")
  val n2 = UUID.fromString("00000000-0000-0200-0000-000000000000")
  val n3 = UUID.fromString("00000000-0000-0300-0000-000000000000")
  val c1 = UUID.fromString("00000000-0000-0001-0000-000000000000")
  val c2 = UUID.fromString("00000000-0000-0002-0000-000000000000")
  val c3 = UUID.fromString("00000000-0000-0003-0000-000000000000")
  val d1 = UUID.fromString("00000000-0000-0000-0001-000000000000")
  val d2 = UUID.fromString("00000000-0000-0000-0002-000000000000")
  val d3 = UUID.fromString("00000000-0000-0000-0003-000000000000")
  val e1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val e2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
  val e3 = UUID.fromString("00000000-0000-0000-0000-000000000003")
  val e4 = UUID.fromString("00000000-0000-0000-0000-000000000004")
  val e5 = UUID.fromString("00000000-0000-0000-0000-000000000005")
  val i1 = UUID.fromString("00000000-0000-0000-0000-000000000100")
  val i2 = UUID.fromString("00000000-0000-0000-0000-000000000200")
  val i3 = UUID.fromString("00000000-0000-0000-0000-000000000300")
  val i4 = UUID.fromString("00000000-0000-0000-0000-000000000400")

  val c1db = DbCompany(c1, "Comp1")
  val c2db = DbCompany(c2, "Comp2")

  val d1db = DbDepartment(d1, c1, "Dep1")
  val d2db = DbDepartment(d2, c2, "Dep2")

  val e1db = DbEmployee(e1, d1, "Emp1")
  val e2db = DbEmployee(e2, d1, "Emp2")
  val e3db = DbEmployee(e3, d2, "Emp3")

  private val dep1WithChildren: Department = model.Department(
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
  )

  private val dep2WithChildren: Department = model.Department(
    d2,
    "Dep2",
    employees = Vector(
      model.Employee(
        e3,
        "Emp3",
      ),
    ),
  )

  private val dep3WithChildren: Department = Department(
    d3,
    name = "Dep3",
    employees = Vector(
      Employee(
        e4,
        name = "Emp4"
      ),
      Employee(
        e5,
        name = "Emp5"
      )
    )
  )

  val expectedCompanies = Vector(
    Company(
      c1,
      "Comp1",
      departments = Vector(dep1WithChildren),
    ),
    Company(
      c2,
      "Comp2",
      departments = Vector(dep2WithChildren),
    ),
  )

  val expectedEnterprise = Vector(
    Enterprise(
      n1,
      "Ent1",
      // More invoice than departments
      departments = Vector(dep1WithChildren),
      invoices = Vector(
        Invoice(i1, 10),
        Invoice(i2, 20)
      )
    ),
    // More departments than invoices
    Enterprise(
      n2,
      "Ent2",
      departments = Vector(dep2WithChildren, dep3WithChildren),
      invoices = Vector(
        Invoice(i3, 30)
      )
    )
  )

  val expectedCompaniesWithSomeEmptyChildren = Vector(
    Company(
      c1,
      "Comp1",
      departments = Vector(
        Department(
          d1,
          "Dep1",
          employees = Vector(
            Employee(
              e1,
              "Emp1",
            ),
            Employee(
              e2,
              "Emp2",
            ),
          ),
        ),
      ),
    ),
    Company(
      c2,
      "Comp2",
      departments = Vector(),
    ),
    Company(
      c3,
      "Comp3",
      departments = Vector(
        Department(
          d2,
          name = "Dep2",
          employees = Vector()
        ),
        dep3WithChildren
      )
    )
  )

}
