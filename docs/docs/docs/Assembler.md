---
layout: docs
title:  "Assembler"
permalink: docs/assembler
---

# Assembler

## Motivation - What problem does it solve?

Assembler helps "assemble" relational data into their corresponding hierarchical representation.

When querying a relational database, the query result are often not immediately usable for our business logic nor API response
because our domain models are often hierarchical.

To use an example, DoobieRoll assemblers can help you transform results of a SQL JOIN query like this:

```
SELECT company_id, company_name, department_id, department_name, employee_id, employee_name
FROM company
INNER JOIN department ON department.company_id = company.id
INNER JOIN employee ON employee.department_id = department.id
```

| company_id | company_name | department_id | department_name | employee_id | employee_name |
| --         | --           | --            | --              | --          | --            |
| comp_id_1  | Comp 1       | dep_id_1      | Dep 1           | emp_id_1    | Alice         |
| comp_id_1  | Comp 1       | dep_id_1      | Dep 1           | emp_id_2    | Bob           |
| comp_id_2  | Comp 2       | dep_id_2      | Dep 2           | emp_id_3    | John          |
| comp_id_2  | Comp 2       | dep_id_2      | Dep 3           | emp_id_4    | Nicole        |

Into this:

```scala
  List(
    Company(
      id = "comp_id_1",
      name = "Comp 1",
      departments = List(
        Department(
          id = "dep_id_1",
          name = "Dep1",
          employees = List(
            Employee(
              id = "emp_id_1",
              name = "Alice",
            ),
            Employee(
              id = "emp_id_2",
              name = "Bob"
            )
          )
        )
      )
    ),
    id = "comp_id_2",
    name = "Comp 2",
    departments = List(
      Department(
        id = "dep_id_2",
        name = "Dep2",
        employees = List(
          Employee(
            id = "emp_id_3",
            name = "John",
          )
        )
      ),
      Department(
        id = "dep_id_3",
        name = "Dep3",
        employees = List(
          Employee(
            id = "emp_id_4",
            name = "Nicole",
          )
        )
      )
    )
```

With doobie, typically the columns in the result set are grouped into logical groups which roughly maps to domain models. 
Using the example query above, the type of a doobie query will be:

```
List[(DbCompany, DbDepartment, DbEmployee)]
```

Each field in these DB model case classes map to a column in the query result:

```
case class DbCompany(
  id: String,
  name: String
)

case class DbDepartment(
  id: String,
  name: String
)

case class DbEmployee(
  id: String,
  name: String
)
```

To convert this to a `List[Company]`, we often use `.groupBy`s on the ids (in this case on `company.id` and `department.id`).
We'd group employees belonging to the same department/company together and then convert each group into their domain model.

There are however some downsides to this approach:

- Code is difficult to reuse - You'd have to write the same transformation for every different query you have
- The logic becomes more complex when
  - The conversion from the database model (`DbDepartment`) to your domain model (`Department`) can fail.
  - You object relationships are more complex, where a parent class can have multiple children types 
    (which in turn can have their own children types)

Assembler solves this problem by only requiring you to declare your data relationships, and it'll take care of 
the assembling for you!

# Using Assembler

Here are the main steps to use Assembler:

1. **Define the database-to-domain relationship** - Define how to convert each database to their corresponding domain model.
1. **Create the Assembler** using these definitions
1. **Assemble your query result** - Feed the database query results into the assembler to get your domain objects

Let's see how it's actually done with an example:

- A **Town** can have many **Schools** (one-to-many relationship)
- A **School** can have many **Students** (one-to-many relationship)

```scala mdoc:silent
import java.util.UUID

case class Town(
  id: UUID,
  name: String,
  schools: Vector[School]
)

case class School(
  id: UUID,
  name: String,
  students: Vector[Student]
)

case class Student(
  id: UUID,
  name: String
)
```

Let's say we want to find towns, with the schools in those towns and the students in each of those schools.
The code you'll write with doobie to retrieve this information will probably look like 

```scala mdoc:invisible
import doobie.postgres.implicits._
```

```scala mdoc:silent
import doobie.implicits._ 
import shapeless.{::, HNil}

val townsQuery = fr"""
SELECT town.id, town.name, school.id, school.name, student.id, student.name 
FROM town
INNER JOIN school ON school.town_id = town.id
INNER JOIN student ON student.school_id = school.id
WHERE town.name LIKE '%ville'
""".query[DbTown :: DbSchool :: DbStudent :: HNil]

// Our database models

case class DbTown(
  id: UUID,
  name: String
)

case class DbSchool(
  id: UUID,
  name: String
)

case class DbStudent(
  id: UUID,
  name: String
)
```

and after running the query against some data you'll have a result list.

```scala mdoc:invisible
val queryResult = Vector.empty[DbTown :: DbSchool :: DbStudent :: HNil]
```
```scala
val queryResult: Vector[DbTown :: DbSchool :: DbStudent :: HNil] = // ...code to run the SQL query here omitted
```

### 1. Define the database-to-domain relationship

When assembling domain models, they can be split into two types:

- **Leaf**: Types without any children (e.g. `Student`)
- **Parent**: Types with one or more children types, (e.g. `Town` has `School` as children, and `School` has `Stdudent` as children)

Let's define our relationships
```scala mdoc:silent
import cats.Id
import doobieroll._
import doobieroll.implicits._

val townDef: ParentDef.Aux[Id, Town, DbTown, School :: HNil] = ParentDef.make(
  getId = (d: DbTown) => d.id,
  constructWithChild = (db: DbTown, schools: Vector[School]) => Town(db.id, db.name, schools)
)

val schoolDef: ParentDef.Aux[Id, School, DbSchool, Student :: HNil] = ParentDef.make(
  getId = (d: DbSchool) => d.id,
  constructWithChild = (db: DbSchool, students: Vector[Student]) => School(db.id, db.name, students)
)

val studentDef: LeafDef[Id, Student, DbStudent] = LeafDef.make(
  (db: DbStudent) => Student(db.id, db.name)
)
```

### 2. Create the Assembler 

With our individual definitions, we can now build an **Assembler**

```scala mdoc:silent
val assembler: ParentAssembler[cats.Id, Town, DbTown :: DbSchool :: DbStudent :: HNil] = 
  townDef.toAssembler(schoolDef.toAssembler(studentDef.toAssembler))
```

The signature of the assembler tells us that it knows how to construct some `Town`s from a 
list of query result rows! (`DbTown :: DbSchool :: DbStudent :: HNil`).
(Note how the DB type of assembler and the query result from above matches!)

### 3. Assemble your query result

```
val towns: Vector[Town] = assemble.assemble(queryResult)
```

That's it!
