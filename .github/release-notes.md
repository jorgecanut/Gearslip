Gearslip 0.3.12 plus a Spanish voice assistant and fixes for head units whose music channel only takes AAC (tested on an aftermarket GalReceiver 2014 unit). This is a fork build, not an official release.

## Fork fixes
- Music plays on head units that only take raw AAC: the AAC codec config goes out right after Start, before the first frame. Before, the head unit reset the connection half a second into the first song.
- The phone stays silent while music goes to the car. Pressing volume up no longer brings the phone's speaker back for good.
- Connecting by itself waits for a head unit that is still starting up, if its music channel takes only AAC: Gearslip keeps calling it for up to a minute. Other cars get one call, then Gearslip waits for the car to call back. Gearslip learns which kind your car is the first time it connects.
- When a VPN keeps the phone from reaching the car over Wi-Fi, a popup says so, with a shortcut to the VPN settings.
- Media apps that won't let Gearslip browse them, like YouTube Music, no longer stay stuck on "would not let Gearslip browse it": start one playing on the phone and the car takes over its controls.
- When the car hangs up on its own (switched off, or wireless dropped), the music pauses and the phone stops sending audio, as when you unplug the cable. Before, it kept playing until the next connection.
- The voice assistant speaks Spanish: choose Español under Settings, Voice: language. Spanish downloads Kroko's Spanish model (149 MB) and uses Canary for the final words.
- Ask "what's on my calendar" (or "¿qué tengo hoy?") to hear your next event.

This build is signed with a different key than the official releases: uninstall the official Gearslip before installing it.

---

Gearslip 0.3.12 gives the car screen a fresh look and fixes the wireless loop 0.3.02 caused on some head units.

## New
- Navigation cards sit down the left side, with a bigger turn card that shows your lane. The map keeps the right side.
- The car screen and the phone app take their colors from your phone's Material You palette.
- The car's settings sit on the phone's home screen, each group on its own screen.
- Put the home screen's player left or right of the map.
- The browser shows the page title, and it has an address bar, forward, reload, and a start page.
- If Gearslip lacks call permissions, the car's Phone screen asks for them on the phone.

## Changed
- Gearslip uses Car App Library 1.8. Map apps show the right turn arrows and route times, and Vela works fully.
- Setup ends with recommended apps, then the button picker, then usage notes.

## Fixed
- Some head units, such as ZLink adapters, sent their Wi-Fi details twice. Gearslip rejoined the network each time and dropped the connection. It now ignores the repeat.
- Gearslip no longer crashes when a wireless connection ends.
- Opening a second music app without playing anything keeps the player on the song that's still playing.
- Vela's speed sign is full size again.
- RF Sentinel and other non-media car apps get their own tile in the launcher.
