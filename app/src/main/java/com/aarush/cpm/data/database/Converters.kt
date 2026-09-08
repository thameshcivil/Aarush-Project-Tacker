package com.aarush.cpm.data.database

import androidx.room.TypeConverter
import com.aarush.cpm.data.entity.*

class Converters {

    @TypeConverter
    fun fromProjectStatus(v: ProjectStatus): String = v.name
    @TypeConverter
    fun toProjectStatus(v: String): ProjectStatus = ProjectStatus.valueOf(v)

    @TypeConverter
    fun fromCostCategory(v: CostCategory): String = v.name
    @TypeConverter
    fun toCostCategory(v: String): CostCategory = CostCategory.valueOf(v)

    @TypeConverter
    fun fromVendorWorkCategory(v: VendorWorkCategory): String = v.name
    @TypeConverter
    fun toVendorWorkCategory(v: String): VendorWorkCategory = VendorWorkCategory.valueOf(v)

    @TypeConverter
    fun fromVendorRateType(v: VendorRateType): String = v.name
    @TypeConverter
    fun toVendorRateType(v: String): VendorRateType = VendorRateType.valueOf(v)

    @TypeConverter
    fun fromExpenseType(v: ExpenseType): String = v.name
    @TypeConverter
    fun toExpenseType(v: String): ExpenseType = ExpenseType.valueOf(v)

    @TypeConverter
    fun fromPaymentMode(v: PaymentMode): String = v.name
    @TypeConverter
    fun toPaymentMode(v: String): PaymentMode = PaymentMode.valueOf(v)

    @TypeConverter
    fun fromPaymentStatus(v: PaymentStatus): String = v.name
    @TypeConverter
    fun toPaymentStatus(v: String): PaymentStatus = PaymentStatus.valueOf(v)

    @TypeConverter
    fun fromActivityStatus(v: ActivityStatus): String = v.name
    @TypeConverter
    fun toActivityStatus(v: String): ActivityStatus = ActivityStatus.valueOf(v)

    @TypeConverter
    fun fromNotificationType(v: NotificationType): String = v.name
    @TypeConverter
    fun toNotificationType(v: String): NotificationType = NotificationType.valueOf(v)

    @TypeConverter
    fun fromBOQQuantityMode(v: BOQQuantityMode): String = v.name
    @TypeConverter
    fun toBOQQuantityMode(v: String): BOQQuantityMode = BOQQuantityMode.valueOf(v)
}
