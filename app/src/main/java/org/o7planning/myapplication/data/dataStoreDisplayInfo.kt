package org.o7planning.myapplication.data

class dataStoreDisplayInfo(
    val storeId: String? = null,
    val ownerId: String? = null,
    val name: String? = null,
    val address: String? = null,
    val tableNumber: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val des: String? = null,
    val openingHour: String? = null,
    val closingHour: String? = null,
    val totalBooking: Int? = 0,//tổng đặt bàn
    val simpleEnding: Int? = 0,//kết thúc đơn
    val profit: Double? = 0.0,//Doanh thu
    val tableActive: Int? = 0,
    val tableEmpty: Int? = 0,
    val paidBookings: Int? = 0,//đã thanh toán
    val latitude: Double? = null,
    val longitude: Double? = null,
    var distance: Double? = null
)