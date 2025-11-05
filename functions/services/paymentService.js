const moment = require('moment-timezone');
const crypto = require('crypto');
const querystring = require('qs');
const axios = require('axios');
const admin = require('firebase-admin');
const { sortObject } = require('../utils/helpers');

// --- HÀM TẠO URL VNPAY CHUNG ---
async function createVnPayUrl(req, res, type) {
    try {
        const { tmnCode, secretKey, vnpUrl, returnUrl } = global.envVars;
        const createDate = moment().tz('Asia/Ho_Chi_Minh').format('YYYYMMDDHHmmss');
        const ipAddr = req.headers['x-forwarded-for'] || req.connection.remoteAddress || req.socket.remoteAddress || '127.0.0.1';

        let orderId, amount, orderInfo;

        // Chỉ giữ lại logic cho type === 'booking'
        if (type === 'booking') {
            const { amount: reqAmount, bankCode, language, orderId: reqOrderId } = req.body;
            if (!reqAmount || !reqOrderId) { return res.status(400).json({ error: 'Missing required fields: amount, orderId' }); }
            orderId = reqOrderId;
            amount = reqAmount;
            orderInfo = `Thanh toan don hang ${orderId}`;
        }
        /* KHỐI else if (type === 'upgrade') ĐÃ BỊ XÓA */
        else {
            return res.status(400).json({ error: 'Payment type not supported.' });
        }


        const orderType = 'other';
        const locale = req.body.language || 'vn';
        const currCode = 'VND';
        let vnp_Params = {
            'vnp_Version': '2.1.0', 'vnp_Command': 'pay', 'vnp_TmnCode': tmnCode,
            'vnp_Locale': locale, 'vnp_CurrCode': currCode, 'vnp_TxnRef': orderId,
            'vnp_OrderInfo': orderInfo, 'vnp_OrderType': orderType, 'vnp_Amount': amount * 100,
            'vnp_ReturnUrl': returnUrl, 'vnp_IpAddr': ipAddr, 'vnp_CreateDate': createDate
        };
        if (req.body.bankCode && type === 'booking') vnp_Params['vnp_BankCode'] = req.body.bankCode;

        vnp_Params = sortObject(vnp_Params);
        const signData = querystring.stringify(vnp_Params, { encode: false });
        const hmac = crypto.createHmac("sha512", secretKey);
        const signed = hmac.update(Buffer.from(signData, 'utf-8')).digest("hex");
        vnp_Params['vnp_SecureHash'] = signed;
        const paymentUrl = vnpUrl + '?' + querystring.stringify(vnp_Params, { encode: false });

        console.log(`[VNPAY Create][${type}] Tao URL thanh cong cho ${orderId}`);
        res.json({ paymentUrl });
    } catch (error) {
        console.error(`[VNPAY Create][${type}] Loi khi tao URL:`, error);
        res.status(500).json({ error: 'Internal server error while creating VNPAY URL.' });
    }
}

// --- HÀM TẠO CHUỖI VIETQR ---
async function createVietQrData(req, res) {
    const { amount, orderId } = req.body;
    if (!amount || !orderId) { return res.status(400).json({ error: 'Missing required fields: amount, orderId' }); }

    const { VIETQR_API_URL, VIETQR_API_KEY, VIETQR_MERCHANT_ACC_NO, VIETQR_MERCHANT_BIN, VIETQR_MERCHANT_NAME } = global.envVars;

    if (!VIETQR_API_URL || !VIETQR_API_KEY || !VIETQR_MERCHANT_ACC_NO || !VIETQR_MERCHANT_BIN || !VIETQR_MERCHANT_NAME) {
         console.error('[VietQR] Lỗi: Thiếu cấu hình biến môi trường VietQR.');
         return res.status(500).json({ error: 'VietQR service is not configured.'});
    }

    try {
        const vietQrApiPayload = {
            accountNo: VIETQR_MERCHANT_ACC_NO,
            accountName: VIETQR_MERCHANT_NAME,
            acqId: VIETQR_MERCHANT_BIN,
            amount: amount,
            addInfo: orderId, // Đây là push().key
            format: "text"
        };
        const apiResponse = await axios.post(VIETQR_API_URL, vietQrApiPayload, {
            headers: { 'x-api-key': VIETQR_API_KEY, 'Content-Type': 'application/json' }
        });

        if (apiResponse.data && apiResponse.data.code === '00' && apiResponse.data.data && apiResponse.data.data.qrCode) {
             const qrString = apiResponse.data.data.qrCode;
             res.json({ qrDataString: qrString });
        } else {
             console.error('[VietQR] Loi tu API VietQR (khong thanh cong):', apiResponse.data);
             res.status(500).json({ error: 'Lỗi khi tạo mã VietQR từ nhà cung cấp.' });
        }
    } catch (error) {
        console.error('[VietQR] Loi ngoai le khi goi API VietQR:', error.response ? JSON.stringify(error.response.data) : error.message);
        res.status(500).json({ error: 'Lỗi hệ thống khi gọi API VietQR.' });
    }
}

// --- HÀM XỬ LÝ IPN VNPAY ---
async function handleVnPayIpn(req, res) {
    const { secretKey } = global.envVars;
    let vnp_Params = req.query;
    let secureHash = vnp_Params['vnp_SecureHash'];
    delete vnp_Params['vnp_SecureHash']; delete vnp_Params['vnp_SecureHashType'];
    vnp_Params = sortObject(vnp_Params);
    let signData = querystring.stringify(vnp_Params, { encode: false });
    let hmac = crypto.createHmac("sha512", secretKey);
    let signed = hmac.update(Buffer.from(signData, 'utf-8')).digest("hex");

    if (secureHash !== signed) {
        console.log('[VNPAY IPN] Lỗi: Invalid Checksum');
        return res.status(200).json({ RspCode: '97', Message: 'Invalid Checksum' });
    }

    const orderId = vnp_Params['vnp_TxnRef'];
    const rspCode = vnp_Params['vnp_ResponseCode'];

    try {
        if (orderId.startsWith('ADDSTORECHARGE_')) {
            const pendingRef = global.db.ref(`pendingChargedStores/${orderId}`);
            if (rspCode == '00') {
                const pendingSnap = await pendingRef.once('value');
                if (!pendingSnap.exists()) { return res.status(200).json({ RspCode: '01', Message: 'Pending Order not found' }); }
                const pendingData = pendingSnap.val();
                const storeOwnerId = pendingData.ownerId;
                const newStoreRef = global.db.ref('dataStore').push();
                const finalStoreId = newStoreRef.key;
                if (!finalStoreId) throw new Error("Không thể tạo final storeId");
                const finalStoreData = {
                    storeId: finalStoreId, ownerId: storeOwnerId, name: pendingData.name,
                    image: null, address: pendingData.location, phone: pendingData.phoneNumber,
                    email: pendingData.email, tableNumber: pendingData.tableNumber, des: pendingData.des || "",
                    openingHour: pendingData.openingHour, closingHour: pendingData.closingHour,
                    priceTable: pendingData.priceTable || 0,
                    latitude: pendingData.latitude || null, longitude: pendingData.longitude || null
                };
                const finalOverviewData = {
                    storeId: finalStoreId, ownerId: storeOwnerId, totalBooking: 0,
                    simpleEnding: 0, paidBookings: 0, profit: 0.0, tableActive: 0,
                    tableEmpty: parseInt(pendingData.tableNumber) || 0,
                };
                await Promise.all([
                    global.db.ref(`dataStore/${finalStoreId}`).set(finalStoreData),
                    global.db.ref(`dataOverview/${finalStoreId}`).set(finalOverviewData),
                    pendingRef.remove()
                ]);
            } else {
                await pendingRef.remove();
            }
        }
        else { // Xử lý đơn hàng đặt bàn (dataBookTable) và các trường hợp khác
            const orderRef = global.db.ref(`dataBookTable/${orderId}`);
            const snapshot = await orderRef.once('value');
            if (!snapshot.exists()) { return res.status(200).json({ RspCode: '01', Message: 'Order not found' }); }
            const orderData = snapshot.val();
            if (orderData.paymentStatus !== 'Chờ thanh toán') { return res.status(200).json({ RspCode: '02', Message: 'Order already confirmed' }); }
            let paymentStatus = (rspCode == '00') ? 'Đã thanh toán' : 'FAILED';
            await orderRef.update({ paymentStatus: paymentStatus });
        }

        return res.status(200).json({ RspCode: '00', Message: 'Success' });
    } catch (error) {
        console.error(`[VNPAY IPN] LOI XU LY cho order ${orderId}:`, error);
        return res.status(200).json({ RspCode: '99', Message: 'Internal Server Error' });
    }
}

// --- HÀM XỬ LÝ IPN VIETQR ---
async function handleVietQrIpn(req, res) {
    const { VIETQR_IPN_SECRET } = global.envVars;
    let vnp_Params = req.query;
    let secureHash = vnp_Params['vnp_SecureHash'];
    delete vnp_Params['vnp_SecureHash']; delete vnp_Params['vnp_SecureHashType'];
    vnp_Params = sortObject(vnp_Params);
    let signData = querystring.stringify(vnp_Params, { encode: false });
    let hmac = crypto.createHmac("sha512", secretKey);
    let signed = hmac.update(Buffer.from(signData, 'utf-8')).digest("hex");

    if (secureHash !== signed) {
        console.log('[VNPAY IPN] Lỗi: Invalid Checksum');
        return res.status(200).json({ RspCode: '97', Message: 'Invalid Checksum' });
    }

    const orderId = vnp_Params['vnp_TxnRef'];
    const rspCode = vnp_Params['vnp_ResponseCode'];

    try {
        const orderRef = global.db.ref(`dataBookTable/${orderId}`);
        const snapshot = await orderRef.once('value');
        if (!snapshot.exists()) { return res.status(200).json({ RspCode: '01', Message: 'Order not found' }); }
        const orderData = snapshot.val();
        if (orderData.paymentStatus !== 'Chờ thanh toán') { return res.status(200).json({ RspCode: '02', Message: 'Order already confirmed' }); }
        let paymentStatus = (rspCode == '00') ? 'Đã thanh toán' : 'FAILED';
        await orderRef.update({ paymentStatus: paymentStatus });

        return res.status(200).json({ RspCode: '00', Message: 'Success' });
    } catch (error) {
        console.error(`[VNPAY IPN] LOI XU LY cho order ${orderId}:`, error);
        return res.status(200).json({ RspCode: '99', Message: 'Internal Server Error' });
    }
}


module.exports = { createVnPayUrl, createVietQrData, handleVnPayIpn, handleVietQrIpn };