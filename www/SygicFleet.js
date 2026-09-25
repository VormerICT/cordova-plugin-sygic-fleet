var exec = require('cordova/exec');

var trackedElement = null;
var trackingInstalled = false;
var positionUpdatePending = false;
var lastBoundsKey = null;

function call(action, args, success, error) {
    exec(success || function () {}, error || function () {}, 'SygicFleet', action, args || []);
}

function getElement(elementOrId) {
    return typeof elementOrId === 'string' ? document.getElementById(elementOrId) : elementOrId;
}

function getBounds(el) {
    var r = el.getBoundingClientRect();
    var scale = window.devicePixelRatio || 1;
    return {
        left: Math.round(r.left * scale),
        top: Math.round(r.top * scale),
        width: Math.round(r.width * scale),
        height: Math.round(r.height * scale)
    };
}

function updateTrackedPosition() {
    if (!trackedElement || !document.documentElement.contains(trackedElement)) return;
    var b = getBounds(trackedElement);
    if (b.width < 32 || b.height < 32) return;
    var key = b.left + ',' + b.top + ',' + b.width + ',' + b.height;
    if (key === lastBoundsKey) return;
    lastBoundsKey = key;
    call('updatePosition', [b.left, b.top, b.width, b.height]);
}

function schedulePositionUpdate() {
    if (!trackedElement || positionUpdatePending) return;
    positionUpdatePending = true;
    window.requestAnimationFrame(function () {
        positionUpdatePending = false;
        updateTrackedPosition();
    });
}

function installTracking() {
    if (trackingInstalled) return;
    // Capture scroll events so this also works with nested OutSystems scroll containers.
    window.addEventListener('scroll', schedulePositionUpdate, true);
    window.addEventListener('resize', schedulePositionUpdate, true);
    window.addEventListener('orientationchange', schedulePositionUpdate, true);
    trackingInstalled = true;
}

function stopTracking() {
    trackedElement = null;
    lastBoundsKey = null;
}

var SygicFleet = {
    initialize: function (success, error) { call('initialize', [], success, error); },
    show: function (left, top, width, height, success, error) { call('show', [left, top, width, height], success, error); },
    showForElement: function (elementOrId, success, error) {
        var el = getElement(elementOrId);
        if (!el) { if (error) error('Element not found: ' + elementOrId); return; }
        var b = getBounds(el);
        if (b.width < 32 || b.height < 32) {
            if (error) error('Sygic target element is too small or not laid out yet: ' + b.width + 'x' + b.height + ' px');
            return;
        }
        trackedElement = el;
        lastBoundsKey = b.left + ',' + b.top + ',' + b.width + ',' + b.height;
        installTracking();
        this.show(b.left, b.top, b.width, b.height, success, error);
    },
    hide: function (success, error) {
        stopTracking();
        call('hide', [], success, error);
    },
    navigateToAddress: function (address, success, error) { call('navigateToAddress', [address], success, error); },
    navigateToCoordinates: function (latitude, longitude, name, success, error) { call('navigateToCoordinates', [latitude, longitude, name || 'Destination'], success, error); },
    stopNavigation: function (success, error) { call('stopNavigation', [], success, error); },
    getRouteInfo: function (success, error) { call('getRouteInfo', [], success, error); },
    getActualGpsPosition: function (success, error) { call('getActualGpsPosition', [], success, error); },
    getDeviceId: function (success, error) { call('getDeviceId', [], success, error); },
    getApplicationVersion: function (success, error) { call('getApplicationVersion', [], success, error); },
    isReady: function (success, error) { call('isReady', [], success, error); },
    addEventListener: function (success, error) { exec(success, error || function () {}, 'SygicFleet', 'registerEventListener', []); },
    removeEventListener: function (success, error) { call('removeEventListener', [], success, error); }
};

module.exports = SygicFleet;
