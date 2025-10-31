package org.o7planning.myapplication.data

data class dataVoucher(
    var id: String? = null,
    var code: String? = null,
    var description: String? = null,
    var discount: Long? = 0L,
    var expiryDate: String? = null,
    var isActive: Boolean? = true,
    var minOrder: Long? = 0L,
    var storeId: String? = null
)