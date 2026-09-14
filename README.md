# FloatShot — Floating Adjustable Square Screenshot App

## Kya hai ye
Ek Android app jo:
- Screen par ek **floating icon** dikhata hai (drag kar sakte ho kahin bhi)
- Icon tap karne par ek **adjustable (resizable, free) square frame** khulta hai — usse drag karke position aur bottom-right corner se resize kar sakte ho
- Frame ke andar **camera button** dabate hi wahi square area screenshot ho jata hai
- Settings (gear icon) me **border ON/OFF** aur **border color** choose karne ka option hai
- Ek baar border set karne ke baad, **har screenshot me by default wahi border automatically** lag jata hai — manually add karne ki zaroorat nahi
- Screenshots `Pictures/FloatShot` folder me save hote hain

## Project kaise open karein
1. **Android Studio** (latest, Hedgehog ya usse naya) install karein.
2. Is poore `FloatShot` folder ko **"Open"** karein (File → Open → is folder ko select karein).
3. Gradle sync hone dein (internet chahiye, dependencies download hongi).
4. Ek real device ya emulator (API 24+) connect karein aur **Run ▶** dabayein.

## App use kaise karein
1. App kholte hi "Start Floating Icon" button dabayein.
2. **"Display over other apps"** permission allow karein (ek settings screen khulegi).
3. Android **screen recording/cast** permission popup aayega — "Start now" dabayein. (Ye Android ka security requirement hai, isse app khud bypass nahi kar sakta — har baar jab app process naya start ho tab ye ek baar poochta hai.)
4. Ab ek **floating camera icon** screen par dikhega — ise kisi bhi jagah drag kar sakte ho.
5. Icon par **tap** karein → ek square frame khulega:
   - Beech me se **drag** karke position adjust karein
   - **Neeche-daayein corner** se drag karke size (free/adjustable) badhayein-ghatayein
   - **Gear icon (top-left)** → border ON/OFF aur color set karein (ek baar set karo, hamesha ke liye yaad rahega)
   - **X icon (top-right)** → frame band karein
   - **Neeche beech ka camera button** → us exact square area ka screenshot le lega, border (agar enabled hai) automatically lag jayega, aur gallery me save ho jayega
6. Notification me **"Stop"** button se poora floating service band kar sakte ho.

## Important notes
- Border settings (enable + color) `SharedPreferences` me save hoti hain, so app band karke dobara kholne par bhi yaad rehti hain.
- Frame ki last size/position bhi yaad rehti hai.
- Kuch devices par status bar ki wajah se capture ka thoda vertical offset ho sakta hai — agar aisa lage to `FloatingOverlayService.kt` me `cropToFrame()` call se pehle `fParams.y` me chhota sa adjustment (device ke status bar height jitna) add kar sakte ho.
- Android OS ki policy ke wajah se, screen-capture permission popup **har naye app-process start par ek baar** dobara poochta hai — isko app se permanently bypass nahi kiya ja sakta (Google ki security requirement hai).
