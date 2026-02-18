# cordova-plugin-prevent-screenshot-coffice

This is a cordova plugin to enable/disable screenshots in android and ios 

## Supported Platforms

- Android API all versions('Detect and prevent' screenshot functionality)*
- IOS all versions (Only 'detect screenshot' functionality)

*For android the detect functionality is based on an Observer that keeps checking if a file with name 'Screenshot' was created while the app was opened.
It was taken from https://stackoverflow.com/questions/31360296/listen-for-screenshot-action-in-android
And was the only option I found, personally I didn't liked

## Installation

Cordova local build:
    cordova plugin add <GIT URL PATH>




## Usage in javascript

```js
document.addEventListener("deviceready", onDeviceReady, false);
// Enable
function onDeviceReady() {
  window.plugins.preventscreenshot.enable(successCallback, errorCallback);
}
// Disable
function onDeviceReady() {
  window.plugins.preventscreenshot.disable(successCallback, errorCallback);
}

function successCallback(result) {
  console.log(result); // true - enabled, false - disabled
}

function errorCallback(error) {
  console.log(error);
}


document.addEventListener("onTookScreenshot",function(){
// Receive notification when screenshot is ready;
});

document.addEventListener("onGoingBackground",function(){
// Receive notification when control center or app going in background.
});



//Activate Detect functionality for android
function enableDetect() {
  window.plugins.preventscreenshot.activateDetectAndroid(successActivateCallback, errorActivateCallback);
}

function disableDetect() {
  window.plugins.preventscreenshot.deactivateDetectAndroid(() => console.log('Detection paused'));
}

function successActivateCallback(result) {
  console.log(result); // detection running
}

function errorActivateCallback(error) {
  console.error(error);
}

enableDetect();

// later, when you no longer need to monitor screenshots
disableDetect();

// Optional: receive raw string payloads ("tookScreenshot" / "background")
window.plugins.preventscreenshot.registerListener(function (eventName) {
  console.log('Screenshot plugin event:', eventName);
});

```

### Android detection specifics

- When `activateDetectAndroid` runs for the first time the plugin requests photo/media read permission (Android 13+ uses `READ_MEDIA_IMAGES`, Android 12 and below keep using `READ_EXTERNAL_STORAGE`).
- The plugin registers a background `ContentObserver` while detect mode is active; it emits the `onTookScreenshot` DOM event as soon as a new screenshot image is added to the MediaStore.
- Call `deactivateDetectAndroid` to tear down the observer and stop listening when the sensitive screen is no longer visible.
- If the permission dialog is denied, the error callback receives `"Screenshot detection permission denied"` and no events are fired until the call succeeds.



## Usage in typescript

```ts

// Enable
  (<any>window).plugins.preventscreenshot.enable((a) => this.successCallback(a), (b) => this.errorCallback(b));

// Disable
  (<any>window).plugins.preventscreenshot.disable((a) => this.successCallback(a), (b) => this.errorCallback(b));

  successCallback(result) {
    console.log(result); // true - enabled, false - disabled
  }

  errorCallback(error) {
    console.log(error);
  }

```
