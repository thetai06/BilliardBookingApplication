package org.o7planning.myapplication.admin

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

data class PreBookCheckRequest(
    val storeId: String,
    val dataDate: String,
    val dataStartTime: String,
    val dataEndTime: String,
    val voucherCode: String?
)

data class PreBookCheckResponse(
    val available: Boolean, // Còn bàn hay không (tích hợp check_availability)
    val message: String?,
    val availableCount: Int? = null,
    val basePrice: Double, // Giá cơ bản chưa giảm
    val discountAmount: Double, // Số tiền giảm giá
    val finalPrice: Double // Tổng tiền cuối cùng
)

// ====================================================================
// DATA CLASSES CHO USER MANAGEMENT (AUTH ROUTES: /api/...)
// ====================================================================

data class CreateUserRequest(
    val name: String,
    val email: String,
    val phone: String?,
    val password: String,
    val role: String,
    val address: String?,
    val storeId: String? // Bắt buộc nếu role là 'manager'
)

data class UpdateUserRequest(
    val name: String,
    val phone: String?,
    val role: String,
    val address: String?,
    val storeId: String? // Bắt buộc nếu role là 'manager'
)

data class UserResponse(
    val success: Boolean,
    val message: String,
    val userId: String? = null // Trả về khi tạo user thành công
)

// ====================================================================
// DATA CLASSES CHO ĐẶT BÀN/CLB (STORE & AVAILABILITY ROUTES: /...)
// ====================================================================

data class TimeSlot(
    val time: String,
    val available_tables: Int
)

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

data class StoreRequest(
    val name: String,
    val location: String,
    val phoneNumber: String,
    val email: String,
    val tableNumber: String, // Chú ý: Server Node.js của bạn chấp nhận String, nhưng nên là Int
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
// DATA CLASSES CHO THANH TOÁN (PAYMENT ROUTES: /...)
// ====================================================================

data class PaymentRequest(
    val amount: Double,
    val orderId: String, // Mã đơn hàng (booking key)
    val bankCode: String? = null,
    val language: String? = null
)

data class PaymentRequestUpgrade(
    val amount: Double,
    val userId: String,
    val storeId: String
)

data class PaymentResponse(
    val paymentUrl: String // URL VNPAY để redirect người dùng
)

data class VietQrResponse(
    val qrDataString: String // Chuỗi dữ liệu QR Code
)

interface ApiService {

    // --- API QUẢN LÝ USER (Prefix: /api) ---
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

    @POST("/pre_book_check")
    fun preBookCheck(@Body request: PreBookCheckRequest): Call<PreBookCheckResponse>

    // --- API THANH TOÁN (Prefix: /) ---
    @POST("/create_payment_url")
    fun createPaymentUrl(@Body request: PaymentRequest): Call<PaymentResponse>

    @POST("/create_upgrade_payment_url")
    fun createUpgradePaymentUrl(@Body request: PaymentRequestUpgrade): Call<PaymentResponse>

    @POST("/create_vietqr_data")
    fun createVietQrData(@Body request: PaymentRequest): Call<VietQrResponse>

    // --- API CLB & ĐẶT BÀN (Prefix: /) ---
    @POST("/check_availability")
    fun checkAvailability(@Body request: AvailabilityRequest): Call<AvailabilityResponse>

    @POST("/get_availability_timeline")
    fun getAvailabilityTimeline(@Body request: TimelineRequest): Call<List<TimeSlot>>

    @POST("/add_store")
    fun addStore(
        @Header("Authorization") idToken: String,
        @Body storeData: StoreRequest
    ): Call<AddStoreResponse>
}