# IanPlayer UI/UX Design Playbook

Combined guidance from `ui-ux-pro-max`, `apple-design`, and `swiftui-expert-skill`, adapted for IanPlayer's Android Jetpack Compose app. Use this as a reference before changing Stream tab or any UI.

## 1. Product Context

- App type: music streaming/player, entertainment-first.
- Primary screens: Stream, Now Playing, playlists, search.
- Platform target: Android, with Compose UI and adaptive color.
- Main risk: cluttered sections, small touch targets, weak contrast, and unstable layout.

## 2. Combined Design Rules

### Accessibility

- Text contrast minimum 4.5:1 for normal text.
- Touch targets minimum 48dp on Android.
- Support large text and reduced motion.
- Do not rely on color alone for state.
- Every icon-only control needs a content description.
- Keep focus traversal and scroll content clear of fixed bars.

### Layout and Spacing

- Use a 4/8dp spacing rhythm.
- Keep horizontal card gaps around 10-12dp.
- Use 12-16dp screen gutters.
- Keep scroll content from hiding behind fixed bars.
- Prefer one primary action per card.

### Visual Hierarchy

- One signature element per screen, such as adaptive color or ambient art.
- Keep secondary text at 60% opacity or lower, but check contrast.
- Use title + artist with single-line ellipsis for compact cards.
- Avoid clutter; every section should earn its place.

### Motion and Feedback

- Provide immediate tap feedback.
- Use short, purposeful transitions.
- Respect reduced motion.
- Avoid decorative animation on frequently used controls.

### Dark Mode

- Test cards and text on OLED black.
- Borders and dividers must stay visible in both themes.
- Do not use an app-specific appearance toggle unless necessary.

### Navigation

- Tabs navigate; actions belong in toolbars or menus.
- Keep important controls reachable with one hand.
- Search should remain easy to find on Stream tab.

## 3. Stream Tab Specific Guidance

- Keep "Recently played" horizontal, compact, and only when data exists.
- Show an empty-state message when there are no recent stream songs.
- Keep mood and genre sections visually distinct.
- Avoid too many simultaneous sections; collapse or order by priority if needed.
- Recently played cards should play the whole card, not only a tiny badge.
- Art loading should show a placeholder, not blank space.

## 4. Recently Played Card Guidance

- Card width around 130-140dp.
- Square art with 20dp corner radius.
- Title: bodySmall, semi-bold, one line.
- Artist: labelSmall, 60% opacity, one line.
- Play badge: 28dp visual circle is acceptable if the full card is tappable.
- Use `contentDescription` combining title and artist.

## 5. Skill-Specific Notes

### ui-ux-pro-max

- Use dark, low-light, high-readability styling for entertainment.
- Maintain clear feature hierarchy and strong CTA repetition.
- Prefer SVG/icon consistency and visible focus states.

### apple-design

- Navigation and controls should use familiar system patterns.
- Accessibility comes before polish.
- Brand expression should not override usability.
- Dark mode and reduced motion must be tested.

### swiftui-expert-skill

- This skill is SwiftUI/iOS-focused and not directly applicable to Android Compose.
- Its useful transferable principles are: view invalidation boundaries, data flow clarity, safe areas, performance, and accessibility.
- Do not copy SwiftUI APIs into IanPlayer; translate principles only.

## 6. Implementation Sequence

1. Accessibility and contrast.
2. Navigation and touch targets.
3. Layout rhythm and spacing.
4. Visual hierarchy and craft.
5. Polish and motion.

## 7. Do Not

- Do not add decorative emojis as icons.
- Do not make small touch targets.
- Do not use color as the only state indicator.
- Do not overload Stream tab with too many competing sections.
- Do not convert SwiftUI code directly into Compose.
