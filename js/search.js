// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Assembler",
      "url": "/doobieroll/docs/assembler",
      "content": "Assembler Motivation - What problem does it solve? Assembler helps “assemble” relational data into their corresponding hierarchical representation. When querying a relational database, the query result are often not immediately usable for our business logic nor API response because our domain models are often hierarchical. To use an example, DoobieRoll assemblers can help you transform results of a SQL JOIN query like this: SELECT company_id, company_name, department_id, department_name, employee_id, employee_name FROM company INNER JOIN department ON department.company_id = company.id INNER JOIN employee ON employee.department_id = department.id company_id company_name department_id department_name employee_id employee_name comp_id_1 Comp 1 dep_id_1 Dep 1 emp_id_1 Alice comp_id_1 Comp 1 dep_id_1 Dep 1 emp_id_2 Bob comp_id_2 Comp 2 dep_id_2 Dep 2 emp_id_3 John comp_id_2 Comp 2 dep_id_2 Dep 3 emp_id_4 Nicole Into this: List( Company( id = \"comp_id_1\", name = \"Comp 1\", departments = List( Department( id = \"dep_id_1\", name = \"Dep1\", employees = List( Employee( id = \"emp_id_1\", name = \"Alice\", ), Employee( id = \"emp_id_2\", name = \"Bob\" ) ) ) ) ), id = \"comp_id_2\", name = \"Comp 2\", departments = List( Department( id = \"dep_id_2\", name = \"Dep2\", employees = List( Employee( id = \"emp_id_3\", name = \"John\", ) ) ), Department( id = \"dep_id_3\", name = \"Dep3\", employees = List( Employee( id = \"emp_id_4\", name = \"Nicole\", ) ) ) ) With doobie, typically the columns in the result set are grouped into logical groups which roughly maps to domain models. Using the example query above, the type of a doobie query will be: List[DbCompany :: DbDepartment :: DbEmployee :: HNil] (The type above is isomorphic to List[(DbCompany, DbDepartment, DbEmployee))]. Assembler works directly shapeless’s HList instead of tuples, though it’s easy to convert between them. I suggest reading about it to get a basic understand of what they represent before you continue) Each field in these DB model case classes map to a column in the query result: case class DbCompany( id: String, name: String ) case class DbDepartment( id: String, name: String ) case class DbEmployee( id: String, name: String ) To convert this to a List[Company], we often use .groupBys on the ids (in this case on company.id and department.id). We’d group employees belonging to the same department/company together and then convert each group into their domain model. There are however some downsides to this approach: Code is difficult to reuse - You’d have to write the same transformation for every different query you have The logic becomes more complex when The conversion from the database model (DbDepartment) to your domain model (Department) can fail. You object relationships are more complex, where a parent class can have multiple children types (which in turn can have their own children types) Assembler solves this problem by only requiring you to declare your data relationships, and it’ll take care of the assembling for you! Using Assembler Here are the main steps to use Assembler: Define the database-to-domain relationship - Define how to convert each database to their corresponding domain model. Create the Assembler using these definitions Assemble your query result - Feed the database query results into the assembler to get your domain objects Let’s see how it’s actually done with an example: A Town can have many Schools (one-to-many relationship) A School can have many Students (one-to-many relationship) import java.util.UUID case class Town( id: UUID, name: String, schools: Vector[School] ) case class School( id: UUID, name: String, students: Vector[Student] ) case class Student( id: UUID, name: String ) Let’s say we want to find towns, with the schools in those towns and the students in each of those schools. The code you’ll write with doobie to retrieve this information will probably look like import doobie.implicits._ import shapeless.{::, HNil} val townsQuery = fr\"\"\" SELECT town.id, town.name, school.id, school.name, student.id, student.name FROM town INNER JOIN school ON school.town_id = town.id INNER JOIN student ON student.school_id = school.id WHERE town.name LIKE '%ville' \"\"\".query[DbTown :: DbSchool :: DbStudent :: HNil] // Our database models case class DbTown( id: UUID, name: String ) case class DbSchool( id: UUID, name: String ) case class DbStudent( id: UUID, name: String ) and after running the query against some data you’ll have a result list. val queryResult: Vector[DbTown :: DbSchool :: DbStudent :: HNil] = // ...code to run the SQL query here omitted 1. Define the database-to-domain relationship When assembling domain models, they can be split into two types: Leaf: Types without any children (e.g. Student) Parent: Types with one or more children types, (e.g. Town has School as children, and School has Stdudent as children) Let’s define our relationships import cats.Id import doobieroll._ import doobieroll.implicits._ val townDef: ParentDef.Aux[Id, Town, DbTown, School :: HNil] = ParentDef.make( getId = (d: DbTown) =&gt; d.id, constructWithChild = (db: DbTown, schools: Vector[School]) =&gt; Town(db.id, db.name, schools) ) val schoolDef: ParentDef.Aux[Id, School, DbSchool, Student :: HNil] = ParentDef.make( getId = (d: DbSchool) =&gt; d.id, constructWithChild = (db: DbSchool, students: Vector[Student]) =&gt; School(db.id, db.name, students) ) val studentDef: LeafDef[Id, Student, DbStudent] = LeafDef.make( (db: DbStudent) =&gt; Student(db.id, db.name) ) 2. Create the Assembler With our individual definitions, we can now build an Assembler val assembler: ParentAssembler[cats.Id, Town, DbTown :: DbSchool :: DbStudent :: HNil] = townDef.toAssembler(schoolDef.toAssembler(studentDef.toAssembler)) The signature of the assembler tells us that it knows how to construct some Towns from a list of query result rows! (DbTown :: DbSchool :: DbStudent :: HNil). (Note how the DB type of assembler and the query result from above matches!) 3. Assemble your query result val towns: Vector[Town] = assembler.assemble(queryResult) // towns: Vector[Town] = Vector( // Town( // bca65379-1e46-40ca-bff5-5ca96c5fd183, // \"Smallville\", // Vector( // School( // c60e0a80-cfcb-4578-b00d-14b3fe68e734, // \"Smallville High\", // Vector( // Student(d948735b-1173-411c-ac00-e019ee89897a, \"Clark\"), // Student(7158132d-fe59-4ef1-8693-1476a0359aa7, \"Bob\") // ) // ) // ) // ), // Town( // ee58ad86-ce81-4650-b329-b701424fc23b, // \"Springfield\", // Vector( // School( // 51f02985-db87-4350-b557-6d9476e1367f, // \"Springfield Elementary School\", // Vector( // Student(4386ad3e-a30c-4179-938f-a0ead5bd9b4a, \"Lisa\"), // Student(997c0f4b-106d-4d80-82bd-02bae0a2c177, \"Bart\") // ) // ) // ) // ) // ) That’s it! 4. Usage with Doobie (and any other sources of data) The Assembler typeclass assemble can take any input data that resembles a list of HList. When querying with Doobie, you can query directly into an HList and then pipe the output straight through Assembler. import cats.effect.IO import doobie.{ConnectionIO, Transactor} import doobie.implicits._ // Your SQL query to perform // (...however you create your Doobie transactor) val transactor: Transactor[IO] = ??? val query: ConnectionIO[Vector[DbTown :: DbSchool :: DbStudent :: HNil]] = fr\"\"\" |SELECT school.id, school.name, teacher.id, teacher.name, student.name |FROM school |LEFT JOIN teacher WHERE teacher.school_id = school.id |LEFT JOIN student WHERE student.school_id = school.id \"\"\" .stripMargin .query[DbTown :: DbSchool :: DbStudent :: HNil] .to[Vector] val result: IO[Vector[Town]] = query.transact(transactor).map { queryResult: Vector[DbTown :: DbSchool :: DbStudent :: HNil] =&gt; assembler.assemble(queryResult) } Usage notes Row identity When joining sibling tables, SQL engines will often duplicate data from previous rows when there are no new data needed, thus Assembler needs to deduplicate (using equals/hashCode) when processing the data to avoid duplicates. So it is important to write SQL (and database types) such that your data has fields which allows proper uniqueness detection. Here’s an example: SELECT school.id, school.name, teacher.id, teacher.name, student.name FROM school LEFT JOIN teacher WHERE teacher.school_id = school.id LEFT JOIN student WHERE student.school_id = school.id Note this problematic query only returns the name of the student but not the ID. If the school has 2 teachers but only one student, we will get a result like this: school.id school.name teacher.id teacher.name student.name sch_id_1 School 1 tch_id_1 Einstein Alice sch_id_1 School 1 tch_id_2 Curie Alice The problem here is that Alice has been duplicated by the database engine to “fill in” the student columns when returning the second teacher row. We have no way of knowing whether there are one or two Alice in the school. The simplest way to solve this is to have some sort of identifier (e.g. UUID) for each entity type. In the above example, we should retrieve student.id column. (This doesn’t just apply when using Assembler - if you’re using SQL then this is something you need to think about) Parent types with multiple children Assembler supports having more than one child for parent entities. You can use make2, make3 etc depending on how many child entities there are. For example, teacher and student can both be child entities of a school. case class SchoolMoreInfo( id: UUID, name: String, teachers: Vector[Teacher], students: Vector[Student] ) val schoolMoreInfoDef: ParentDef.Aux[Id, SchoolMoreInfo, DbSchool, Teacher :: Student :: HNil] = ParentDef.make2( getId = (d: DbSchool) =&gt; d.id, constructWithChild = (db: DbSchool, teachers: Vector[Teacher], students: Vector[Student]) =&gt; SchoolMoreInfo( id = db.id, name = db.name, teachers = teachers, students = students ) ) val schoolMoreInfoAssembler: ParentAssembler[cats.Id, SchoolMoreInfo, DbSchool :: DbTeacher :: DbStudent :: HNil] = schoolMoreInfoDef.toAssembler(teacherDef.toAssembler, studentDef.toAssembler) Validated conversion to domain types It is common for domain types to have additional constraints representing some domain logic (e.g. name field cannot be empty). Assembler allows you to handle failures with any failure context as long as it has a cats.Applicative instance. (e.g. , Either, Validated Ior) To create a fallible definition use makeF instead of make val stricterStudentDef: LeafDef[Either[MyError, *], Student, DbStudent] = LeafDef.makeF( (db: DbStudent) =&gt; { if (db.name.isEmpty) { Left(MyError(\"Name is empty!\")) } else Right(Student(id = db.id, name = db.name)) } ) And our assemble result will be wrapped in our error import cats.implicits._ // Provides Monad instance for Either // Provides Monad instance for Either val schoolAssembler = schoolDef.forEither.toAssembler(stricterStudentDef.toAssembler) // schoolAssembler: ParentAssembler[Either[MyError, B], School, DbSchool :: DbStudent :: HNil] = doobieroll.syntax.ToAssemblerSyntax$$anon$2@2d3bcd27 schoolAssembler.assemble(queryResultsWithBadData) // res1: Vector[Either[MyError, School]] = Vector( // Right( // School( // 51f02985-db87-4350-b557-6d9476e1367f, // \"Springfield Elementary School\", // Vector( // Student(7158132d-fe59-4ef1-8693-1476a0359aa7, \"Bob\"), // Student(4386ad3e-a30c-4179-938f-a0ead5bd9b4a, \"Lisa\") // ) // ) // ), // Left(MyError(\"Name is empty!\")) // ) Some notes for the code snippet: schoolDef (from previous section) is an unfallible definition. So we need to “lift” its error context to Either[MyError, *] in order to combine it with the fallible stricterStudentDef which can fail The results are partial failures - Failures will only bubble up and fail all their parents. Other entities with valid data are still assembled and accessible. This is useful in many scenarios where you don’t want one corrupted entity to cause the whole result set to error."
    } ,    
    {
      "title": "Snippets",
      "url": "/doobieroll/docs/snippets",
      "content": "Snippets Snippets are functions that you can use to construct common SQL fragments. Import these function in scope with: import doobieroll.snippets._ Let’s define some TableColumns first and see how we can create common SQL snippets from them: import doobieroll.TableColumns case class DbCompany( id: UUID, name: String, phoneNumber: String, ) object DbCompany { val columns: TableColumns[DbCompany] = TableColumns.deriveSnakeCaseTableColumns(tableName = \"company\") } case class DbEmployee( id: UUID, companyId: UUID, firstName: String, lastName: String, ) object DbEmployee { val columns: TableColumns[DbEmployee] = TableColumns.deriveSnakeCaseTableColumns(tableName = \"employee\") } Select columns from single table - selectColumnsFrom selectColumnsFrom(DbCompany.columns) // res0: doobie.util.fragment.Fragment = Fragment(\"SELECT id,name,phone_number FROM company \") Select columns from multiple tables - selectColumns selectColumns( DbCompany.columns.prefixedF(\"c\"), DbEmployee.columns.prefixedF(\"e\") ) ++ fr\"FROM company c LEFT JOIN employee e ON company.id = employee.company_id\" // res1: doobie.util.fragment.Fragment = Fragment(\"SELECT c.id,c.name,c.phone_number ,e.id,e.company_id,e.first_name,e.last_name FROM company c LEFT JOIN employee e ON company.id = employee.company_id \")"
    } ,    
    {
      "title": "TableColumns",
      "url": "/doobieroll/docs/tablecolumns",
      "content": "TableColumns What problem does it solve? In Doobie, we write SQL directly. This results in quite a lot of repetition when we’re specifying columns. Take this postgres upsert query, for example: import doobie.Update import doobie.implicits._ val q: Update[DbCompany] = Update[DbCompany](\"\"\" INSERT INTO company ( id, name, phone_number, tax_number, address ) VALUES ( ?, ?, ?, ?, ? ) ON CONFLICT (id) DO UPDATE SET id = EXCLUDED.id, name = EXCLUDED.name, phone_number = EXCLUDED.phone_number, tax_number = EXCLUDED.tax_number, address = EXCLUDED.address \"\"\") case class DbCompany( id: UUID, name: String, phoneNumber: String, taxNumber: String, address: String ) There is a lot of repetition here! Using TableColumns Let’s use TableColumns to “DRY” up the example above: import doobieroll.TableColumns object DbCompany { val columns: TableColumns[DbCompany] = TableColumns.deriveSnakeCaseTableColumns(tableName = \"company\") } val qq: Fragment = fr\"\"\" |INSERT INTO company |${DbCompany.columns.listWithParenF} |VALUES |${DbCompany.columns.parameterizedWithParenF} |ON CONFLICT (id) DO UPDATE SET |${updateAllNonKeyColumns(DbCompany.columns)} \"\"\".stripMargin // qq: Fragment = Fragment(\" // INSERT INTO company // (id,name,phone_number,tax_number,address) // VALUES // (?,?,?,?,?) // ON CONFLICT (id) DO UPDATE SET // name = EXCLUDED.name, phone_number = EXCLUDED.phone_number, tax_number = EXCLUDED.tax_number, address = EXCLUDED.address // \") // You can define your own functions to work with the list of fields! private def updateAllNonKeyColumns(tableColumns: TableColumns[_]): Fragment = Fragment.const( // Assume first field is the primary key, so we don't need to set it tableColumns.allColumns.toList.drop(1).map(c =&gt; s\"$c = EXCLUDED.$c\").mkString(\", \") ) Other than having less boilerplate, the main benefit of using TableColumns is consistency. Since field names and order are consistent across all use sites, we can avoid out of order fields causing bugs."
    } ,    
    {
      "title": "FAQ",
      "url": "/doobieroll/docs/faq",
      "content": "Frequently Asked Questions What problem is DoobieRoll trying to solve? This library aims to reduce the boring, boilerplatey things when working with Doobie / SQL databases. Similar to Doobie’s philosophy, it doesn’t try to hide the underlying SQL from you. For example snippets functions maps directly to common SQL fragments - their purpose is to reduce boilerplate and avoid typos. I want to use Assembler but I have a list of tuples instead of HLists! If you are using Doobie, you can query directly into HLists (see Assembler’s “Usage with Doobie” section). If that’s not possible, shapeless can convert tuples to HLists: import shapeless.syntax.std.tuple._ import shapeless._ val listOfTuples: List[(Int, String)] = List((1, \"one\"), (2, \"two\")) // listOfTuples: List[(Int, String)] = List((1, \"one\"), (2, \"two\")) val listOfHList: List[Int :: String :: HNil] = listOfTuples.map(_.productElements) // listOfHList: List[Int :: String :: HNil] = List( // 1 :: \"one\" :: HNil, // 2 :: \"two\" :: HNil // )"
    } ,    
    {
      "title": "Home",
      "url": "/doobieroll/",
      "content": "DoobieRoll is a collection of utilities to make working with Doobie / SQL even easier. TableColumns - Ensure fields in your SQL are consistently named and ordered. Assembler - Assemble SQL query results into hierarchical domain models. Assembler does not depend on Doobie, so check it out even if you don’t use Doobie! Installation // SBT \"com.github.jatcwang\" %% \"doobieroll\" % \"0.1.7\" // Mill ivy\"com.github.jatcwang::doobieroll:0.1.7\""
    } ,        
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
