package org.o7planning.myapplication.data

class dataOverviewOwner(
    var storeId:String?=null,
    var ownerId:String? = null,
    var totalBooking: Int? = null, //tổng đặt bàn
    var simpleEnding: Int? = null, //kết thúc đơn
    var paidBookings: Int? = null, //đã thanh toán
    var profit: Double? = null, //Doanh thu
    var tableActive: Int? = null,// bàn hoạt động
    var tableEmpty: Int? = null, // bàn trống
)