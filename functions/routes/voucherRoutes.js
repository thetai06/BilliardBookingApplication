// routes/voucherRoutes.js
const express = require('express');
const router = express.Router();
const admin = require('firebase-admin');
const { authenticateToken } = require('../authMiddleware'); // Đảm bảo đường dẫn đúng

// ====================================================================
// 1. LẤY DANH SÁCH VOUCHER (CHỈ ADMIN/OWNER)
// ====================================================================
router.get('/list', authenticateToken, async (req, res) => {
    // Chỉ cho phép Owner xem danh sách voucher
    if (req.user.role !== 'owner') {
        return res.status(403).json({ success: false, message: 'Forbidden: You do not have permission to view vouchers.' });
    }

    try {
        const snapshot = await global.db.ref('dataVoucher').once('value');
        const vouchers = snapshot.val();

        if (!vouchers) {
            return res.status(200).json({ success: true, message: 'No vouchers found.', data: [] });
        }

        // Chuyển object thành mảng để dễ xử lý ở frontend
        const voucherList = Object.keys(vouchers).map(key => ({
            voucherId: key,
            ...vouchers[key]
        }));

        res.status(200).json({ success: true, data: voucherList });

    } catch (error) {
        console.error('[API voucher/list] Error:', error);
        res.status(500).json({ success: false, message: 'Server error retrieving vouchers.' });
    }
});

// ====================================================================
// 2. ÁP DỤNG VOUCHER (Frontend gọi khi đặt bàn)
// ====================================================================
router.post('/apply', async (req, res) => {
    const { voucherCode, bookingAmount, userId } = req.body;

    if (!voucherCode || !bookingAmount || !userId) {
        return res.status(400).json({ success: false, message: 'Thiếu voucherCode, bookingAmount, hoặc userId.' });
    }

    try {
        const snapshot = await global.db.ref('dataVoucher').orderByChild('code').equalTo(voucherCode).once('value');

        if (!snapshot.exists()) {
            return res.status(404).json({ success: false, message: 'Mã voucher không tồn tại.' });
        }

        const voucherKey = Object.keys(snapshot.val())[0];
        const voucher = snapshot.val()[voucherKey];

        // --- BƯỚC XỬ LÝ LOGIC ---

        // 1. Kiểm tra ngày hết hạn
        if (new Date(voucher.expiryDate) < new Date()) {
             return res.status(400).json({ success: false, message: 'Voucher đã hết hạn sử dụng.' });
        }

        // 2. Kiểm tra giới hạn sử dụng (nếu cần)
        // if (voucher.usedCount >= voucher.usageLimit) {
        //      return res.status(400).json({ success: false, message: 'Voucher đã hết lượt sử dụng.' });
        // }

        // 3. Tính toán giá trị giảm giá
        let discountAmount = 0;
        let finalAmount = bookingAmount;

        if (voucher.type === 'percent') {
            discountAmount = bookingAmount * (voucher.value / 100);
            if (voucher.maxDiscount && discountAmount > voucher.maxDiscount) {
                discountAmount = voucher.maxDiscount;
            }
        } else if (voucher.type === 'fixed') {
            discountAmount = voucher.value;
        }

        finalAmount = bookingAmount - discountAmount;
        if (finalAmount < 0) finalAmount = 0;

        // 4. Nếu hợp lệ, trả về kết quả
        res.status(200).json({
            success: true,
            message: 'Áp dụng voucher thành công.',
            voucherId: voucherKey,
            discountAmount: discountAmount,
            finalAmount: finalAmount
        });

    } catch (error) {
        console.error('[API voucher/apply] Error:', error);
        res.status(500).json({ success: false, message: 'Lỗi server khi áp dụng voucher.' });
    }
});

// ====================================================================
// EXPORT ROUTER
// ====================================================================
module.exports = router;