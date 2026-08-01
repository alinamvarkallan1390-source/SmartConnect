# 📱 SmartConnect

اپلیکیشن ارتباط هوشمند بین **گوشی اندروید** و **ساعت هوشمند (Wear OS)** از طریق بلوتوث.

---

## ✨ ویژگی‌ها

| ویژگی | توضیح |
|-------|-------|
| 🔗 **اتصال بلوتوث دوگانه** | پشتیبانی از BLE و Classic Bluetooth |
| 🔄 **اتصال مجدد خودکار** | تلاش برای reconnect تا ۱۰ بار |
| 💓 **Heartbeat** | بررسی زنده بودن اتصال هر ۱۰ ثانیه |
| 📊 **سنجش کیفیت سیگنال** | ۶ سطح: عالی، خوب، متوسط، ضعیف، خیلی ضعیف، نامشخص |
| 🔔 **آینه‌سازی اعلان‌ها** | فوروارد نوتیفیکیشن، تماس و پیامک به ساعت |
| 📤 **انتقال فایل** | ارسال فایل تکه‌تکه‌ای با قابلیت Pause/Resume |
| 🎵 **کنترل مدیا** | کنترل پخش موزیک از ساعت |
| 📋 **سینک کلیپ‌بورد** | همگام‌سازی کلیپ‌بورد بین گوشی و ساعت |
| 🔍 **پیدا کردن گوشی** | پخش صدای زنگ گوشی از ساعت |
| ⌚ **Wake-to-Raise** | روشن شدن صفحه ساعت با بلند کردن مچ |
| 🔒 **رمزنگاری** | تبادل کلید جلسه (Session Key) در هندشیک |
| 🚀 **شروع خودکار** | اجرای سرویس بعد از بوت دستگاه |

---

## 🏗️ ساختار پروژه

```
SmartConnect/
├── mobile/          ← اپلیکیشن گوشی (Jetpack Compose + Material 3)
├── wear/            ← اپلیکیشن ساعت (Wear Compose)
├── shared/          ← ماژول مشترک (پروتکل و مدل‌ها)
└── .github/         ← GitHub Actions CI/CD
```

### ماژول‌ها

| ماژول | توضیح |
|-------|-------|
| **mobile** | اپلیکیشن اصلی گوشی — داشبورد، مدیریت بلوتوث، انتقال فایل، تنظیمات |
| **wear** | اپلیکیشن ساعت — نمایش اطلاعات گوشی، پیدا کردن گوشی، Wake-to-Raise |
| **shared** | پروتکل ارتباطی JSON، مدل‌های داده مشترک |

---

## 🛠️ تکنولوژی‌ها

- **زبان:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt (Dagger)
- **Database:** Room
- **ذخیره تنظیمات:** DataStore Preferences
- **Concurrency:** Kotlin Coroutines + Flow
- **Build:** Gradle 8.6 + Kotlin DSL
- **CI/CD:** GitHub Actions
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 34 (Android 14)

---

## 🔧 پروتکل ارتباطی

پروتکل سفارشی JSON-based با ۳۲ نوع پیام:

```kotlin
Message(
    type: String,      // نوع پیام (HANDSHAKE, NOTIFICATION, FILE_TRANSFER_...)
    payload: String,   // JSON اختصاصی هر نوع
    timestamp: Long    // زمان ارسال
)
```

### انواع پیام‌ها

| دسته | پیام‌ها |
|------|---------|
| اتصال | `HANDSHAKE`, `HEARTBEAT`, `RSSI_UPDATE` |
| اطلاعات | `DEVICE_INFO_REQUEST`, `DEVICE_INFO_RESPONSE` |
| اعلان‌ها | `NOTIFICATION`, `CALL_EVENT`, `SMS_EVENT` |
| مدیا | `MEDIA_CONTROL`, `MEDIA_STATUS` |
| فایل | `FILE_TRANSFER_START/CHUNK/COMPLETE/ACK/PAUSE/RESUME/CANCEL` |
| امنیت | `SESSION_KEY_EXCHANGE` |
| کنترل | `REMOTE_CONTROL`, `FIND_DEVICE`, `FIND_PHONE` |

---

## 🚀 بیلد و اجرا

### پیش‌نیازها
- JDK 17+
- Android SDK (API 34)

### بیلد با Gradle Wrapper

```bash
cd SmartConnect

# بیلد APK موبایل
./gradlew :mobile:assembleDebug

# بیلد APK ساعت
./gradlew :wear:assembleDebug
```

### خروجی APK

```
SmartConnect/mobile/build/outputs/apk/debug/mobile-debug.apk
SmartConnect/wear/build/outputs/apk/debug/wear-debug.apk
```

### بیلد با Android Studio

1. پوشه `SmartConnect/` را در Android Studio باز کنید
2. صبر کنید تا Gradle sync تمام شود
3. ماژول مورد نظر (`mobile` یا `wear`) را انتخاب و Run کنید

---

## 📱 صفحات اپلیکیشن موبایل

| صفحه | توضیح |
|------|-------|
| **داشبورد** | وضعیت اتصال، اطلاعات ساعت، کیفیت سیگنال، عملیات سریع |
| **انتقال فایل** | ارسال/دریافت فایل بین گوشی و ساعت |
| **تنظیمات** | تنظیمات اتصال، اعلان‌ها، بهینه‌سازی باتری |
| **کنترل از راه دور** | کنترل ساعت از گوشی |
| **لاگ اتصال** | تاریخچه اتصالات |
| **توسعه‌دهنده** | ابزارهای دیباگ |

---

## 📄 لایسنس

تمامی حقوق محفوظ است.
