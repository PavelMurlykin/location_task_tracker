# Nearby Tasks user manual

## Installation and first launch

The app supports Android 8.0 (API 26) and later. Install the APK on your device
and open Nearby Tasks. On first launch, read the explanation of location
reminders and grant precise location, background location, and notification
permissions in sequence. Android 13 and later require a separate notification
permission. Ordinary tasks remain available if you decline; location reminders
require these permissions and the Google Play Services geofencing service.
On the permissions screen, you can tap Continue without permissions immediately.
After completing the requests, tap Start using the app to open the task list.

To build from source, use Android Studio, Android SDK 36, and JDK 17 or later.
Set the SDK path using `sdk.dir` in `local.properties`. For the embedded map,
add `MAPKIT_API_KEY`, a Yandex MapKit key for
`com.pamurlykin.locationtasks`, to that file. Keep keys out of the repository.

From the project root in PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

On other systems, use `./gradlew` instead of `.\gradlew.bat`.
The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
Install it through Android Studio or a regular APK installer.
Without a map key, manual coordinate entry and device positioning are available.

## Creating and editing tasks

Create a task from the list, enter a title, and optionally add a description,
due date and time, priority, category, tags, and checklist. Save the task.
Quick creation is available from the list; you can dictate text through an
available system speech recognition service or send text to the app using Share.
The editor can duplicate or delete an existing task.
Daily, weekly, monthly, and yearly recurrence is supported.

Use search, filters, and sorting by due date, distance, or priority in the list.
Swipe actions let you handle tasks quickly and undo the action.
Completed tasks can be archived and restored from the archive.
Edit categories and their colors under Categories in Settings.

## Choosing a task location

1. Open the location picker from the editor for a new or existing task.
2. Move the map under the pin: its tip always points to the map center,
   and the selected coordinates change as the map moves. No long press is needed.
   When the map stops, the app attempts to resolve an address.
3. Optionally enable Show active tasks below the map.
   All incomplete, unarchived tasks with coordinates appear, including tasks
   with location reminders disabled. Nearby markers are grouped with a count,
   just like the general task map. The switch starts off, remains available
   with the map expanded, and does not change the selected point.
4. You can search for an address and choose a result, select a saved or recent
   place, use the current position button, or enter coordinates manually.
   These actions move the map to the chosen point. When editing, the map
   initially shows the saved location. For a new task without a location, it
   attempts to obtain the current position if permission is granted;
   otherwise it shows Moscow.
5. Enter an address or place name and a geofence radius from 100 to 1,000 meters.
   Optionally save the place as a template.
6. Tap Choose this location, then save the task itself. If the map is expanded,
   collapse it first to return to the fields and confirmation button.

Address search, reverse geocoding, and map loading depend on network and service
availability. Coordinates can be saved without a resolved address.

## General map and reminders

Open the general task map from the list. It shows active tasks with coordinates;
tapping a marker or group lets you open its tasks. A long press on the general
map creates a task at that point. Nearby mode sorts tasks by distance.
Select up to eight stops to plan their order locally and see tasks along the way.
The route line is approximate and does not replace road navigation.

In the editor, configure a location reminder for entry, exit, or both events,
weekdays, a time window, and an individual notification cooldown.
Tasks without a geofence can use a due reminder.
Notifications let you snooze for 15 minutes, an hour, or until the next visit;
available actions depend on reminder type. You can also complete a task from a
notification. Power saving and Android restrictions can delay location reminders;
permission status and the event log are available in Settings.
The system limit is 100 simultaneously registered geofences.

## Settings and data protection

The first settings item is Appearance: system theme (the default), light, or dark.
The second is App language: select Русский or English.
The interface changes immediately and the choice survives restarting the app.
Before an explicit choice, the device language is used; unsupported languages
fall back to Russian. On Android 13 and later, you can also change the language
in the app's system settings. New notifications use the selected language;
entered task titles and saved addresses are not translated automatically.

The default general notification cooldown is 4 hours; 1, 4, 12, and 24 hours
are available. Quiet hours start disabled, with an initial interval of 22:00–08:00.
Settings also lets you check geofences, permission status and power-saving
restrictions, view the location reminder event log, and read the privacy policy.

To lock the app, enable the switch and confirm with the system PIN, password,
or biometrics. A device screen lock must be configured. With app lock enabled,
screenshots are blocked and reminder text is hidden on the lock screen.
After being away from the app for 30 seconds, you must unlock it again.

Export data to a local `.ltbackup` file with a password of at least 8 characters.
The backup includes tasks, categories, places, and reminder settings; theme, language,
and app lock are excluded. Importing with the correct
password replaces current data after file validation. The password is not stored;
recovery without it is impossible. There are no accounts or cloud synchronization,
and automatic Android cloud backup is disabled.
