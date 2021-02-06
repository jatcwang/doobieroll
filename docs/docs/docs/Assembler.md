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
List[DbCompany :: DbDepartment :: DbEmployee :: HNil]
```

(The type above is isomorphic to `List[(DbCompany, DbDepartment, DbEmployee))]`. 
Assembler works directly shapeless's HList instead of tuples, though it's easy to convert between them. 
I suggest [reading about it](https://books.underscore.io/shapeless-guide/shapeless-guide.html#generic-product-encodings) 
to get a basic understand of what they represent before you continue)

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
import shapeless.syntax.std.tuple._
val town1 = DbTown(
UUID.fromString("bca65379-1e46-40ca-bff5-5ca96c5fd183"),
"Smallville"
)

val town2 = DbTown(
UUID.fromString("ee58ad86-ce81-4650-b329-b701424fc23b"),
"Springfield"
)

val school1 = DbSchool(
UUID.fromString("c60e0a80-cfcb-4578-b00d-14b3fe68e734"),
"Smallville High"
)

val school2 = DbSchool(
UUID.fromString("51f02985-db87-4350-b557-6d9476e1367f"),
"Springfield Elementary School"
)

val student1 = DbStudent(
UUID.fromString("d948735b-1173-411c-ac00-e019ee89897a"),
"Clark"
)

val student2 = DbStudent(
UUID.fromString("7158132d-fe59-4ef1-8693-1476a0359aa7"),
"Bob"
)

val student3 = DbStudent(
UUID.fromString("4386ad3e-a30c-4179-938f-a0ead5bd9b4a"),
"Lisa"
)

val student4 = DbStudent(
UUID.fromString("997c0f4b-106d-4d80-82bd-02bae0a2c177"),
"Bart"
)

val queryResult: Vector[DbTown :: DbSchool :: DbStudent :: HNil] = {
  Vector(
    Tuple3(town1, school1, student1),
    Tuple3(town1, school1, student2),
    Tuple3(town2, school2, student3),
    Tuple3(town2, school2, student4),
  ).map(_.productElements)
}
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

```scala mdoc
val towns: Vector[Town] = assembler.assemble(queryResult)
```

That's it!

### 4. Usage with Doobie (and any other sources of data)

The Assembler typeclass `assemble` can take any input data that resembles a list of HList.

When querying with Doobie, you can query directly into an HList and then pipe the output 
straight through Assembler.

```scala mdoc:compile-only
import cats.effect.IO
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._

// Your SQL query to perform
// (...however you create your Doobie transactor)
val transactor: Transactor[IO] = ??? 

val query: ConnectionIO[Vector[DbTown :: DbSchool :: DbStudent :: HNil]] = 
  fr"""
    |SELECT school.id, school.name, teacher.id, teacher.name, student.name
    |FROM school
    |LEFT JOIN teacher WHERE teacher.school_id = school.id
    |LEFT JOIN student WHERE student.school_id = school.id
  """
    .stripMargin
    .query[DbTown :: DbSchool :: DbStudent :: HNil]
    .to[Vector]
    
val result: IO[Vector[Town]] = query.transact(transactor).map { queryResult: Vector[DbTown :: DbSchool :: DbStudent :: HNil] =>
  assembler.assemble(queryResult)
}
```

# Usage notes

### Row identity
When joining sibling tables, SQL engines will often duplicate data from previous rows when 
there are no new data needed, thus `Assembler` needs to deduplicate (using `equals`/`hashCode`)
when processing the data to avoid duplicates.

So it is important to write SQL (and database types) such that your data has fields which
allows proper uniqueness detection.

Here's an example:

```
SELECT school.id, school.name, teacher.id, teacher.name, student.name
FROM school
LEFT JOIN teacher WHERE teacher.school_id = school.id
LEFT JOIN student WHERE student.school_id = school.id
```

Note this problematic query only returns the name of the student but not the ID.
If the school has 2 teachers but only one student, we will get a result like this:

| school.id | school.name | teacher.id | teacher.name | student.name |
| --        | --          | --         | --           | --           |
| sch_id_1  | School 1    | tch_id_1   | Einstein     | Alice        |
| sch_id_1  | School 1    | tch_id_2   | Curie        | Alice        |

The problem here is that **Alice** has been duplicated by the database engine
to "fill in" the student columns when returning the second `teacher` row.
We have no way of knowing whether there are one or two **Alice** in the school.

The simplest way to solve this is to have some sort of identifier (e.g. UUID)
for each entity type. In the above example, we should retrieve `student.id` column.

(This doesn't just apply when using `Assembler` - if you're using SQL then
this is something you need to think about) 

### Parent types with multiple children

Assembler supports having more than one child for parent entities. You can use `make2`, `make3` etc
depending on how many child entities there are.

For example, `teacher` and `student` can both be child entities of a `school`.

```scala mdoc:invisible
case class Teacher(
  id: UUID,
  name: String
)

case class DbTeacher(
  id: UUID,
  name: String
)

val teacherDef: LeafDef[Id, Teacher, DbTeacher] = LeafDef.make((db: DbTeacher) => Teacher(db.id, db.name))
```

```scala mdoc:silent
case class SchoolMoreInfo(
  id: UUID,
  name: String,
  teachers: Vector[Teacher],
  students: Vector[Student]
)

val schoolMoreInfoDef: ParentDef.Aux[Id, SchoolMoreInfo, DbSchool, Teacher :: Student :: HNil] = ParentDef.make2(
  getId = (d: DbSchool) => d.id,
  constructWithChild = (db: DbSchool, teachers: Vector[Teacher], students: Vector[Student]) => SchoolMoreInfo(
    id = db.id,
    name = db.name,
    teachers = teachers,
    students = students
  )
)

val schoolMoreInfoAssembler: ParentAssembler[cats.Id, SchoolMoreInfo, DbSchool :: DbTeacher :: DbStudent :: HNil] = 
  schoolMoreInfoDef.toAssembler(teacherDef.toAssembler, studentDef.toAssembler)
```

### Validated conversion to domain types

It is common for domain types to have additional constraints representing some domain logic 
(e.g. `name` field cannot be empty). `Assembler` allows you to handle failures with any failure context
as long as it has a `cats.Applicative` instance. (e.g. , `Either`, `Validated` `Ior`)

To create a fallible definition use `makeF` instead of `make`

```scala mdoc:invisible
case class MyError(msg: String)
```

```scala mdoc:silent
val stricterStudentDef: LeafDef[Either[MyError, *], Student, DbStudent] = LeafDef.makeF(
  (db: DbStudent) => {
    if (db.name.isEmpty) {
      Left(MyError("Name is empty!"))
    }
    else Right(Student(id = db.id, name = db.name))
  }
)
```

And our assemble result will be wrapped in our error

```scala mdoc:invisible
val queryResultsWithBadData: Vector[DbSchool :: DbStudent :: HNil] = Vector(
  Tuple2(school2, student2),
  Tuple2(school2, student3),
  Tuple2(school1, student1.copy(name = "")),
).map(_.productElements)
```

```scala mdoc
import cats.implicits._ // Provides Monad instance for Either
val schoolAssembler = schoolDef.forEither.toAssembler(stricterStudentDef.toAssembler)

schoolAssembler.assemble(queryResultsWithBadData)
```

Some notes for the code snippet:

* `schoolDef` (from previous section) is an unfallible definition. 
  So we need to "lift" its error context to `Either[MyError, *]` in order to combine it with
  the fallible `stricterStudentDef` which can fail
* The results are partial failures - Failures will only bubble up and fail all their parents.
  Other entities with valid data are still assembled and accessible. This is useful in many scenarios
  where you don't want one corrupted entity to cause the whole result set to error.
