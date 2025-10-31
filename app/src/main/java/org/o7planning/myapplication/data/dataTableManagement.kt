package org.o7planning.myapplication.data

class dataTableManagement(
    val id:String? = null,
    val userId:String? =null,
    val storeOwnerId: String? = null,
    val storeId:String? = null,
    val phoneNumber:String? = null,
    val email:String? = null,
    val name: String="",
    val startTime: String="",
    val endTime:String="",
    val dateTime:String="",
    val person: String="",
    val money: Double = 0.0,
    val paymentUrl: String? = null,
    val qrDataString: String? = null,
    val createdAt: Long? = null,
    val status: String? = null,
    val paymentStatus: String? = null,
    val addressClb: String? = null)