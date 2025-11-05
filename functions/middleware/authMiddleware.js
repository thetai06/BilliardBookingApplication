const admin = require('firebase-admin');

async function authenticateToken(req, res, next) {
    const authHeader = req.headers.authorization;
    const idToken = authHeader && authHeader.startsWith('Bearer ') ? authHeader.split('Bearer ')[1] : null;

    if (!idToken) {
        return res.status(401).send({ success: false, message: 'Unauthorized: No token provided.' });
    }

    try {
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        req.user = decodedToken; // Gắn thông tin user (uid, email...) vào request
        next(); // Token hợp lệ, cho phép đi tiếp
    } catch (error) {
        console.error('Error verifying Firebase ID token:', error);
        return res.status(403).send({ success: false, message: 'Forbidden: Invalid token.' });
    }
}

module.exports = { authenticateToken };