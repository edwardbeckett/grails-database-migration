/* Copyright 2006-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.databasemigration

import java.sql.SQLException

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class DbmUpdateTests extends AbstractScriptTests {

	void testUpdate() {

		assertTableCount 1

		// should fail since the table isn't there yet
		String message = shouldFail(SQLException) {
			executeUpdate '''
				insert into person(version, name, street1, street2, zipcode)
				values (0, 'test.name', 'test.street1', 'test.street2', '12345')
			'''
		}
		assertTrue message.contains('Table "PERSON" not found')

		copyTestChangelog()

		executeAndCheck 'dbm-update'

		// original + 2 Liquibase + new person table
		assertTableCount 4

		assertTrue output.contains(
			'Starting dbm-update for database sa @ jdbc:h2:tcp://localhost/./target/testdb/testdb')

		// now we should be able to insert into person table
		executeUpdate '''
			insert into person(version, name, street1, street2, zipcode)
			values (0, 'test.name', 'test.street1', 'test.street2', '12345')
		'''
	}

	void testUpdateWithGroovyChanges() {

		assertTableCount 1

		// should fail since the table isn't there yet
		String message = shouldFail(SQLException) {
			executeUpdate "insert into grails(name, foo, bar) values ('test.name', 1000, 5000)"
		}
		assertTrue message.contains('Table "GRAILS" not found')

		copyTestChangelog 'test.custom.changelog'

		def initFile = new File('target/created_in_init')
		initFile.deleteOnExit()
		assertFalse initFile.exists()

		def createFile = new File('target/created_in_create')
		createFile.deleteOnExit()
		assertFalse createFile.exists()

		def rollbackFile = new File('target/created_in_rollback')
		rollbackFile.deleteOnExit()
		assertFalse rollbackFile.exists()

		executeAndCheck 'dbm-update'

		assertTrue output.contains("WARNING: Keep out of reach of children")

		// original + 2 Liquibase + new 'grails' table
		assertTableCount 4

		assertTrue initFile.exists()
		String initFileContent = initFile.text
		assertTrue initFileContent.contains("in init")

		assertTrue createFile.exists()
		String createFileContent = createFile.text
		assertTrue createFileContent.contains("in create")
		assertTrue createFileContent.contains("database class: 'liquibase.database.core.H2Database', typeName: 'h2'")
		assertTrue createFileContent.contains("databaseConnection class: 'liquibase.database.jvm.JdbcConnection'")
		assertTrue createFileContent.contains("connection class: 'org.apache.commons.dbcp.PoolingDataSource\$PoolGuardConnectionWrapper'")

		assertFalse rollbackFile.exists()

		def tableSize = { newSql().firstRow('select count(*) from grails')[0] }

		assertEquals 20, tableSize()

		// now we should be able to insert into grails table
		executeUpdate "insert into grails(name, foo, bar) values ('test.name', 1000, 5000)"
		assertEquals 21, tableSize()

		// roll back the 2nd change
		executeAndCheck(['dbm-rollback-count', '1'])

		assertTrue rollbackFile.exists()
		String rollbackFileContent = rollbackFile.text
		assertTrue rollbackFileContent.contains("in rollback")
		assertTrue rollbackFileContent.contains("database class: 'liquibase.database.core.H2Database', typeName: 'h2'")
		assertTrue rollbackFileContent.contains("databaseConnection class: 'liquibase.database.jvm.JdbcConnection'")
		assertTrue rollbackFileContent.contains("connection class: 'org.apache.commons.dbcp.PoolingDataSource\$PoolGuardConnectionWrapper'")

		// should only have the row we inserted above
		assertEquals 1, tableSize()

		// should fail since the column was dropped
		message = shouldFail(SQLException) {
			executeUpdate "insert into grails(name, foo, bar) values ('test.name.2', 2000, 5100)"
		}
		assertTrue message.contains('Column "BAR" not found')

		executeUpdate "insert into grails(name, foo) values ('test.name.2', 2000)"
		assertEquals 2, tableSize()
	}
}