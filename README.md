# VIT Student (Enhanced Edition)

This repository is a fork of the original [VIT Student](https://github.com/therealsujitk/android-vtop-chennai) app created by **Sujit Kumar**. 

The original project can be found at [therealsujitk/android-vtop-chennai](https://github.com/therealsujitk/android-vtop-chennai). All credit for the core features, architecture, and original implementation goes to him.

---

## New Features & Enhancements

This edition introduces several major features, usability enhancements, and UI bug fixes:

### 1. Unified Lab Sessions (Lab Slot Merging)
* **Combined Timetable Cards**: Contiguous lab sessions of the same subject on a given day are merged into a single card showing the total time span (e.g., `8:00 AM - 9:45 AM` instead of showing two separate cards).
* **Unified Lock Screen**: Consecutive lab blocks are similarly combined into a single, cohesive card on the lock screen overlay UI.
* **Smart Progress Tracking**: The circular and horizontal progress bars calculate time across the entire merged duration of the lab.

### 2. In-App Task and Deadline Management
* **Create Tasks inside the App**: Added an option to create new tasks or deadlines directly from within the app, bypassing the restriction of only being able to add tasks when the phone is locked.
* **Unified Dialog**: Added a custom `TaskDialogHelper` that coordinates dialog prompts, TimePickerDialogs, Room database inserts, and exact alarm/reminder registrations in one place.

### 3. Clickable Subjects in the Courses Tab
* **Detailed Bottom Sheets**: Subject cards in the **Courses** tab are now clickable. Selecting a subject brings up the detailed bottom sheet containing faculty names, venues, combined slots, attendance statistics (including positive/negative class excess), and all active tasks/deadlines.
* **Direct Task Shortcuts**: You can add tasks/deadlines for a subject directly from its details card in the Courses list.

### 4. Smart Lab Notifications (Deduplication)
* **Bypass Duplicate Notifications**: Timetable alarms automatically check if a lab session for the same subject is already ongoing. The app bypasses redundant upcoming notifications for the subsequent periods of the same lab.

### 5. Fully Themed Permissions Screen
* **Adaptive Theme Colors**: Converted the hardcoded white texts and translucent white card borders in `activity_permissions.xml` to dynamic theme attributes (`?attr/colorOnSurface`, `?attr/colorOnSurfaceVariant`, etc.). The permission setup cards are now fully readable in both light theme (showing dark text on light cards) and dark theme.

---

## License

This project is licensed under the same GPL-3.0 License. See the original project link for full licensing details.
