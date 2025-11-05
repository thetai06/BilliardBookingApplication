// services/initService.js
const admin = require('firebase-admin');
const moment = require('moment-timezone');
const crypto = require('crypto');
const querystring = require('qs');

function initializeFirebase(adminSDK) {
    let envVars = {};
    try {
        const serviceAccount = JSON.parse(process.env.FIREBASE_CREDENTIALS);
        adminSDK.initializeApp({
          credential: adminSDK.credential.cert(serviceAccount),
          databaseURL: process.env.FIREBASE_DATABASE_URL
        });
        console.log("Firebase Admin SDK initialized successfully.");
    } catch (error) {
        console.error("!!! LỖI KHỞI TẠO FIREBASE ADMIN SDK !!!:", error);
        process.exit(1);
    }

    const db = adminSDK.database();
    const dbFirestore = adminSDK.firestore();

    // Tải các biến môi trường VNPAY/VietQR cần kiểm tra
    envVars.tmnCode = process.env.VNP_TMNCODE;
    envVars.secretKey = process.env.VNP_HASHSECRET;
    envVars.vnpUrl = process.env.VNP_URL || 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html';
    envVars.returnUrl = process.env.VNP_RETURNURL;
    envVars.frontendSuccessUrl = process.env.FRONTEND_SUCCESS_URL;
    envVars.frontendFailUrl = process.env.FRONTEND_FAIL_URL;
    envVars.VIETQR_API_URL = process.env.VIETQR_API_URL;
    envVars.VIETQR_API_KEY = process.env.VIETQR_API_KEY;
    envVars.VIETQR_IPN_SECRET = process.env.VIETQR_IPN_SECRET;
    envVars.VIETQR_MERCHANT_ACC_NO = process.env.VIETQR_MERCHANT_ACC_NO;
    envVars.VIETQR_MERCHANT_BIN = process.env.VIETQR_MERCHANT_BIN;
    envVars.VIETQR_MERCHANT_NAME = process.env.VIETQR_MERCHANT_NAME;
    envVars.VNP_RETURNURL_CHARGE = process.env.VNP_RETURNURL_CHARGE || envVars.returnUrl;

    return { db, dbFirestore, envVars };
}

function loadEnvironmentVariables(envVars) {
     if (!envVars.tmnCode || !envVars.secretKey || !envVars.returnUrl || !process.env.FIREBASE_CREDENTIALS || !process.env.FIREBASE_DATABASE_URL) {
        console.error("!!! LỖI: Thiếu các biến môi trường VNPAY hoặc Firebase quan trọng!");
        process.exit(1);
    }
    return envVars;
}

module.exports = { initializeFirebase, loadEnvironmentVariables };