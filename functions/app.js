// app.js

const express = require('express');
const moment = require('moment-timezone');
const crypto = require('crypto');
const querystring = require('qs');
const admin = require('firebase-admin');
const axios = require('axios');
const cors = require('cors');

const { cleanupExpiredOrders, cleanupUnverifiedUsers, scheduleStatusUpdatesForBooking } = require('./services/cronService');
const { initializeFirebase, loadEnvironmentVariables } = require('./services/initService');

// --- Khởi tạo Express & Biến môi trường ---
const app = express();
const port = process.env.PORT || 3000;
global.FREE_STORE_LIMIT = 2; 
global.ADD_STORE_FEE = 5000000;

// Khởi tạo Firebase Admin SDK và tải biến môi trường vào global scope
const { db, dbFirestore, envVars } = initializeFirebase(admin);
global.db = db;
global.dbFirestore = dbFirestore;
global.envVars = loadEnvironmentVariables(envVars); // envVars: tmnCode, secretKey, vnpUrl, ...

// --- Middleware chung ---
app.use(express.json());

// CẤU HÌNH CORS
const corsOptions = {
    origin: [
        'http://127.0.0.1:5500',
        'http://localhost:5500'
    ],
    methods: "GET,HEAD,PUT,PATCH,POST,DELETE",
    optionsSuccessStatus: 204
};
app.use(cors(corsOptions));
console.log("CORS enabled for http://127.0.0.1:5500");


const voucherRoutes = require('./routes/voucherRoutes');
app.use('/vouchers', voucherRoutes);

// Import các routes mới
const authRoutes = require('./routes/authRoutes');
const paymentRoutes = require('./routes/paymentRoutes');
const storeRoutes = require('./routes/storeRoutes');

// Gắn routes vào app (sử dụng /api làm tiền tố cho dễ quản lý)
app.use('/api', authRoutes);
app.use('/', paymentRoutes); 
app.use('/', storeRoutes); 


// HỆ THỐNG LÊN LỊCH CẬP NHẬT TRẠNG THÁI & DỌN DẸP
const CLEANUP_INTERVAL = 5 * 60 * 1000;
const USER_CLEANUP_RUN_INTERVAL = 24 * 60 * 60 * 1000;

// Lên lịch chạy định kỳ
setInterval(cleanupExpiredOrders, CLEANUP_INTERVAL);
setInterval(cleanupUnverifiedUsers, USER_CLEANUP_RUN_INTERVAL);

// KHỞI ĐỘNG SERVER VÀ LẮNG NGHE
app.listen(port, () => {
    console.log(`Server listening on port ${port}`);
    const localTimeFormat = { hour: '2-digit', minute: '2-digit', second: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh' };
    console.log(`-> Thời gian server hiện tại (đã điều chỉnh theo giờ VN): ${new Date().toLocaleString('vi-VN', localTimeFormat)}`);

    // Khởi động các tác vụ dọn dẹp lần đầu
    cleanupExpiredOrders();
    cleanupUnverifiedUsers();

    // KHỞI ĐỘNG TRÌNH LẮNG NGHE ĐƠN MỚI
    const bookingsRef = db.ref('dataBookTable');
    console.log("-> Dang lang nghe cac don hang moi de len lich cap nhat...");
    bookingsRef.on('child_added', (snapshot) => {
        const bookingId = snapshot.key;
        const bookingData = snapshot.val();
        console.log(`[Listener] Phat hien don hang moi/da co: ${bookingId}`);
        scheduleStatusUpdatesForBooking(bookingId, bookingData);
    });
});
