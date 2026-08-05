# Accessibility checklist

Run this checklist on a physical device before every store release.

## TalkBack

- Complete onboarding without looking at the screen; every control must have a clear name and state.
- Create, edit, complete, archive, and delete a task using swipe navigation.
- Verify that icon-only buttons announce their action and decorative icons are skipped.
- Open the map and confirm that the nearby-place list provides a non-map alternative.
- Trigger a notification and complete or snooze the task using TalkBack.

## Display and interaction

- Test font sizes at 100%, 150%, and 200%; text must not overlap or hide required controls.
- Test display size at its largest setting and both portrait and landscape orientation.
- Material buttons, chips, switches, and icon buttons must retain at least a 48 × 48 dp touch target.
- Do not use color as the only status signal; pair it with text such as “Allowed” or “Not allowed”.

## Contrast and themes

- Check light, dark, and system modes with Android’s high-contrast text option.
- Unit tests enforce WCAG AA 4.5:1 contrast for the app’s static key color pairs.
- Dynamic Android colors must be spot-checked on at least one Android 12+ device.

## Automated checks

Run `./gradlew test lintDebug`. Android lint is the static accessibility gate; TalkBack,
font scaling, dynamic colors, and real touch targets still require the manual checks above.
