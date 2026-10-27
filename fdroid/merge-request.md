Title: New app: Sun Piano Duo

## Abstract for new app inclusion

### Please introduce the functions and usage of your app in summary, and also attach some screenshots.

Sun Piano Duo is a fork of Sun Piano (code.alimiracle.sunpiano, already in F-Droid): a multitouch 88-key piano with its own real-time synthesizer, 8 instruments, recording, metronome, MIDI import/export and USB/Bluetooth MIDI input.

The fork adds a two-keyboard layout for tablets held in landscape: the screen is split into two stacked keyboards (the upper one continues where the lower one ends), and the mini-map scrolls both together. It switches on when the window is at least 600dp tall, so phones keep one keyboard, and it can be turned off in Settings.

Screenshots: https://github.com/drajaytripathi/sun-piano-duo/tree/main/fastlane/metadata/android/en-US/images (tenInchScreenshots/4.png shows the new layout).

### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.

On a large tablet in landscape, Sun Piano shows a single keyboard with very tall keys and only two octaves. Sun Piano Duo uses that space to show four adjacent octaves at once on two keyboards, which makes playing across octaves (both hands, or wide melodies) possible without scrolling.

As F-Droid asks of forks, it has its own application ID, name and icon, and credits the original author in the About screen and description.

## Checklist

### Policy

* [x] The app complies with the inclusion criteria. Note on Quality: the fork's own repository is new (October 2026); the code it builds on is the actively maintained Sun Piano. I commit to maintaining the fork.
* [ ] The original app author has been notified (and does not oppose the inclusion). <!-- fill in once the notice is sent: date and how -->
* [x] The upstream app source code repo contains the app metadata in a Fastlane folder structure (en-US, with summary, description, icon, screenshots and changelog).

### Docs

* [x] Please read the guide first if this is your first contribution.
* [x] Please make sure your metadata follows the best practice in our templates.
* [x] Please read the Build Metadata Reference and make sure your metadata is valid.
* [x] Please read the Quick Start Guide.

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected.
* [x] Please read our Git guide if you don't know how to rebase your branch.
* [x] All related fdroiddata and RFP issues have been referenced in this merge request (there are none).
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo.
* [x] Releases are tagged and auto update is enabled.
* [x] There is an issue tracker and contact info of the author.
* [x] An AuthorName must be added.
* [x] External repos are added as git submodules instead of srclibs (there are none).
* [ ] Enable Reproducible Builds. Not enabled: please sign with the F-Droid key.
* [x] Setup abi split if the APK is large (not needed: the release APK is about 2.4 MB).
* [x] Only the latest versions should be kept in the metadata before it's merged.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash.

### Pipeline

* [ ] All pipelines should pass.
* [ ] All warnings and errors in the Reports tab should be fixed or explained.

/label ~"New App"
