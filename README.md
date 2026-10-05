# Ultrebo

[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

**Website:** https://ultrevo.github.io/Ultrebo/  -  **Discord:** https://discord.gg/mAKGfaAWWW  -  **Computer version:** https://github.com/Ultrevo/Ultrebo-pc

An Android app that **records your taps and swipes, lets you edit every step, and replays them in a loop**.
It can also look for pictures and words on your screen and react to them. Built for repetitive game tasks such
as tower-defense farming, but it works over any app.

- **Record** your taps and swipes over any app; the pauses between them are captured for you.
- **Edit every step:** position, press time, wait, repeat, on/off, and a test button for each one.
- **The list order is the run order.** Move a step up or down from its ⋮ menu.
- **Two run modes.** *Sequence* runs every step in order, looped. *Reactive* runs only the first step whose condition is met.
- **Find a picture.** Crop a button or icon from your screen and Ultrebo taps it whenever it appears.
- **Find text.** Type the words to look for, such as `I'm here`. Text is read on your phone.
- **Rules.** Always-watching detections that handle pop-ups while your macro runs. Add as many as you like.
- **A floating RUN / REC / CROP bar** that stays on top of your game.
- **Private.** No accounts, no ads, no tracking. Your macros and screenshots stay on your phone.

> **Android only.** iOS doesn't let an app tap or read other apps.
>
> **Use responsibly.** Many games, including Roblox experiences, forbid automation in their terms of service and may
> suspend accounts that use it. You are responsible for how you use this app.

## Install

1. On your phone, open the [latest release](../../releases/latest) and download `Ultrebo-*.apk`.
2. Tap the file. If Android asks, allow your browser or Files app to install apps.
3. Open Ultrebo. The **Get started** guide on the home screen takes you through the permissions it needs and
   disappears once they're on:
   1. **Accessibility service** (needed to tap). The guide opens the right settings page. On Android 13 and newer you may
      first need **Allow restricted settings**; the guide has a button for that.
   2. **Notifications** (Android 13+), for the small "screen capture is on" notice.
   3. **Screen capture** (optional), only for picture and text steps.

Requires Android 8.0 or newer. After the first install, Ultrebo offers to update itself when a new version is out.

## Quick start

1. Tap **New macro** and open it.
2. Tap **Add step > Record inputs in the game**, switch to your game, press **REC**, play the taps you want, then press **DONE**.
3. Tap a step to change it. Use the ⋮ menu on a step to test it, move it or delete it.
4. Press **Start** (or **RUN** on the floating bar). **STOP** ends it.

### Run modes

| Mode | What it does |
| --- | --- |
| **Sequence** | Runs every enabled step once per loop, from the top of the list down. Loops a set number of times or forever, with a pause between loops. |
| **Reactive** | Each cycle, runs only the first step in the list whose condition is met, then starts over. Tap and swipe steps are always met, so put them at the bottom to act as a fallback. A *Wait for picture* step that is visible holds back every step below it. |

*Look at the screen every* (in the macro's Settings tab) sets how often picture and text steps check the screen. A longer wait, such as
`10000` (10 seconds), is much easier on the battery.

### Find a picture

1. Tap **Add step > Find a picture on screen**, then **Pick image from screen**.
2. Switch to your game, press **CROP** on the floating bar and drag a box around the button or icon.
3. Open Ultrebo again. A thumbnail shows on the step.

Crop tightly around something distinctive, use the same phone and orientation you'll run it on, and avoid areas with changing numbers. Raise the match
threshold if it taps the wrong thing; lower it if it misses.

### Find text

Tap **Add step > Find text on screen** and type the words (for example `I'm here`). Capital letters, spaces and punctuation are ignored.
*Match strictness* controls how many misread letters are forgiven. It reads Latin letters and numbers (English and similar) and is
slower than picture matching, so use a longer check interval (1 to 3 seconds) if your phone gets warm.

### Rules

Open a macro's **Rules** tab and tap **Add rule**. A rule looks for a picture or for words in the background for the whole run, even while your steps are
tapping. When its target appears (say an "I'm here" button) it can:

- **Pause, then carry on:** the macro pauses between taps, the rule taps the target (if you left that on), waits the time you set, and the macro continues.
- **Restart macro from the start:** the macro stops, the rule (optionally) taps the target, waits, then the macro starts again from step 1.

If two rules are on screen at the same moment, **the one nearer the top of the list goes first**; the other is handled right after if it's still showing. Move a
rule with its ⋮ menu. A macro with only rules just sits and watches until you press STOP. Rules need screen capture on.

### Groups

If several rules are really the same thing, such as three pictures of one pop-up, put them in a **group**: on the Rules tab tap **Groups**, make a group, then pick it in each rule.
As soon as one rule in a group is found, the whole group stops being checked. By default that lasts until the macro stops. Set a number of seconds
for a pop-up that comes back now and then, and the group starts looking again after that long. Switch on **Start checking again when the macro restarts** to wake a group up
whenever the macro restarts: it finishes a loop and starts over (Sequence mode), or a rule set to restart the macro does it. In Reactive mode only a restarting rule counts,
because a cycle isn't a restart. Rules with no group are unaffected, and groups are shared along with the rules.

### Sharing rules with other players

Set your rules up once, then share them. On the **Rules** tab tap **Export rules** and choose where to save: that makes one
`.ultrebo-rules` file with the pictures inside. Send it to anyone (for example in Discord). They open their own macro's Rules tab, tap
**Import rules** and pick the file, and the rules are added at the bottom of their list.

- **Text rules** work on any phone. **Picture rules** match best on the same phone screen size, so they may need to pick a picture again.
- Rule packs from the computer version can't be used on a phone, and the other way round.
- **Only import rule packs from people you trust, and check what each rule does:** rules tap things on your screen.

### Screenshots to Discord

Want to know when something is found while you're away? Add a **Discord webhook** and switch on **Send a screenshot to Discord when found** in any picture or text
step, or in a rule. Each time it is found, Ultrebo posts a screenshot of your screen, with a short message such as `Ultrebo found "Victory" in "Farm"`, to your channel.

1. In Discord open your channel's **Settings > Integrations > Webhooks > New Webhook** and choose **Copy Webhook URL**.
2. In Ultrebo tap the **bell** button at the top of the home screen, paste it in and tap **Send test** to check it works.

- A step sends at most one screenshot every few seconds, so a rule that keeps matching won't flood the channel. Sending happens in the background and never slows the macro down.
- **Keep the webhook address private:** anyone who has it can post in that channel. It is saved only on your phone and is never included in a shared rules file. Importing rules never switches this on.
- The screenshot shows your whole screen at that moment, so it can include anything that is showing.
- If a macro has this switched on but no webhook is set, Ultrebo tells you instead of starting.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Accessibility option is greyed out | Settings > Apps > Ultrebo > ⋮ > *Allow restricted settings*, then try again. |
| Taps land in the wrong place | Re-record after changing screen rotation or display size; positions are absolute. |
| A picture is never found | Crop it again, lower the threshold (try 0.75) and make sure screen capture is on. |
| It taps the wrong thing | Crop a more distinctive area and raise the threshold (0.9 or more). |
| The macro stops by itself | Battery savers can switch the accessibility service off. Turn it back on and exclude Ultrebo from battery optimisation. |
| A game blocks it | Some games block taps from accessibility services. Nothing here works around that. |

Still stuck? Join the [Discord server](https://discord.gg/mAKGfaAWWW) and open a support ticket, or open an [issue](../../issues).

## Permissions and privacy

Each sensitive permission is used for exactly one thing:

| Permission | What it's used for |
| --- | --- |
| **Accessibility service** | Performing your macros' taps and swipes and showing the floating bar. It doesn't read other apps' content. |
| **Screen capture** (optional) | Only for picture and text steps. Frames are checked in memory and thrown away; the only image saved is the box you crop. Android asks again each time the app restarts. |
| **Notifications** | Android requires a visible notice while screen capture is on. |
| **Internet** | Checking GitHub for a newer release (you can turn this off), downloading it if you tap Update, and, only if you set a Discord webhook and switch it on for a step or rule, sending a screenshot to that webhook when it is found. |
| **Install unknown apps** (optional) | Only so the in-app updater can install Ultrebo's own update. Android rejects anything signed with a different key. |

Macros and cropped images are stored in the app's private storage and never uploaded. The one thing that can leave your phone is a screenshot you ask for: only if you set a Discord webhook and switch on *Send a screenshot to Discord* for a step or rule is that screenshot posted to your webhook. On launch the app asks GitHub's public API for the latest release
and compares version numbers; nothing about you, your phone or your macros is sent. Switch it off with **Check for updates on launch** in About.

Text recognition uses Google's [ML Kit](https://developers.google.com/ml-kit), which runs on your phone with a model bundled in the app.
Your screen content is never uploaded, but ML Kit may send anonymous usage statistics to Google. If you don't want that, don't use text steps or text rules.

Updates download from this repo's GitHub release, are checked against its SHA-256, and are installed by Android's own installer, which keeps your macros and settings.
Android may switch the accessibility service off after an update; the **Get started** guide reappears if so.

## Support Ultrebo

Ultrebo is free. If it saves you time you can optionally chip in on the **Ethereum network** (ETH, or USDT/USDC on Ethereum):

```
0x108484e1744Fd6ED22288411B9596390E76CD5b2
```

Double-check the address and network before sending; crypto payments can't be reversed. There's no obligation, and nothing is locked behind it.

## License and credits

[GPL-3.0](LICENSE), copyright (C) 2026 Ultrevo. Anyone may use, study and modify this app, but copies and modified versions must stay open source under
the same licence and keep the copyright notice. See [NOTICE](NOTICE) for the extra permission covering Google ML Kit. Releases v0.1.0 to v0.1.2 were
published under the MIT License and remain available under those terms.

Ultrebo is designed, directed, tested and maintained by Ultrevo. Much of the code was written with the help of an AI assistant (Claude, by Anthropic).
