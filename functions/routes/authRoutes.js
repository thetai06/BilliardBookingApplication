// routes/authRoutes.js
const express = require('express');
const router = express.Router();
const admin = require('firebase-admin');
const { authenticateToken } = require('../middleware/authMiddleware');
const { formatPhoneNumber } = require('../utils/helpers');

// API 1: TẠO USER MỚI (Manager, User...)
router.post('/createUser', authenticateToken, async (req, res) => {
    // Thay thế db bằng global.db, admin.auth() bằng admin.auth()
    const ownerId = req.user.uid;
    const { name, email, phone, password, role, address, storeId } = req.body;
    // ... (logic kiểm tra, tạo user, tạo dataUser) ...
    if (!name || !email || !password || !role) {
        return res.status(400).json({ success: false, message: 'Thiếu thông tin (name, email, password, role).' });
    }
    if (role === 'manager' && !storeId) {
         return res.status(400).json({ success: false, message: 'Manager phải được gán vào một cơ sở (storeId).' });
    }
    try {
        const userRecord = await admin.auth().createUser({
            email: email, emailVerified: true, password: password,
            displayName: name, phoneNumber: formatPhoneNumber(phone)
        });
        const newUserId = userRecord.uid;
        const userData = {
            name: name, email: email, phone: phone || "", role: role, address: address || "",
            ownerId: ownerId, storeId: (role === 'manager') ? storeId : null,
            createdAt: admin.database.ServerValue.TIMESTAMP
        };
        await global.db.ref(`dataUser/${newUserId}`).set(userData);
        res.status(201).json({ success: true, message: 'Tạo người dùng mới thành công!', userId: newUserId });
    } catch (error) {
        console.error('[API createUser] Lỗi:', error);
        if (error.code && error.code.startsWith('auth/')) {
             res.status(400).json({ success: false, message: `Lỗi Auth: ${error.message}` });
        } else {
             res.status(500).json({ success: false, message: 'Lỗi server khi tạo người dùng.' });
        }
    }
});

// API 2: CẬP NHẬT USER
router.put('/updateUser/:id', authenticateToken, async (req, res) => {
    // ... [GIỮ NGUYÊN LOGIC CỦA API updateUser TẠI ĐÂY] ...
    const ownerId = req.user.uid;
    const userIdToUpdate = req.params.id;
    const { name, phone, role, address, storeId } = req.body;
    if (!name || !role) { return res.status(400).json({ success: false, message: 'Thiếu thông tin (name, role).' }); }
    if (role === 'manager' && !storeId) { return res.status(400).json({ success: false, message: 'Manager phải được gán vào một cơ sở (storeId).' }); }
    try {
        const userRef = global.db.ref(`dataUser/${userIdToUpdate}`);
        const snapshot = await userRef.once('value');
        if (!snapshot.exists()) { return res.status(404).json({ success: false, message: 'Không tìm thấy người dùng này.' }); }
        if (snapshot.val().ownerId !== ownerId) { return res.status(403).json({ success: false, message: 'Bạn không có quyền sửa người dùng này.' }); }
        const updateData = {
            name: name, phone: phone || "", role: role, address: address || "",
            storeId: (role === 'manager') ? storeId : null
        };
        await admin.auth().updateUser(userIdToUpdate, { displayName: name, phoneNumber: formatPhoneNumber(phone) });
        await userRef.update(updateData);
        res.status(200).json({ success: true, message: 'Cập nhật thông tin thành công!' });
    } catch (error) {
        console.error(`[API updateUser] Lỗi khi cập nhật ${userIdToUpdate}:`, error);
        res.status(500).json({ success: false, message: 'Lỗi server khi cập nhật.' });
    }
});

// API 3: XÓA USER
router.delete('/deleteUser/:id', authenticateToken, async (req, res) => {
    // ... [GIỮ NGUYÊN LOGIC CỦA API deleteUser TẠI ĐÂY] ...
    const ownerId = req.user.uid;
    const userIdToDelete = req.params.id;
    try {
        const userRef = global.db.ref(`dataUser/${userIdToDelete}`);
        const snapshot = await userRef.once('value');
        if (!snapshot.exists()) { return res.status(404).json({ success: false, message: 'Không tìm thấy người dùng này.' }); }
        if (snapshot.val().ownerId !== ownerId) { return res.status(403).json({ success: false, message: 'Bạn không có quyền xóa người dùng này.' }); }
        await admin.auth().deleteUser(userIdToDelete);
        await userRef.remove();
        res.status(200).json({ success: true, message: 'Xóa người dùng thành công.' });
    } catch (error) {
         console.error(`[API deleteUser] Lỗi khi xóa ${userIdToDelete}:`, error);
         if (error.code === 'auth/user-not-found') {
             await global.db.ref(`dataUser/${userIdToDelete}`).remove();
             return res.status(200).json({ success: true, message: 'Xóa người dùng (chỉ RTDB) thành công.' });
         }
         res.status(500).json({ success: false, message: `Lỗi server khi xóa: ${error.message}` });
    }
});

module.exports = router;