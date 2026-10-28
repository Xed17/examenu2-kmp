package pe.edu.upeu.ecorecicla.data.local

import app.cash.sqldelight.db.SqlDriver
import pe.edu.upeu.ecorecicla.db.EcoReciclaDatabase

class DatabaseManager(driver: SqlDriver) {
    val database = EcoReciclaDatabase(driver)
    val queries = database.ecoReciclaQueries
}

