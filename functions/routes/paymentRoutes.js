// routes/paymentRoutes.js
const express = require('express');
const router = express.Router();
const { createVnPayUrl, createVietQrData, handleVnPayIpn, handleVietQrIpn } = require('../services/paymentService');
const { sortObject } = require('../utils/helpers'); // Cần sortObject cho VNPAY Return

// ENDPOINT 1: TẠO LINK THANH TOÁN VNPAY (ĐẶT BÀN)
router.post('/create_payment_url', (req, res) => {
    createVnPayUrl(req, res, 'booking');
});

// ENDPOINT 2: TẠO CHUỖI DỮ LIỆU VIETQR
router.post('/create_vietqr_data', async (req, res) => {
    createVietQrData(req, res);
});

// ENDPOINT 4: VNPAY RETURN URL (Chỉ chuyển hướng)
router.get('/vnpay_return', function (req, res) {
    try {
        let vnp_Params = req.query;
        const secureHash = vnp_Params['vnp_SecureHash'];
        delete vnp_Params['vnp_SecureHash']; delete vnp_Params['vnp_SecureHashType'];
        vnp_Params = sortObject(vnp_Params);
        const signData = querystring.stringify(vnp_Params, { encode: false });
        const hmac = crypto.createHmac("sha512", global.envVars.secretKey);
        const signed = hmac.update(Buffer.from(signData, 'utf-8')).digest("hex");

        if (secureHash === signed) {
            console.log(`[VNPAY Return] Thanh cong cho order ${vnp_Params['vnp_TxnRef']}`);
            res.redirect(global.envVars.frontendSuccessUrl || '/');
        } else {
            console.log(`[VNPAY Return] That bai (sai hash) cho order ${vnp_Params['vnp_TxnRef']}`);
            res.redirect(global.envVars.frontendFailUrl || '/');
        }
    } catch (error) {
        console.error('[VNPAY Return] Loi xu ly:', error);
        res.redirect(global.envVars.frontendFailUrl || '/');
    }
});

// ENDPOINT 5: VNPAY IPN URL (Cập nhật DB cho VNPAY)
router.get('/vnpay_ipn', handleVnPayIpn);

// ENDPOINT 6: VIETQR IPN URL (Cập nhật DB cho VietQR)
router.post('/vietqr_ipn', handleVietQrIpn);

module.exports = router;