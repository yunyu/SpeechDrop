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
        codeInputClass: null,
        csrfToken: Cookies.get('XSRF-TOKEN')
    },
    watch: {
        code(val) {
            this.produceHint(val);
        }
    },
    methods: {
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
