var exec = require('cordova/exec');

function BIXOLON() {
    console.log("BIXOLON.js: is created");
}

BIXOLON.prototype.connect = function( ip, port, success, error ){
	exec(success, error, "BIXOLON", 'connect', [ip, port]);
}

BIXOLON.prototype.disconnect = function( success, error ){
	exec(success, error, "BIXOLON", 'disconnect', []);
}

BIXOLON.prototype.print = function( title, text, success, error ){
	exec(success, error, "BIXOLON", 'print', [title, text]);
}

BIXOLON.prototype.getStatus = function( success, error ){
	exec(success, error, "BIXOLON", 'getStatus', []);
}

var BIXOLON = new BIXOLON();
module.exports = BIXOLON;