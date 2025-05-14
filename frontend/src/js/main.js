import '../scss/base.scss';
import '../scss/main.scss';

import Vue from 'vue';
import Cookies from 'js-cookie';

import MainCard from './MainCard.vue';

const maxChars = 6;

const charRemaining = val => {
    const remaining = maxChars - val.length;
    return `${remaining} character${remaining !== 1 ? 's' : ''} left`;
};

new Vue({
    el: '#app',
    data: {
        code: '',
        hint: charRemaining(''),
        codeInputClass: null
    },
    watch: {
        code(val) {
            this.produceHint(val);
        }
    },
    methods: {
        makeRoom(e) {
            ga('send', 'event', 'Room', 'make')

            const token = Cookies.get('XSRF-TOKEN');          // current value
            const body  = new FormData(e.target);             // keep form fields

            fetch('/makeroom', {
                method: 'POST',
                credentials: 'same-origin',                   // keeps cookies
                headers: { 'X-XSRF-TOKEN': token },
                body
            })
            .then(resp => {
                if (!resp.ok) throw new Error('CSRF failed');
                window.location.href = resp.headers.get('Location') || '/'; // or whatever
            })
            .catch(err => {
                // handle error/UI feedback
            });
        },
        produceHint(val) {
            if (val.length < maxChars) {
                this.codeInputClass = null;
                this.hint = charRemaining(val);
            } else {
                this.hint = 'Checking...';
                const r = new XMLHttpRequest();
                r.open('GET', `/${val}/index`, true);
                r.onload = () => {
                    if (r.status !== 200) {
                        this.codeInputClass = 'input-invalid';
                        this.hint =
                            '<span style="color:#b72a2a">Invalid room code</span>';
                    } else {
                        window.location.href = `/${val}`;
                        this.hint = 'Taking you there...';
                    }
                };
                r.send();
            }
        }
    },
    ...MainCard
});
