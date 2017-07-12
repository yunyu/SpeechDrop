var maxChars = 6;
var charRemaining = function (val) {
    var remaining = maxChars - val.length;
    return remaining + " character" + (remaining !== 1 ? "s" : "") + " left";
};
var codeInput = new Vue({
    el: '#app',
    data: {
        code: '',
        hint: charRemaining(''),
        codeInputClass: null,
        csrfToken: Cookies.get('XSRF-TOKEN')
    },
    watch: {
        code: function (val) {
            this.produceHint(val);
        }
    },
    methods: {
        produceHint: function (val) {
            if (val.length < maxChars) {
                this.codeInputClass = null;
                this.hint = charRemaining(val);
            } else {
                this.hint = "Checking...";
                var r = new XMLHttpRequest();
                r.open('GET', "/" + val + "/index", true);
                r.onload = function () {
                    if (this.status !== 200) {
                        codeInput.codeInputClass = "input-invalid";
                        codeInput.hint = '<span style="color:#b72a2a">Invalid room code</span>';
                    } else {
                        window.location.href += val;
                        codeInput.hint = "Taking you there...";
                    }
                };
                r.send();
            }
        }
    }
});
