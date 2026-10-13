# 🎙️ LiveTranslate — Real-Time AI Audio Translator & Live Dubbing for Android

![Version](https://img.shields.io/badge/Version-1.1-blue)
![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20(API%2029%2B)-3DDC84?logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin)
![Gemini API](https://img.shields.io/badge/AI-Gemini%20Live%20API-4285F4?logo=google)

[**🇬🇧 English**](#-english) | [**🇮🇷 فارسی**](#-فارسی)

---

## 🇬🇧 English

**LiveTranslate** is an Android application powered by the **Google Gemini Live API** that provides **real-time speech-to-speech translation (live dubbing)** and **live transcription**. Whether you are watching a foreign-language video, attending an online lecture, or having an in-person conversation, LiveTranslate captures the audio, translates it simultaneously with natural-sounding voice output, and displays both the original and translated transcripts in real time.

### ✨ Key Features

- **🎧 Dual Audio Capture Modes:**
  - **System / Internal Audio (`MediaProjection`):** Translate audio directly from YouTube, social media, movies, or any app playing on your phone.
  - **Microphone:** Translate live conversations, meetings, or external speakers in real time.
- **🎚️ Independent Audio Mixer:**
  - Separate, smooth `0%–100%` volume sliders for **Original Video Volume** and **Live Dubbing Volume**.
  - Lower or mute the original video's sound while keeping the translated voice loud and clear—without interrupting internal audio capture.
- **🔑 Multi-API Key Manager with Auto-Failover:**
  - Add an unlimited number of Gemini API keys.
  - Real-time status badges (`Active`, `Standby`, `Quota / Error`).
  - **Automatic Failover:** If the active API key runs out of quota or encounters an error, the app seamlessly switches to the next available key without stopping your translation session.
- **🌍 130+ Target Languages:**
  - Full searchable dropdown supporting over 130 Google-supported languages (with **Persian**, **English**, and **German** pinned at the top for quick access).
- **📝 Live Dual Transcripts:**
  - Simultaneous real-time text panels for **Original Speech** and **Live Translation**, complete with one-tap **Copy** and **Clear** actions.
- **🔔 Interactive Foreground Notification:**
  - Control your session from anywhere (even when watching videos in other apps) with **Pause**, **Resume**, and **Stop** buttons right in the notification bar. Tapping the notification brings you straight back to the app.
- **🎨 Modern UI, Themes & Localization:**
  - Interactive audio-reactive particle visualizer.
  - **Dark Mode** and **Light Mode** support.
  - Complete UI and notification localization in **English (`EN`)**, **German (`DE`)**, and **Persian (`FA`)**.

### 📱 Requirements
- **Android Version:** Android 10 (API 29) or higher
- **API Key:** At least one free or paid [Google AI Studio (Gemini) API Key](https://aistudio.google.com/)

### 🚀 How to Use
1. Download and install the **LiveTranslate `v1.1` APK** from the [Releases](../../releases) section (or build from source in Android Studio).
2. Open app.
3. Under **API Keys**, paste one or more **Gemini API Keys** and tap **Add**.
4. Select your **Audio Source** (**System Audio** for videos/apps on your device, or **Microphone** for live speech).
5. Choose your **Target Language** from the dropdown menu.
6. Tap **▶ Start**, grant the required microphone/screen-audio permission, and adjust the **Original Volume** and **Translation Volume** sliders to your liking!

### 🛠️ Building from Source
1. Clone this repository:
   ```bash
   git clone https://github.com/garodemane/APKLiveTranslate.git
   ```
2. Open the project in **Android Studio**.
3. Let Gradle sync and run the `app` configuration on a physical Android 10+ device (or assemble via `./gradlew assembleDebug`).

---

<div dir="rtl">

## 🇮🇷 فارسی

**LiveTranslate** یک اپلیکیشن پیشرفته اندروید مبتنی بر **هوش مصنوعی Google Gemini Live API** است که امکان **ترجمه صوتی همزمان (دوبله زنده)** و **تبدیل گفتار به متن زنده** را فراهم می‌کند. چه در حال تماشای ویدیو به زبان اصلی در یوتیوب و شبکه‌های اجتماعی باشید، چه در یک کلاس یا جلسه آنلاین، و چه در حال گفت‌وگوی حضوری، این برنامه صدا را دریافت کرده و به صورت آنی با صدای طبیعی دوبله می‌کند و همزمان متن گفتار اصلی و ترجمه را روی صفحه نمایش می‌دهد.

### ✨ ویژگی‌های کلیدی

- **🎧 دو حالت دریافت صدا:**
  - **صدای داخلی گوشی (System Audio):** دریافت مستقیم صدای ویدیوها، یوتیوب، اینستاگرام، فیلم‌ها و سایر برنامه‌های در حال پخش روی گوشی.
  - **میکروفون (Microphone):** مناسب برای مکالمات حضوری، جلسات یا صدای محیط.
- **🎚️ میکسر مجزای صدا (کنترل مستقل صداها):**
  - دارای دو اسلایدر مجزا و دقیق (`۰٪` تا `۱۰۰٪`) برای **صدای ویدیوی اصلی (Original Volume)** و **صدای دوبله (Translation Volume)**.
  - می‌توانید صدای ویدیوی اصلی را کم یا کاملاً بی‌صدا کنید و فقط صدای دوبله را با بلندی دلخواه بشنوید، بدون اینکه دریافت صدای ویدیو توسط هوش مصنوعی قطع شود.
- **🔑 مدیریت چندگانه کلید API با جابه‌جایی خودکار (Auto-Failover):**
  - امکان افزودن **تعداد دلخواه کلید API جمینای (Gemini API Key)**.
  - نمایش وضعیت لحظه‌ای هر کلید (`فعال`، `آماده به کار`، `خطا / اتمام سهمیه`).
  - **تعویض خودکار کلید:** اگر کلید فعلی به سقف محدودیت (Quota) برسد یا خطا بدهد، برنامه به صورت کاملاً خودکار و بدون قطع شدن ترجمه به سراغ کلید بعدی می‌رود.
- **🌍 پشتیبانی از بیش از ۱۳۰ زبان زنده دنیا:**
  - منوی کشویی کامل شامل تمام زبان‌های پشتیبانی‌شده توسط گوگل (با قرارگیری **فارسی**، **انگلیسی** و **آلمانی** در ابتدای لیست و چیدمان الفبایی سایر زبان‌ها).
- **📝 نمایش زنده متن گفتار و ترجمه:**
  - نمایش همزمان متن **گفتار اصلی** و **ترجمه زنده** در دو پنل مجزا به همراه دکمه‌های **کپی (Copy)** و **پاک کردن (Clear)**.
- **🔔 نوتیفیکیشن تعاملی و هوشمند:**
  - کنترل کامل ترجمه هنگام تماشای ویدیو در برنامه‌های دیگر از طریق دکمه‌های **توقف موقت (Pause)**، **ادامه (Resume)** و **توقف کامل (Stop)** روی نوتیفیکیشن. همچنین با لمس نوتیفیکیشن مستقیماً وارد برنامه می‌شوید.
- **🎨 رابط کاربری مدرن، چندزبانه و دارای تم شب و روز:**
  - ویژوالایزر صوتی تعاملی که به صدای ورودی و دوبله واکنش نشان می‌دهد.
  - پشتیبانی از **حالت تاریک (Dark)** و **حالت روشن (Light)**.
  - پشتیبانی کامل رابط کاربری و نوتیفیکیشن از سه زبان **فارسی (`FA`)**، **انگلیسی (`EN`)** و **آلمانی (`DE`)**.

### 📱 پیش‌نیازها
- **سیستم‌عامل:** اندروید ۱۰ (API 29) یا بالاتر
- **کلید API:** حداقل یک کلید API رایگان یا تجاری از [Google AI Studio](https://aistudio.google.com/)

### 🚀 راهنمای استفاده
۱. فایل نصبی (`APK`) نسخه **v1.1** را از بخش [Releases](../../releases) دانلود و نصب کنید.
۲. وارد برنامه شوید.
۳. در بخش **کلیدهای API**، یک یا چند کلید Gemini API وارد کرده و دکمه **افزودن** را بزنید.
۴. **منبع صدا** (**صدای سیستم** برای ویدیوهای داخل گوشی یا **میکروفون** برای صدای محیط) و **زبان مقصد** را انتخاب کنید.
۵. روی دکمه **▶ شروع (Start)** بزنید، دسترسی لازم را تایید کنید و با اسلایدرهای صدا، میزان صدای ویدیوی اصلی و دوبله را به دلخواه تنظیم نمایید!

</div>
