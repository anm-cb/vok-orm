package com.github.vok.framework.sql2o

import com.github.mvysny.dynatest.DynaTest
import kotlin.test.expect

class FiltersTest : DynaTest({

    fun unmangleParameterNames(sql: String, params: Map<String, Any?>): String {
        var sql = sql
        params.entries.forEach { (key, value) -> sql = sql.replace(":$key", ":$value") }
        return sql
    }
    fun sql(block: SqlWhereBuilder<Person>.()->Filter<Person>): String {
        val filter: Filter<Person> = block(SqlWhereBuilder())
        return unmangleParameterNames(filter.toSQL92(), filter.getSQL92Parameters())
    }

    test("ToSQL92") {
        expect("age = :25") { sql { com.github.vok.framework.sql2o.Person::age eq 25 } }
        expect("(age >= :25 and age <= :50)") { sql { com.github.vok.framework.sql2o.Person::age between 25..50 } }
    }

    test("LikeFilterInMemory") {
        expect(false) { LikeFilter<Person>("name", "A").test(Person(name = "kari", age = 35)) }
        expect(true) { LikeFilter<Person>("name", " a ").test(Person(name = "kari", age = 35)) }
        expect(true) { ILikeFilter<Person>("name", "A").test(Person(name = "kari", age = 35)) }
    }

    test("Equals") {
        expect(ILikeFilter("name", "A")) { ILikeFilter<Person>("name", "A") }
        expect(false) { ILikeFilter<Person>("name", "A").equals(LikeFilter<Person>("name", "A")) }
    }
})
