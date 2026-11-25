package com.example.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(
  tableName = "tanks_meta",
  indices = [Index(value = ["name"], unique = false)]
)
data class TankMetaEntity(
  @PrimaryKey val id: String,
  val name: String,
  val type: String,
  val shape: String,
  val maxVolumeLiters: Double,
  val hasN2Factor: Boolean,
  val productId: String?,
  val customPropsJson: String
)

@Entity(
  tableName = "calibration_dip",
  indices = [
    Index(value = ["tankId", "dipMm"]),
    Index(value = ["tankName", "dipMm"])
  ]
)
data class CalibrationDipEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val tankId: String,
  val tankName: String,
  val dipMm: Double,
  val volumeL: Double
)

@Entity(
  tableName = "calibration_fraction",
  indices = [
    Index(value = ["tankId", "mm"]),
    Index(value = ["tankName", "mm"])
  ]
)
data class CalibrationFractionEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val tankId: String,
  val tankName: String,
  val mm: Int,
  val volumeL: Double
)

@Entity(
  tableName = "calibration_n2",
  indices = [
    Index(value = ["tankId", "heightMm"]),
    Index(value = ["tankName", "heightMm"])
  ]
)
data class CalibrationN2Entity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val tankId: String,
  val tankName: String,
  val heightMm: Double,
  val volumeL: Double
)

@Entity(tableName = "bulk_dip_sheets")
data class BulkDipSheetEntity(
  @PrimaryKey val dateStr: String,
  val payloadJson: String,
  val lastModified: Long
)

@Entity(tableName = "production_report_days")
data class ProductionReportDayEntity(
  @PrimaryKey val dateStr: String,
  val payloadJson: String,
  val lastModified: Long
)

@Entity(tableName = "loss_reports")
data class LossReportEntity(
  @PrimaryKey val id: String,
  val startTime: String,
  val endTime: String,
  val timestamp: String,
  val payloadJson: String
)

@Entity(tableName = "daily_injection_records")
data class DailyInjectionRecordEntity(
  @PrimaryKey val id: String,
  val date: String,
  val timestamp: String,
  val tankName: String,
  val payloadJson: String
)

@Entity(tableName = "transactions")
data class TransactionEntity(
  @PrimaryKey val id: String,
  val dateTime: String,
  val tankName: String,
  val payloadJson: String
)

@Entity(tableName = "blend_records")
data class BlendRecordEntity(
  @PrimaryKey val id: String,
  val dateTime: String,
  val tankName: String,
  val payloadJson: String
)

@Dao
interface TankCalcDao {
  // --- Tank Meta & Calibration ---
  @Query("SELECT * FROM tanks_meta")
  fun getAllTankMetasSync(): List<TankMetaEntity>

  @Query("SELECT * FROM calibration_dip ORDER BY tankId, dipMm ASC")
  fun getAllDipRowsSync(): List<CalibrationDipEntity>

  @Query("SELECT * FROM calibration_fraction ORDER BY tankId, mm ASC")
  fun getAllFractionRowsSync(): List<CalibrationFractionEntity>

  @Query("SELECT * FROM calibration_n2 ORDER BY tankId, heightMm ASC")
  fun getAllN2RowsSync(): List<CalibrationN2Entity>

  @Query("SELECT * FROM calibration_dip WHERE tankId = :tankId ORDER BY dipMm ASC")
  suspend fun getDipRowsForTank(tankId: String): List<CalibrationDipEntity>

  @Query("SELECT * FROM calibration_fraction WHERE tankId = :tankId ORDER BY mm ASC")
  suspend fun getFractionRowsForTank(tankId: String): List<CalibrationFractionEntity>

  @Query("SELECT * FROM calibration_n2 WHERE tankId = :tankId ORDER BY heightMm ASC")
  suspend fun getN2RowsForTank(tankId: String): List<CalibrationN2Entity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertTankMetasSync(metas: List<TankMetaEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertDipRowsSync(rows: List<CalibrationDipEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertFractionRowsSync(rows: List<CalibrationFractionEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertN2RowsSync(rows: List<CalibrationN2Entity>)

  @Query("DELETE FROM tanks_meta")
  fun clearTankMetasSync()

  @Query("DELETE FROM calibration_dip")
  fun clearDipRowsSync()

  @Query("DELETE FROM calibration_fraction")
  fun clearFractionRowsSync()

  @Query("DELETE FROM calibration_n2")
  fun clearN2RowsSync()

  @Transaction
  fun replaceAllTanksAndCalibrationsSync(
    metas: List<TankMetaEntity>,
    dips: List<CalibrationDipEntity>,
    fractions: List<CalibrationFractionEntity>,
    n2s: List<CalibrationN2Entity>
  ) {
    clearTankMetasSync()
    clearDipRowsSync()
    clearFractionRowsSync()
    clearN2RowsSync()
    insertTankMetasSync(metas)
    if (dips.isNotEmpty()) insertDipRowsSync(dips)
    if (fractions.isNotEmpty()) insertFractionRowsSync(fractions)
    if (n2s.isNotEmpty()) insertN2RowsSync(n2s)
  }

  // --- Bulk Dip Sheets ---
  @Query("SELECT * FROM bulk_dip_sheets ORDER BY dateStr DESC")
  fun getAllBulkDipSheetsSync(): List<BulkDipSheetEntity>

  @Query("SELECT * FROM bulk_dip_sheets ORDER BY dateStr DESC")
  fun observeBulkDipSheets(): Flow<List<BulkDipSheetEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun upsertBulkDipSheetSync(entity: BulkDipSheetEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun upsertBulkDipSheetsSync(entities: List<BulkDipSheetEntity>)

  // --- Production Report Days ---
  @Query("SELECT * FROM production_report_days ORDER BY dateStr DESC")
  fun getAllProductionReportDaysSync(): List<ProductionReportDayEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun upsertProductionReportDaySync(entity: ProductionReportDayEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun upsertProductionReportDaysSync(entities: List<ProductionReportDayEntity>)

  @Query("DELETE FROM production_report_days WHERE dateStr = :dateStr")
  fun deleteProductionReportDaySync(dateStr: String)

  @Query("DELETE FROM production_report_days")
  fun clearProductionReportDaysSync()

  // --- Loss Reports ---
  @Query("SELECT * FROM loss_reports")
  fun getAllLossReportsSync(): List<LossReportEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertLossReportsSync(entities: List<LossReportEntity>)

  @Query("DELETE FROM loss_reports")
  fun clearLossReportsSync()

  @Transaction
  fun replaceAllLossReportsSync(entities: List<LossReportEntity>) {
    clearLossReportsSync()
    if (entities.isNotEmpty()) insertLossReportsSync(entities)
  }

  // --- Daily Injection Records ---
  @Query("SELECT * FROM daily_injection_records")
  fun getAllDailyInjectionRecordsSync(): List<DailyInjectionRecordEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertDailyInjectionRecordsSync(entities: List<DailyInjectionRecordEntity>)

  @Query("DELETE FROM daily_injection_records")
  fun clearDailyInjectionRecordsSync()

  @Transaction
  fun replaceAllDailyInjectionRecordsSync(entities: List<DailyInjectionRecordEntity>) {
    clearDailyInjectionRecordsSync()
    if (entities.isNotEmpty()) insertDailyInjectionRecordsSync(entities)
  }

  // --- Transactions ---
  @Query("SELECT * FROM transactions")
  fun getAllTransactionsSync(): List<TransactionEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertTransactionsSync(entities: List<TransactionEntity>)

  @Query("DELETE FROM transactions")
  fun clearTransactionsSync()

  @Transaction
  fun replaceAllTransactionsSync(entities: List<TransactionEntity>) {
    clearTransactionsSync()
    if (entities.isNotEmpty()) insertTransactionsSync(entities)
  }

  // --- Blend Records ---
  @Query("SELECT * FROM blend_records")
  fun getAllBlendRecordsSync(): List<BlendRecordEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertBlendRecordsSync(entities: List<BlendRecordEntity>)

  @Query("DELETE FROM blend_records")
  fun clearBlendRecordsSync()

  @Transaction
  fun replaceAllBlendRecordsSync(entities: List<BlendRecordEntity>) {
    clearBlendRecordsSync()
    if (entities.isNotEmpty()) insertBlendRecordsSync(entities)
  }
}

@Database(
  entities = [
    TankMetaEntity::class,
    CalibrationDipEntity::class,
    CalibrationFractionEntity::class,
    CalibrationN2Entity::class,
    BulkDipSheetEntity::class,
    ProductionReportDayEntity::class,
    LossReportEntity::class,
    DailyInjectionRecordEntity::class,
    TransactionEntity::class,
    BlendRecordEntity::class
  ],
  version = 1,
  exportSchema = false
)
abstract class TankCalcDatabase : RoomDatabase() {
  abstract fun tankCalcDao(): TankCalcDao

  companion object {
    @Volatile
    private var INSTANCE: TankCalcDatabase? = null

    fun getDatabase(context: Context): TankCalcDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          TankCalcDatabase::class.java,
          "tank_calc_refinery.db"
        )
          .allowMainThreadQueries()
          .fallbackToDestructiveMigration(true)
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
