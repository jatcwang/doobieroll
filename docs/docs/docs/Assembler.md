---
layout: docs
title:  "Assembler"
permalink: docs/assembler
---

# Assembler

## Motivation

Assembler helps "assemble" relational data into their corresponding hierarchical representation.

Here by **relational** we mean the data model you typically get out of a relational database (e.g. SQL query result).
However, In business logic ("domain") we typically use **Hierarchical** data models, because it's often the better,
more intuitive way to work with the data. (e.g. "A company can have many departments, and each department can have many employees")

To use an example, DoobieRoll assemblers can help you transform results of a SQL JOIN query like this:

```
SELECT company_id, company_name, department_id, department_name, employee_id, employee_name
FROM company
LEFT JOIN department ON department.company_id = company.id
LEFT JOIN employee ON employee.department_id = department.id
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

Typically with doobie, the columns in the result set are grouped into logical groups roughly mapping to domain entities. 
Using the example query above, the result type of a doobie query will probably look like this:

```
List[(DbCompany, DbDepartment, DbEmployee)]
```

We will call types like `DbCompany`, `DbDepartment` and `DbEmployee` **Column Group**s. A Column Group's case class fields
map to a column. They are defined as:

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

If we want to convert this to a `List[Company]`, what you'd normally do is to use `.groupBy`s 
on `company.id` and `department.id` - group employees belonging to the same department/company together and then
convert each group into their hierarchical model.

In most cases this works, but there are some downsides:

- Difficult to reuse the logic - You'd have to write the same transformation for every different query you have
- The logic becomes more complex when
  - The conversion from the column group (`DbDepartment`) to your domain entity (`Department`) can fail.
  - You object relationships are more complex, where a parent class can have multiple children types 
    (which in turn can have their own children types)

Assembler solves this problem by only requiring you to declare your data relationships, and it'll take care of assembling
your object for you!

# Using Assembler

There are the few steps to using Assembler:

- Define definitions for each individual domain and and how to convert them from **Column Group** types
- Create the Assembler from these definitions
- Feed the assembler with your database query results and get the hierarchical object!
