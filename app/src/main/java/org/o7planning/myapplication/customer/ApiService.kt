package org.o7planning.myapplication.admin

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST


// Data class cho ĐẶT BÀN (từ FragmentBooktable)
data class PaymentRequest(
    val amount: Double,
    val orderId: String
)

// Data class cho NÂNG CẤP (từ FragmentUpgrade)
data class PaymentRequestUpgrade(
    val amount: Double,
    val userId: String,
    val storeId: String
)

// Data class cho Response (DÙNG CHUNG)
data class PaymentResponse(
    val paymentUrl: String
)

data class VietQrResponse(
    val qrDataString: String
)

interface ApiService {
    // API cho Đặt bàn
    @POST("/create_payment_url")
    fun createPaymentUrl(@Body request: PaymentRequest): Call<PaymentResponse>

    // API cho Nâng cấp
    @POST("/create_upgrade_payment_url")
    fun createUpgradePaymentUrl(@Body request: PaymentRequestUpgrade): Call<PaymentResponse>

    @POST("/create_vietqr_data")
    fun createVietQrData(@Body request: PaymentRequest): Call<VietQrResponse>

}