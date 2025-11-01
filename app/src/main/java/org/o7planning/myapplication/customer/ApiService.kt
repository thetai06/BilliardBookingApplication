package org.o7planning.myapplication.admin

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

// ====================================================================
// DATA CLASSES CHO TIMELINE OWNER (TimelineChartView)
// ====================================================================

data class BusyInterval(
    val startTime: String, // HH:mm
    val endTime: String    // HH:mm
)
// Dữ liệu chi tiết trả về từ Server cho Timeline Owner (3 màu)
data class DetailedTimelineData(
    val totalTables: Int,
    val openingHour: String, // HH:mm
    val closingHour: String, // HH:mm
    val busyBookings: List<BusyInterval>
)

// ====================================================================
// DATA CLASSES CHUNG (Đặt bàn, Store)
// ====================================================================

data class TimelineRequest(
    val storeId: String,
    val date: String
)

data class AvailabilityRequest(
    val storeId: String,
    val dateTime: String,
    val startTime: String,
    val endTime: String,
    val totalTables: Int
)

data class AvailabilityResponse(
    val available: Boolean,
    val availableCount: Int? = null, // Số bàn còn trống
    val message: String?
)

data class TimeSlot(
    val time: String,
    val available_tables: Int
)

data class StoreRequest(
    val name: String,
    val location: String,
    val phoneNumber: String,
    val email: String,
    val tableNumber: String,
    val des: String?,
    val openingHour: String,
    val closingHour: String,
    val priceTable: Int?,
    val latitude: Double?,
    val longitude: Double?
)

data class AddStoreResponse(
    val success: Boolean,
    val message: String,
    val paymentRequired: Boolean = false,
    val paymentUrl: String? = null,
    val paymentOrderId: String? = null,
    val storeId: String? = null
)

// ====================================================================
// DATA CLASSES CHO CHỨC NĂNG KHÁC (PreCheck, User, Payment)
// ====================================================================

data class PreBookCheckRequest(
    val storeId: String,
    val dataDate: String,
    val dataStartTime: String,
    val dataEndTime: String,
    val voucherCode: String?
)

data class PreBookCheckResponse(
    val available: Boolean,
    val message: String?,
    val availableCount: Int? = null,
    val basePrice: Double,
    val discountAmount: Double,
    val finalPrice: Double
)

data class CreateUserRequest(
    val name: String,
    val email: String,
    val phone: String?,
    val password: String,
    val role: String,
    val address: String?,
    val storeId: String?
)

data class UpdateUserRequest(
    val name: String,
    val phone: String?,
    val role: String,
    val address: String?,
    val storeId: String?
)

data class UserResponse(
    val success: Boolean,
    val message: String,
    val userId: String? = null
)

data class PaymentRequest(
    val amount: Double,
    val orderId: String,
    val bankCode: String? = null,
    val language: String? = null
)

data class PaymentRequestUpgrade(
    val amount: Double,
    val userId: String,
    val storeId: String
)

data class PaymentResponse(
    val paymentUrl: String
)

data class VietQrResponse(
    val qrDataString: String
)

// ====================================================================
// INTERFACE API SERVICE
// ====================================================================

interface ApiService {

    // --- API TIMELINE OWNER (3 MÀU) ---
    @POST("/get_detailed_timeline_data")
    fun getDetailedTimelineData(@Body request: TimelineRequest): Call<DetailedTimelineData>

    // --- API TÍNH NĂNG CHÍNH (Store & Booking) ---
    @POST("/check_availability")
    fun checkAvailability(@Body request: AvailabilityRequest): Call<AvailabilityResponse>

    @POST("/get_availability_timeline")
    fun getAvailabilityTimeline(@Body request: TimelineRequest): Call<List<TimeSlot>>

    @POST("/add_store")
    fun addStore(
        @Header("Authorization") idToken: String,
        @Body storeData: StoreRequest
    ): Call<AddStoreResponse>

    @POST("/pre_book_check")
    fun preBookCheck(@Body request: PreBookCheckRequest): Call<PreBookCheckResponse>

    // --- API QUẢN LÝ USER ---
    @POST("/api/createUser")
    fun createUser(
        @Header("Authorization") idToken: String,
        @Body userRequest: CreateUserRequest
    ): Call<UserResponse>

    @PUT("/api/updateUser/{id}")
    fun updateUser(
        @Header("Authorization") idToken: String,
        @Path("id") userId: String,
        @Body updateData: UpdateUserRequest
    ): Call<UserResponse>

    @DELETE("/api/deleteUser/{id}")
    fun deleteUser(
        @Header("Authorization") idToken: String,
        @Path("id") userId: String
    ): Call<UserResponse>

    // --- API THANH TOÁN ---
    @POST("/create_payment_url")
    fun createPaymentUrl(@Body request: PaymentRequest): Call<PaymentResponse>

    @POST("/create_upgrade_payment_url")
    fun createUpgradePaymentUrl(@Body request: PaymentRequestUpgrade): Call<PaymentResponse>

    @POST("/create_vietqr_data")
    fun createVietQrData(@Body request: PaymentRequest): Call<VietQrResponse>
}