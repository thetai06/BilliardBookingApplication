// services/cronService.js
const moment = require('moment-timezone');
const admin = require('firebase-admin');
const { parseDateTime } = require('../utils/helpers');

const CLEANUP_INTERVAL = 5 * 60 * 1000;
const UNVERIFIED_USER_CLEANUP_INTERVAL_DAYS = 15;
const MAX_USERS_PER_DELETE_BATCH = 1000;

// --- HÀM LÊN LỊCH CẬP NHẬT TRẠNG THÁI ---
function scheduleStatusUpdatesForBooking(bookingId, booking) {
    const now = moment().tz('Asia/Ho_Chi_Minh').toDate();
    const nowTimestamp = now.getTime();

    if (!booking || booking.status === 'Đã huỷ' || booking.status === 'Đã hoàn thành') {
        return;
    }

    const startTimeObj = parseDateTime(booking.dateTime, booking.startTime);
    const endTimeObj = parseDateTime(booking.dateTime, booking.endTime);

    if (!startTimeObj || !endTimeObj) {
        console.warn(`[Scheduler] Bỏ qua đơn ${bookingId} do lỗi định dạng ngày/giờ.`);
        return;
    }

    const startTime = startTimeObj.getTime();
    const endTime = endTimeObj.getTime();

    const isPending = [
        "Đã xác nhận", "Đã thanh toán", "Đã thanh toán (VietQR)", "Thanh toán tại quầy"
    ].includes(booking.paymentStatus);

    const bookingRef = global.db.ref(`dataBookTable/${bookingId}`);

    // Xử lý các đơn hàng trong quá khứ hoặc hiện tại
    if (nowTimestamp >= endTime) {
        if (isPending || booking.status === 'Đang chơi') {
            bookingRef.update({ status: "Đã hoàn thành" });
        }
        return;
    }

    if (nowTimestamp >= startTime && nowTimestamp < endTime && isPending && booking.status !== 'Đang chơi') {
        bookingRef.update({ status: "Đang chơi" });
    }

    // Lên lịch cho các sự kiện trong tương lai
    const startDelay = startTime - nowTimestamp;
    const endDelay = endTime - nowTimestamp;

    if (startDelay > 0) {
        setTimeout(async () => {
            const currentBookingSnap = await bookingRef.once('value');
            const currentBooking = currentBookingSnap.val();
            const stillIsPending = currentBooking && [
                "Đã xác nhận", "Đã thanh toán", "Đã thanh toán (VietQR)", "Thanh toán tại quầy"
            ].includes(currentBooking.paymentStatus);

            if (stillIsPending && currentBooking.status !== 'Đang chơi') {
                bookingRef.update({ status: "Đang chơi" });
            }
        }, startDelay);
    }

    if (endDelay > 0) {
        setTimeout(async () => {
            const currentBookingSnap = await bookingRef.once('value');
            const currentBooking = currentBookingSnap.val();
            if (currentBooking && currentBooking.status !== 'Đã huỷ' && currentBooking.status !== 'Đã hoàn thành') {
                bookingRef.update({ status: "Đã hoàn thành" });
            }
        }, endDelay);
    }
}

// --- HÀM DỌN DẸP ĐƠN HÀNG HẾT HẠN---
async function cleanupExpiredOrders() {
    const now = new Date();
    const ordersRef = global.db.ref('dataBookTable');
    console.log(`[CRON JOB][Orders] Bat dau chu trinh don dep luc ${now.toLocaleTimeString()}`);
    let deletedCount = 0;
    try {
        const fiveMinutesAgo = now.getTime() - CLEANUP_INTERVAL;
        const pendingSnapshots = await Promise.all([
             ordersRef.orderByChild('paymentStatus').equalTo('Chờ thanh toán').once('value'),
             ordersRef.orderByChild('paymentStatus').equalTo('Chờ thanh toán VietQR').once('value')
        ]);
        const promises = [];
        pendingSnapshots.forEach(snapshot => {
            if (snapshot.exists()) {
                snapshot.forEach(childSnapshot => {
                    const order = childSnapshot.val();
                    if (order.createdAt && order.createdAt < fiveMinutesAgo) {
                        promises.push(childSnapshot.ref.remove());
                    }
                });
            }
        });
        if (promises.length > 0) { await Promise.all(promises); deletedCount += promises.length; }
    } catch (error) { console.error('[CRON JOB][Orders] Loi khi don dep don cho thanh toan:', error); }
    try {
        const cashSnapshot = await ordersRef.orderByChild('status').equalTo('Chờ xử lý').once('value');
        if (cashSnapshot.exists()) {
            const cashPromises = [];
            cashSnapshot.forEach(childSnapshot => {
                const order = childSnapshot.val();
                if (order.dateTime && order.startTime) {
                    try {
                        const [day, month, year] = order.dateTime.split('/');
                        const [hour, minute] = order.startTime.split(':');
                        const bookingStartDateTime = new Date(year, parseInt(month) - 1, day, hour, minute);
                        const deadline = bookingStartDateTime.getTime() + (15 * 60 * 1000);
                        if (now.getTime() > deadline) {
                            cashPromises.push(childSnapshot.ref.remove());
                        }
                    } catch (e) { }
                } else { cashPromises.push(childSnapshot.ref.remove()); }
            });
            if (cashPromises.length > 0) { await Promise.all(cashPromises); deletedCount += cashPromises.length; }
        }
    } catch (error) { console.error('[CRON JOB][Orders] Loi khi don dep don tai quay:', error); }
    console.log(`[CRON JOB][Orders] Hoan tat chu trinh don dep. Da xoa ${deletedCount} don.`);
}

// --- HÀM DỌN DẸP NGƯỜI DÙNG CHƯA XÁC THỰC ---
async function cleanupUnverifiedUsers() {
    const now = new Date();
    const fifteenDaysAgoTimestamp = now.getTime() - (UNVERIFIED_USER_CLEANUP_INTERVAL_DAYS * 24 * 60 * 60 * 1000);
    console.log(`[USER CLEANUP] Bat dau quet user chua xac thuc (qua ${UNVERIFIED_USER_CLEANUP_INTERVAL_DAYS} ngay) luc ${now.toLocaleTimeString()}`);
    let usersToDeleteUids = [];
    let checkedUserCount = 0;
    let deletedUserCount = 0;
    let nextPageToken;
    try {
        do {
            const listUsersResult = await admin.auth().listUsers(1000, nextPageToken);
            checkedUserCount += listUsersResult.users.length;
            listUsersResult.users.forEach(userRecord => {
                const creationTime = new Date(userRecord.metadata.creationTime).getTime();
                if (!userRecord.emailVerified && creationTime < fifteenDaysAgoTimestamp) {
                    const isEmailPasswordProvider = userRecord.providerData.some(provider => provider.providerId === 'password');
                    if (isEmailPasswordProvider) {
                         usersToDeleteUids.push(userRecord.uid);
                    }
                }
                if (usersToDeleteUids.length === MAX_USERS_PER_DELETE_BATCH) {
                    admin.auth().deleteUsers(usersToDeleteUids);
                    deletedUserCount += usersToDeleteUids.length;
                    usersToDeleteUids = [];
                }
            });
            nextPageToken = listUsersResult.pageToken;
        } while (nextPageToken);
        if (usersToDeleteUids.length > 0) {
            await admin.auth().deleteUsers(usersToDeleteUids);
            deletedUserCount += usersToDeleteUids.length;
        }
        console.log(`[USER CLEANUP] Hoan tat quet ${checkedUserCount} user. Da xoa ${deletedUserCount} user chua xac thuc.`);
    } catch (error) { console.error('[USER CLEANUP] Loi nghiem trong khi don dep user:', error); }
}

module.exports = { cleanupExpiredOrders, cleanupUnverifiedUsers, scheduleStatusUpdatesForBooking };