var produceHint = function (value) {
    var codeInputBox = document.getElementById("code-input-box");
    if (value.length < maxChars) {
        codeInputBox.className = "";
        var remaining = maxChars - value.length;
        return remaining + " character" + (remaining > 1 ? "s" : "") + " left";
    }
    var http = new XMLHttpRequest();
    http.open('GET', window.location.href + value + "/index", false);
    http.send();
    if (http.status != 200) {
        codeInputBox.className = "input-invalid";
        return '<span style="color:#b72a2a">Invalid room code</span>';
    } else {
        window.location.href += value;
        return "Taking you there...";
    }
};

var maxChars = 6;
var codeInput = new Vue({
    el: '#code-input',
    data: {
        code: '',
    },
});
