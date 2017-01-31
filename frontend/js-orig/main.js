var maxChars = 6;
var codeInputBox = document.getElementById("code-input-box");
var charRemaining = function (val) {
    var remaining = maxChars - val.length;
    return remaining + " character" + (remaining != 1 ? "s" : "") + " left";
};
var codeInput = new Vue({
    el: '#code-input',
    data: {
        code: '',
        hint: charRemaining('')
    },
    watch: {
        code: function (val) {
            this.produceHint(val);
        }
    },
    methods: {
        produceHint: function (val) {
            if (val.length < maxChars) {
                codeInputBox.className = "";
                this.hint = charRemaining(val);
            } else {
                this.hint = "Checking...";
                var http = new XMLHttpRequest();
                http.open('GET', window.location.href + val + "/index");
                http.onloadend = function () {
                    if (http.status != 200) {
                        codeInputBox.className = "input-invalid";
                        codeInput.hint = '<span style="color:#b72a2a">Invalid room code</span>';
                    } else {
                        window.location.href += val;
                        codeInput.hint = "Taking you there...";
                    }
                };
                http.send();
            }
        }
    }
});
