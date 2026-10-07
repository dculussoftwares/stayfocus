package com.dculus.stayfocused.core.data.db

import androidx.room.TypeConverter
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Room type converters. Instants are epoch millis, dates epoch days, times ISO `HH:mm`, enums their names. */
@Suppress("TooManyFunctions")
class Converters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun daysToInt(value: DaysOfWeek): Int = value.mask

    @TypeConverter
    fun intToDays(value: Int): DaysOfWeek = DaysOfWeek(value)

    @TypeConverter
    fun blockTypeToString(value: BlockType): String = value.name

    @TypeConverter
    fun stringToBlockType(value: String): BlockType = BlockType.valueOf(value)

    @TypeConverter
    fun limitPeriodToString(value: LimitPeriod?): String? = value?.name

    @TypeConverter
    fun stringToLimitPeriod(value: String?): LimitPeriod? = value?.let(LimitPeriod::valueOf)

    @TypeConverter
    fun blockSourceToString(value: BlockSource): String = value.name

    @TypeConverter
    fun stringToBlockSource(value: String): BlockSource = BlockSource.valueOf(value)
}
