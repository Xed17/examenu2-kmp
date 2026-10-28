package pe.edu.upeu.ecorecicla.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pe.edu.upeu.ecorecicla.db.EcoReciclaDatabase

class DriverFactory(private val context: Context) {
    fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(EcoReciclaDatabase.Schema, context, "ecorecicla.db")
    }
}
