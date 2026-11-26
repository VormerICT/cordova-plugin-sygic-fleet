var exec = require('cordova/exec');

var trackedElement = null;
var trackingInstalled = false;
var positionUpdatePending = false;
var lastBoundsKey = null;
var resizeObserver = null;
var MIN_SIZE = 100;
var trackingGeneration = 0;

function call(action, args, success, error) {
    exec(success || function () {}, error || function () {}, 'SygicFleet', action, args || []);
}

function getElement(elementOrId) {
    return typeof elementOrId === 'string' ? document.getElementById(elementOrId) : elementOrId;
}

function getBounds(el) {
    var r = el.getBoundingClientRect();
    var scale = window.devicePixelRatio || 1;
    var style = window.getComputedStyle(el);
    var pl = parseFloat(style.paddingLeft) || 0;
    var pr = parseFloat(style.paddingRight) || 0;
    var pt = parseFloat(style.paddingTop) || 0;
    var pb = parseFloat(style.paddingBottom) || 0;
    return {
        left: Math.round((r.left + el.clientLeft + pl) * scale),
        top: Math.round((r.top + el.clientTop + pt) * scale),
        width: Math.round(Math.max(0, el.clientWidth - pl - pr) * scale),
        height: Math.round(Math.max(0, el.clientHeight - pt - pb) * scale)
    };
}

function updateTrackedPosition() {
    if (!trackedElement) return;
    if (!document.documentElement.contains(trackedElement)) {
        stopTracking();
        call('hide', []);
        return;
    }
    var b = getBounds(trackedElement);
    if (b.width < MIN_SIZE || b.height < MIN_SIZE || window.getComputedStyle(trackedElement).visibility === 'hidden') {
        if (lastBoundsKey !== 'hidden') {
            lastBoundsKey = 'hidden';
            call('hide', []);
        }
        return;
    }
    var key = b.left + ',' + b.top + ',' + b.width + ',' + b.height;
    if (key === lastBoundsKey) return;
    lastBoundsKey = key;
    call('updatePosition', [b.left, b.top, b.width, b.height, true]);
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
    trackingGeneration++;
    if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null; }
    if (trackingInstalled) {
        window.removeEventListener('scroll', schedulePositionUpdate, true);
        window.removeEventListener('resize', schedulePositionUpdate, true);
        window.removeEventListener('orientationchange', schedulePositionUpdate, true);
        trackingInstalled = false;
    }
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
        if (b.width < MIN_SIZE || b.height < MIN_SIZE) {
            if (error) error('Sygic target element is too small or not laid out yet: ' + b.width + 'x' + b.height + ' px');
            return;
        }
        stopTracking();
        trackedElement = el;
        lastBoundsKey = b.left + ',' + b.top + ',' + b.width + ',' + b.height;
        installTracking();
        var generation = trackingGeneration;
        call('show', [b.left, b.top, b.width, b.height, true], function (result) {
            if (generation === trackingGeneration) {
                if (typeof ResizeObserver !== 'undefined') {
                    resizeObserver = new ResizeObserver(schedulePositionUpdate);
                    resizeObserver.observe(el);
                }
                schedulePositionUpdate();
            }
            if (success) success(result);
        }, function (err) {
            if (generation === trackingGeneration) stopTracking();
            if (error) error(err);
        });
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
