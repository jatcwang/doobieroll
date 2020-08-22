package doobierolltest.testutils

import zio.test.Assertion.equalTo
import zio.test.{assert, TestResult}
import doobie.util.fragment.Fragment

object FragmentAssertions {

  def assertFragmentSqlEqual(left: Fragment, right: String): TestResult =
    assert(left.query.sql)(equalTo(right))

  def assertStringEqual(left: String, right: String): TestResult = assert(left)(equalTo(right))

}
