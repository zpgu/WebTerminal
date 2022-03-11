function WebTerminalClient() {
}

WebTerminalClient.prototype.connect = function (options) {
    if (window.location.protocol === "https:") {
        var protocol = "wss://";
    } else {
        var protocol = "ws://";
    }
    var endpoint = protocol + window.location.host + "/webterminal";

    if (window.WebSocket) {
        this._connection = new WebSocket(endpoint);
    } else {
        options.onError("WebSocket Not Supported");
        return;
    }

    this._connection.onopen = function () {
        options.onConnect();
    };

    this._connection.onmessage = function (evt) {
        options.onData(evt.data);
    };

    this._connection.onclose = function (evt) {
        options.onClose();
    };
};

WebTerminalClient.prototype.sendOutData = function (data) {
    if (this._connection.readyState === WebSocket.OPEN) {
        this._connection.send(JSON.stringify(data));
    } else {
        console.log("WebSocket Connection No Longer Open");
    }
};