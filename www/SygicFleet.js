var exec = require('cordova/exec');

function call(action, args, success, error) {
    exec(success || function () {}, error || function () {}, 'SygicFleet', action, args || []);
}

var SygicFleet = {
    initialize: function (success, error) { call('initialize', [], success, error); },
    show: function (left, top, width, height, success, error) { call('show', [left, top, width, height], success, error); },
    showForElement: function (elementOrId, success, error) {
        var el = typeof elementOrId === 'string' ? document.getElementById(elementOrId) : elementOrId;
        if (!el) { if (error) error('Element not found: ' + elementOrId); return; }
        var r = el.getBoundingClientRect();
        var scale = window.devicePixelRatio || 1;
        this.show(Math.round(r.left * scale), Math.round(r.top * scale), Math.round(r.width * scale), Math.round(r.height * scale), success, error);
    },
    hide: function (success, error) { call('hide', [], success, error); },
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
