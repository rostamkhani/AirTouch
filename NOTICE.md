# اطلاعیه‌های شخص ثالث

تشخیص دست با MediaPipe Tasks Vision نسخه ۱.۲.۳ و مدل Hand Landmarker انجام می‌شود. هر دو تحت Apache License 2.0 منتشر شده‌اند:

http://www.apache.org/licenses/LICENSE-2.0.txt

مدل داخل برنامه، نسخه float16 از این نشانی است و در زمان اجرا دانلود نمی‌شود:

https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task

SHA-256 مدل: `fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1`

متن حریم خصوصی خود MediaPipe، از فایل `META-INF/NOTICE` بسته `tasks-vision` 1.2.3:

> MediaPipe Tasks Privacy Notice
>
> Last modified: June 5, 2026
>
> When you use MediaPipe Tasks, processing of the input data (e.g. images, video, text) takes place on device, and MediaPipe does not send that input data to Google servers. As a result, you can use our MediaPipe Tasks APIs for processing data that should not leave the device.
>
> MediaPipe Tasks APIs send metrics about the performance and utilization of the APIs in your app to Google. Google uses this metrics data to measure performance, usage, debug, maintain and improve the MediaPipe Tasks, as further described in our Privacy Policy (https://policies.google.com/privacy).
>
> You are responsible for obtaining informed consent from your app users about Google's processing of MediaPipe metrics data as required by applicable law.

در مانیفست ادغام‌شده این برنامه مجوزهای `INTERNET` و `ACCESS_NETWORK_STATE` حذف شده‌اند. این دو مجوز را کتابخانه انتقال وابسته به CameraX آورده بود و برای تشخیص دست لازم نیستند. تصویر دست طبق همین اطلاعیه روی دستگاه پردازش می‌شود و AirTouch فریم، ویدئو یا نام آهنگ را لاگ یا ذخیره نمی‌کند.

فهرست کامل مجوزهای تودرتوی همان کتابخانه داخل `META-INF/NOTICE` فایل AAR است و اینجا تکرار نشده است.
