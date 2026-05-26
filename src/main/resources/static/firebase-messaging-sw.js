// FCM 웹 푸시 서비스 워커 — 백그라운드 알림 수신용
// firebase-messaging-sw.js 는 반드시 루트(/)에서 서빙되어야 합니다.

importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-messaging-compat.js');

// ★ fcm-token-test.html 에 입력한 값과 동일하게 맞춰주세요
firebase.initializeApp({
  apiKey:            'AIzaSyDdCEJ1Qr6A-fkl4ezKGjZbuzu-Ys_R6B4',
  authDomain:        'mom-med.firebaseapp.com',
  projectId:         'mom-med',
  storageBucket:     'mom-med.firebasestorage.app',
  messagingSenderId: '1022760034778',
  appId:             '1:1022760034778:web:08d6d79b220c46e446a0e3',
});

const messaging = firebase.messaging();

// 백그라운드 메시지 수신 시 알림 표시
messaging.onBackgroundMessage(payload => {
  const { title, body } = payload.notification || {};
  self.registration.showNotification(title || '엄마약', {
    body: body || '',
    icon: '/favicon.ico',
  });
});
