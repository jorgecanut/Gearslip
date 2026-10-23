Gearslip 0.3.01 with fixes for head units whose music channel only takes AAC (tested on an aftermarket GalReceiver 2014 unit).

## Fixed
- Music plays on head units that only take AAC: Start goes out once, and the AAC codec config follows it before the first frame. Before, the head unit reset the connection half a second into the first song.
- The phone stays silent while music goes to the car. Pressing volume up no longer brings the phone's speaker back; the buttons still move the phone's volume for after the drive, and the car's level stays with the head unit.

This build is signed with a different key than the official releases: uninstall the official Gearslip before installing it.
