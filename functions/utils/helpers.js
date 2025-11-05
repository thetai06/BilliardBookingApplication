// utils/helpers.js
const moment = require('moment-timezone');

/**
 * Xử lý số điện thoại về định dạng chuẩn +84...
 */
function formatPhoneNumber(phone) {
    if (!phone) {
        return undefined;
    }
    if (phone.startsWith('0')) {
        return `+84${phone.substring(1)}`;
    }
    if (phone.startsWith('+')) {
        return phone;
    }
    return `+84${phone}`;
}

/**
 * Sắp xếp các tham số object theo key cho VNPAY.
 */
function sortObject(obj) {
    let sorted = {};
    let str = [];
    for (let key in obj) {
        if (obj.hasOwnProperty(key)) {
            str.push(encodeURIComponent(key));
        }
    }
    str.sort();
    for (let key = 0; key < str.length; key++) {
        sorted[str[key]] = encodeURIComponent(obj[str[key]]).replace(/%20/g, "+");
    }
    return sorted;
}

/**
 * Hàm chuyển đổi chuỗi ngày và giờ thành đối tượng Date theo múi giờ Việt Nam.
 */
function parseDateTime(dateStr, timeStr) {
    if (!dateStr || !timeStr) return null;

    let format = "DD/MM/YYYY HH:mm";
    let dateToParse = dateStr;

    if (dateStr.includes('-')) {
         const parts = dateStr.split('-');
         if (parts.length === 3) {
             if (parts[0].length === 4) {
                  dateToParse = `${parts[2]}/${parts[1]}/${parts[0]}`;
             }
             else {
                  dateToParse = dateStr.replace(/-/g, '/');
             }
         }
    }

    try {
        const dateTimeString = `${dateToParse} ${timeStr}`;
        const dateObject = moment.tz(dateTimeString, format, 'Asia/Ho_Chi_Minh');

        if (!dateObject.isValid()) {
             console.error(`Ngày giờ không hợp lệ: "${dateTimeString}" (Đã thử format: "${format}")`);
             return null;
        }
        return dateObject.toDate();
    } catch (error) {
        console.error(`Lỗi phân tích ngày/giờ: ${dateStr} ${timeStr}`, error);
        return null;
    }
}

module.exports = { formatPhoneNumber, sortObject, parseDateTime };