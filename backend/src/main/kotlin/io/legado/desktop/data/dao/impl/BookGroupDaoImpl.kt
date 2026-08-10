package io.legado.desktop.data.dao.impl

import io.legado.desktop.constant.BookType
import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.BookGroupDao
import io.legado.desktop.data.entities.BookGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** BookGroupDao SQLite 实现（SQL 对照 Legado Room @Query；LiveData → List 桌面版） */
class BookGroupDaoImpl : BookGroupDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun getByID(id: Long): BookGroup? = withLock {
        db.queryOne("select * from book_groups where groupId = ?", listOf(id), BookGroup::class.java)
    }

    override fun getByName(groupName: String): BookGroup? = withLock {
        db.queryOne("select * from book_groups where groupName = ?", listOf(groupName), BookGroup::class.java)
    }

    override fun flowAll(): Flow<List<BookGroup>> = flow {
        emit(withLock { db.queryList("SELECT * FROM book_groups ORDER BY `order`", emptyList(), BookGroup::class.java) })
    }

    override val show: List<BookGroup> get() = withLock {
        db.queryList(showSql(), emptyList(), BookGroup::class.java)
    }

    override fun flowSelect(): Flow<List<BookGroup>> = flow {
        emit(withLock {
            db.queryList("SELECT * FROM book_groups where groupId >= 0 ORDER BY `order`", emptyList(), BookGroup::class.java)
        })
    }

    override val idsSum: Long get() = withLock {
        db.queryValue("SELECT sum(groupId) FROM book_groups where groupId >= 0", emptyList(), Long::class.java) ?: 0L
    }

    override val maxOrder: Int get() = withLock {
        db.queryValue("SELECT MAX(`order`) FROM book_groups where groupId >= 0", emptyList(), Int::class.java) ?: 0
    }

    override val all: List<BookGroup> get() = withLock {
        db.queryList("SELECT * FROM book_groups ORDER BY `order`", emptyList(), BookGroup::class.java)
    }

    override val canAddGroup: Boolean get() = withLock {
        db.queryValue("select count(*) < 63 from book_groups where groupId > 0", emptyList(), Int::class.java) == 1
    }

    override fun enableGroup(groupId: Long) {
        withLock {
            db.execute("update book_groups set show = 1 where groupId = ?", listOf(groupId))
        }
    }

    override fun getGroupNames(id: Long): List<String> = withLock {
        db.queryList("select groupName from book_groups where groupId > 0 and (groupId & ?) > 0", listOf(id), String::class.java)
    }

    override fun insert(vararg bookGroup: BookGroup) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO book_groups (groupId, groupName, cover, `order`, enableRefresh, " +
                    "show, bookSort, onlyUpdateRead) VALUES (?,?,?,?,?,?,?,?)",
                bookGroup.flatMap { g ->
                    listOf(
                        g.groupId, g.groupName, g.cover, g.order, g.enableRefresh,
                        g.show, g.bookSort, g.onlyUpdateRead
                    )
                }
            )
        }
    }

    override fun update(vararg bookGroup: BookGroup) {
        withLock {
            db.execute(
                "UPDATE book_groups SET groupName=?, cover=?, `order`=?, enableRefresh=?, show=?, " +
                    "bookSort=?, onlyUpdateRead=? WHERE groupId=?",
                bookGroup.flatMap { g ->
                    listOf(
                        g.groupName, g.cover, g.order, g.enableRefresh, g.show,
                        g.bookSort, g.onlyUpdateRead, g.groupId
                    )
                }
            )
        }
    }

    override fun delete(vararg bookGroup: BookGroup) {
        withLock {
            db.execute("DELETE FROM book_groups WHERE groupId = ?", bookGroup.map { it.groupId })
        }
    }

    // ---- 内部 SQL（对照 Legado BookGroupDao.show @Query，LiveData 语义改为一次性查询） ----

    private fun showSql(): String = """
        with const as (SELECT sum(groupId) sumGroupId FROM book_groups where groupId > 0)
        SELECT book_groups.* FROM book_groups join const 
        where show > 0 
        and (
            (groupId >= 0  and exists (select 1 from books where `group` & book_groups.groupId > 0))
            or groupId = -1
            or (groupId = -2 and exists (select 1 from books where type & ${BookType.local} > 0))
            or (groupId = -3 and exists (select 1 from books where type & ${BookType.audio} > 0))
            or (groupId = -6 and exists (select 1 from books where type & ${BookType.video} > 0))
            or (groupId = -11 and exists (select 1 from books where type & ${BookType.updateError} > 0))
            or (groupId = -4 
                and exists (
                    select 1 from books 
                    where type & ${BookType.audio} = 0
                    and type & ${BookType.video} = 0
                    and type & ${BookType.local} = 0
                    and const.sumGroupId & `group` = 0
                )
            )
            or (groupId = -5
                and exists (
                    select 1 from books 
                    where type & ${BookType.audio} = 0
                    and type & ${BookType.video} = 0
                    and type & ${BookType.local} > 0
                    and const.sumGroupId & `group` = 0
                )
            )
        )
        ORDER BY `order`
    """.trimIndent()
}
