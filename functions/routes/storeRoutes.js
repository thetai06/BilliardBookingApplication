// routes/storeRoutes.js
const express = require('express');
const router = express.Router();
const admin = require('firebase-admin');
const moment = require('moment-timezone');
const crypto = require('crypto');
const querystring = require('qs');
const { authenticateToken } = require('../middleware/authMiddleware');
const { parseDateTime, sortObject } = require('../utils/helpers');


// Khai báo chung
const TIMEZONE = 'Asia/Ho_Chi_Minh';
const dateFormat = "DD/MM/YYYY";
const datetimeFormat = "DD/MM/YYYY HH:mm";
const BUSY_STATUSES = ["Đang chơi", "Đang chờ"];


// --- ENDPOINT THÊM CỬA HÀNG (GIỮ NGUYÊN) ---
router.post('/add_store', authenticateToken, async (req, res) => {
    const ownerId = req.user.uid;
    const storeInfo = req.body;
    const { tmnCode, secretKey, vnpUrl, returnUrl, VNP_RETURNURL_CHARGE } = global.envVars;
    const ADD_STORE_FEE = global.ADD_STORE_FEE;
    const FREE_STORE_LIMIT = global.FREE_STORE_LIMIT;

    if (!storeInfo.name || !storeInfo.location || !storeInfo.phoneNumber || !storeInfo.email || !storeInfo.tableNumber || !storeInfo.openingHour || !storeInfo.closingHour) {
        return res.status(400).json({ success: false, message: 'Thiếu thông tin cửa hàng bắt buộc.' });
    }

    try {
        const userStoresSnap = await global.db.ref('dataStore').orderByChild('ownerId').equalTo(ownerId).once('value');
        const currentStoreCount = userStoresSnap.numChildren();

        if (currentStoreCount < FREE_STORE_LIMIT) {
            const storeRef = global.db.ref('dataStore').push();
            const storeId = storeRef.key;
            if (!storeId) throw new Error("Cannot create storeId.");
            const storeData = {
                storeId: storeId, ownerId: ownerId, name: storeInfo.name, image: storeInfo.image || null,
                address: storeInfo.location, phone: storeInfo.phoneNumber, email: storeInfo.email,
                tableNumber: storeInfo.tableNumber, des: storeInfo.des || "",
                openingHour: storeInfo.openingHour, closingHour: storeInfo.closingHour,
                priceTable: storeInfo.priceTable || 0,
                latitude: storeInfo.latitude || null, longitude: storeInfo.longitude || null
            };
            const overviewData = {
                storeId: storeId, ownerId: ownerId, totalBooking: 0, simpleEnding: 0,
                paidBookings: 0, profit: 0.0, tableActive: 0,
                tableEmpty: parseInt(storeInfo.tableNumber) || 0,
            };
            await Promise.all([
                global.db.ref(`dataStore/${storeId}`).set(storeData),
                global.db.ref(`dataOverview/${storeId}`).set(overviewData)
            ]);
            return res.status(201).json({
                success: true, paymentRequired: false, message: "Thêm cửa hàng thành công!", storeId: storeId
            });
        }
        else {
            const paymentOrderId = `ADDSTORECHARGE_${ownerId}_${Date.now()}`;
            await global.db.ref(`pendingChargedStores/${paymentOrderId}`).set({
                ownerId: ownerId, ...storeInfo, createdAt: admin.database.ServerValue.TIMESTAMP
            });

            const returnUrlForCharge = VNP_RETURNURL_CHARGE;
            const createDate = moment().tz(TIMEZONE).format('YYYYMMDDHHmmss');
            const ipAddr = req.headers['x-forwarded-for'] || req.connection.remoteAddress || req.socket.remoteAddress || '127.0.0.1';
            const amount = ADD_STORE_FEE * 100;
            const orderInfo = `Thanh toan phi them CLB ${storeInfo.name}`;

            let vnp_Params = {
                'vnp_Version': '2.1.0', 'vnp_Command': 'pay', 'vnp_TmnCode': tmnCode,
                'vnp_Locale': 'vn', 'vnp_CurrCode': 'VND', 'vnp_TxnRef': paymentOrderId,
                'vnp_OrderInfo': orderInfo, 'vnp_OrderType': 'other', 'vnp_Amount': amount,
                'vnp_ReturnUrl': returnUrlForCharge, 'vnp_IpAddr': ipAddr, 'vnp_CreateDate': createDate
            };
            vnp_Params = sortObject(vnp_Params);
            const signData = querystring.stringify(vnp_Params, { encode: false });
            const hmac = crypto.createHmac("sha512", secretKey);
            const signed = hmac.update(Buffer.from(signData, 'utf-8')).digest("hex");
            vnp_Params['vnp_SecureHash'] = signed;
            const paymentUrl = vnpUrl + '?' + querystring.stringify(vnp_Params, { encode: false });

            return res.status(200).json({
                success: true, paymentRequired: true,
                message: `Cần thanh toán ${ADD_STORE_FEE.toLocaleString('vi-VN')} VND để thêm CLB thứ ${currentStoreCount + 1}.`,
                paymentUrl: paymentUrl, paymentOrderId: paymentOrderId
            });
        }
    } catch (error) {
        console.error(`[Add Store] Error processing request for owner ${ownerId}:`, error);
        res.status(500).json({ success: false, message: 'Lỗi server khi xử lý yêu cầu.' });
    }
});


// --- ENDPOINT KIỂM TRA TÍNH KHẢ DỤNG BÀN (DÙNG CHO USER/FINAL CHECK) ---
router.post('/check_availability', async (req, res) => {
    const { storeId, dateTime, startTime, endTime, totalTables } = req.body;

    if (!storeId || !dateTime || !startTime || !endTime) {
        return res.status(400).json({
            available: false,
            message: 'Thiếu thông tin bắt buộc (storeId, dateTime, startTime, endTime).'
        });
    }

    let tables;
    if (typeof totalTables === 'number') {
        tables = totalTables;
    } else if (typeof totalTables === 'string') {
        tables = parseInt(totalTables.trim(), 10);
    } else {
        tables = NaN;
    }

    if (isNaN(tables) || tables <= 0) {
        console.error(`[Check] totalTables không hợp lệ:`, totalTables, `(type: ${typeof totalTables})`);
        return res.status(400).json({
            available: false,
            message: `Số lượng bàn không hợp lệ. Nhận được: ${totalTables}`
        });
    }

    try {
        let newStartMoment = moment.tz(`${dateTime} ${startTime}`, datetimeFormat, TIMEZONE);
        let newEndMoment = moment.tz(`${dateTime} ${endTime}`, datetimeFormat, TIMEZONE);

        if (!newStartMoment.isValid() || !newEndMoment.isValid()) {
            const tempStartObj = parseDateTime(dateTime, startTime);
            const tempEndObj = parseDateTime(dateTime, endTime);
            if (!tempStartObj || !tempEndObj) {
                return res.status(400).json({
                    available: false,
                    message: 'Định dạng ngày/giờ không hợp lệ. Yêu cầu: dd/MM/yyyy HH:mm'
                });
            }
            newStartMoment = moment(tempStartObj);
            newEndMoment = moment(tempEndObj);
        }

        const now = moment().tz(TIMEZONE).add(1, 'minutes');
        if (newStartMoment.isBefore(now)) {
            return res.status(400).json({
                available: false,
                message: 'Thời gian đặt bàn phải sau thời điểm hiện tại ít nhất 1 phút!'
            });
        }

        // BỎ XỬ LÝ QUA ĐÊM (CHỈ ĐẶT TRONG NGÀY)
        if (newEndMoment.isSameOrBefore(newStartMoment)) {
            return res.status(400).json({
                available: false,
                message: 'Thời gian kết thúc phải sau thời gian bắt đầu và cùng trong ngày.'
            });
        }

        let busyTablesCount = 0;

        const snapshot = await global.db.ref('dataBookTable')
            .orderByChild('storeId')
            .equalTo(storeId)
            .once('value');

        if (snapshot.exists()) {
            snapshot.forEach(child => {
                const booking = child.val();

                if (!booking || !booking.status || !booking.dateTime || !booking.startTime || !booking.endTime) {
                    return;
                }

                if (!BUSY_STATUSES.includes(booking.status)) {
                    return;
                }

                // CHỈ XEM CÁC ĐƠN TRONG NGÀY YÊU CẦU
                if (booking.dateTime !== dateTime) {
                    return;
                }

                try {
                    let existingStartMoment = moment.tz(`${booking.dateTime} ${booking.startTime}`, datetimeFormat, TIMEZONE);
                    let existingEndMoment = moment.tz(`${booking.dateTime} ${booking.endTime}`, datetimeFormat, TIMEZONE);

                    if (!existingStartMoment.isValid() || !existingEndMoment.isValid()) {
                        const tempStartObj = parseDateTime(booking.dateTime, booking.startTime);
                        const tempEndObj = parseDateTime(booking.dateTime, booking.endTime);
                        if (!tempStartObj || !tempEndObj) {
                            return;
                        }
                        existingStartMoment = moment(tempStartObj);
                        existingEndMoment = moment(tempEndObj);
                    }

                    // LOẠI BỎ XỬ LÝ QUA ĐÊM CHO BOOKING ĐÃ CÓ
                    if (existingEndMoment.isSameOrBefore(existingStartMoment)) {
                        return; // Booking không hợp lệ trong mode chỉ trong ngày
                    }

                    const isOverlapping = newStartMoment.isBefore(existingEndMoment) &&
                                          newEndMoment.isAfter(existingStartMoment);

                    if (isOverlapping) {
                        const tablesInThisBooking = 1;
                        busyTablesCount += tablesInThisBooking;
                    }
                } catch (error) {
                    console.error(`[Check] ❌ Lỗi xử lý đơn ${child.key}:`, error.message);
                }
            });
        }

        const availableCount = tables - busyTablesCount;

        if (availableCount > 0) {
            res.status(200).json({
                available: true,
                availableCount: availableCount,
                message: `Còn ${availableCount} bàn trống`
            });
        } else {
            res.status(200).json({
                available: false,
                availableCount: 0,
                message: 'Đã hết bàn vào khung giờ này. Vui lòng chọn giờ khác!'
            });
        }

    } catch (error) {
        console.error(`[Check] ❌ LỖI SERVER:`, error);
        res.status(500).json({
            available: false,
            message: `Lỗi server: ${error.message}`
        });
    }
});

// --- ENDPOINT MỚI: LẤY DỮ LIỆU TIMELINE CHI TIẾT (DÙNG CHO OWNER) ---
router.post('/get_detailed_timeline_data', async (req, res) => {
    const { storeId, date } = req.body;

    if (!storeId || storeId === 'null' || !date) {
        return res.status(400).json({ error: 'Store ID hoặc date không hợp lệ/bị thiếu.' });
    }

    try {
        // 1. LẤY THÔNG TIN CỬA HÀNG (Tổng bàn, Giờ hoạt động)
        const storeSnap = await global.db.ref(`dataStore/${storeId}`).once('value');
        if (!storeSnap.exists()) {
            return res.status(404).json({ error: 'Không tìm thấy CLB.' });
        }

        const storeData = storeSnap.val();
        const totalTables = parseInt(storeData.tableNumber || '0');
        const openingHour = storeData.openingHour || '00:00';
        const closingHour = storeData.closingHour || '23:59';

        if (totalTables <= 0) {
            return res.status(200).json({ totalTables: 0, openingHour: openingHour, closingHour: closingHour, busyBookings: [] });
        }

        // 2. LẤY TẤT CẢ CÁC BOOKING BẬN TRONG NGÀY
        let busyBookings = [];
        const snapshot = await global.db.ref('dataBookTable')
            .orderByChild('storeId')
            .equalTo(storeId)
            .once('value');

        if (snapshot.exists()) {
            snapshot.forEach(child => {
                const booking = child.val();

                // Chỉ lấy các đơn có trạng thái bận VÀ khớp ngày
                if (booking && BUSY_STATUSES.includes(booking.status) && booking.dateTime === date) {

                    let bookingStartMoment = moment.tz(`${booking.dateTime} ${booking.startTime}`, datetimeFormat, TIMEZONE);
                    let bookingEndMoment = moment.tz(`${booking.dateTime} ${booking.endTime}`, datetimeFormat, TIMEZONE);

                    if (!bookingStartMoment.isValid() || !bookingEndMoment.isValid()) {
                        console.warn(`[DetailedTimeline] ⚠️ Lỗi Parse thời gian đơn ${child.key}. Bỏ qua.`);
                        return;
                    }

                    // BỎ XỬ LÝ QUA ĐÊM
                    if (bookingEndMoment.isSameOrBefore(bookingStartMoment)) {
                        return; // Đơn hàng không hợp lệ trong mode chỉ trong ngày
                    }

                    // Lưu booking thô (Chỉ cần startTime, endTime dạng HH:mm)
                    busyBookings.push({
                        startTime: bookingStartMoment.format('HH:mm'),
                        endTime: bookingEndMoment.format('HH:mm')
                    });
                }
            });
        }

        // 3. TRẢ VỀ DỮ LIỆU THÔ ĐỂ CLIENT TÍNH TOÁN VÀ VẼ
        res.status(200).json({
            totalTables: totalTables,
            openingHour: openingHour,
            closingHour: closingHour,
            busyBookings: busyBookings
        });

    } catch (error) {
        console.error(`[DetailedTimeline] ❌ Lỗi server:`, error);
        res.status(500).json({ error: `Lỗi server: ${error.message}` });
    }
});


module.exports = router;