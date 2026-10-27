Gearslip 0.3.31 plus fixes for head units whose music channel only takes AAC (tested on an aftermarket GalReceiver 2014 unit). This is a fork build, not an official release.

## Fork fixes
- Music plays on head units that only take raw AAC: the AAC codec config goes out right after Start, before the first frame. Before, the head unit reset the connection half a second into the first song.
- The phone stays silent while music goes to the car. Pressing volume up no longer brings the phone's speaker back for good.
- Connecting by itself waits for a head unit that is still starting up, if its music channel takes only AAC: Gearslip keeps calling it for up to a minute. Other cars get one call, then Gearslip waits for the car to call back. Gearslip learns which kind your car is the first time it connects.
- When a VPN keeps the phone from reaching the car over Wi-Fi, a popup says so, with a shortcut to the VPN settings.
- Media apps that won't let Gearslip browse them, like YouTube Music, no longer stay stuck on "would not let Gearslip browse it": start one playing on the phone and the car takes over its controls.
- When the car hangs up on its own (switched off, or wireless dropped), the music pauses and the phone stops sending audio, as when you unplug the cable. Before, it kept playing until the next connection.
- A call made from Gearslip on an AAC-only head unit no longer leaves the car on its own phone screen: Gearslip tells the car about the call, as Android Auto does. Music comes back once the call ends.
- Gearslip stops sending video to a car that has stopped taking it, and picks up again when the car does.
- Spoken directions restart text-to-speech when it gets stuck, and the log shows why it stays silent.
- The session report says when the car was simply switched off, and the log is much shorter.

This build is signed with a different key than the official releases: uninstall the official Gearslip before installing it.

---

Gearslip 0.3.31 brings saved places, a new music player and sixteen languages.

## New
- Save Home, Work and three places of your own under Saved places in the Car card on the phone's home screen. Say "navigate home" or "take me to work", and your map app gets the address.
- Calls show on the car's own screens, and the car's phone button answers them.
- A new music player: Now playing with a big cover, Browse with covers, and Up next. Beside the map, the player floats as a card; drag its edge to resize it.
- Lyrics come from BetterLyrics, LRCLIB or KuGou, whichever has the song.
- A Dark mode setting: black, black and colour, deep colour or grey. Black is the default.
- Gearslip speaks Arabic, Chinese, Czech, Dutch, French, German, Italian, Japanese, Korean, Polish, Portuguese, Russian, Spanish, Swedish, Turkish and Ukrainian. Most started as machine translations, so fix your language on [Weblate](https://hosted.weblate.org/engage/gearslip/).
- With experimental features on, car apps get the car's speed, fuel, range and compass, and cars with an instrument cluster screen get a turn card there.
- A Manage storage screen in App info clears lyrics, voice models and old logs.

## Changed
- The nav bar floats on every screen.
- The car's settings all sit under the Car card on the phone's home screen.
- The Light app theme makes the phone app light too.
- With the player beside the map, the map app's own buttons fold behind one button, so everything fits.
- The USB audio choice is now called Through Gearslip.
- Setup and Settings explain that some newer cars reject the built-in certificate and need your own.

## Fixed
- Older head units that take only AAC audio no longer drop the connection when music starts.
- Long screens such as Settings, Messages and Vehicle data scroll all the way down.
- Settings opens faster.
